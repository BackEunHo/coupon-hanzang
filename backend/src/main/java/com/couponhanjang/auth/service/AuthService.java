package com.couponhanjang.auth.service;

import com.couponhanjang.common.crypto.PhoneProtector;
import com.couponhanjang.merchant.entity.Merchant;
import com.couponhanjang.merchant.entity.Operator;
import com.couponhanjang.common.exception.ApiException;
import com.couponhanjang.merchant.repository.MerchantRepository;
import com.couponhanjang.merchant.repository.OperatorRepository;
import com.couponhanjang.auth.security.AuthPrincipal;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {
    private final MerchantRepository merchants;
    private final OperatorRepository operators;
    private final PhoneProtector phones;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(MerchantRepository merchants, OperatorRepository operators, PhoneProtector phones,
                       PasswordEncoder encoder, Clock clock) {
        this.merchants = merchants;
        this.operators = operators;
        this.phones = phones;
        this.encoder = encoder;
        this.clock = clock;
        this.dummyHash = encoder.encode("unregistered-account-placeholder");
    }

    @Transactional
    public SignedIn signup(String merchantName, String phoneInput, String password) {
        String phone = phones.normalize(phoneInput);
        String lookup = phones.lookup(phone);
        if (operators.existsByPhoneLookup(lookup)) {
            throw new ApiException(409, "PHONE_ALREADY_REGISTERED", "This phone is already registered");
        }
        Merchant merchant = merchants.save(new Merchant(merchantName.trim(), clock.instant()));
        Operator operator = new Operator(merchant, phones.encrypt(phone), lookup, encoder.encode(password), clock.instant());
        try {
            operators.saveAndFlush(operator);
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(409, "PHONE_ALREADY_REGISTERED", "This phone is already registered");
        }
        return new SignedIn(operator.getId(), merchant.getId(), merchant.getName(), phone,
                operator.getPhoneVerifiedAt() != null, operator.getCredentialVersion());
    }

    @Transactional(noRollbackFor = ApiException.class)
    public SignedIn login(String phoneInput, String password) {
        String phone = phones.normalize(phoneInput);
        Operator operator = operators.findByPhoneLookupForUpdate(phones.lookup(phone)).orElse(null);
        if (operator == null) {
            encoder.matches(password, dummyHash);
            throw invalidCredentials();
        }
        if (!operator.isActive()) throw invalidCredentials();
        Instant now = clock.instant();
        if (operator.isLoginLocked(now)) throw rateLimited();
        if (!encoder.matches(password, operator.getPasswordHash())) {
            operator.recordFailedLogin(now);
            if (operator.isLoginLocked(now)) throw rateLimited();
            throw invalidCredentials();
        }
        operator.clearFailedLogins();
        Merchant merchant = operator.getMerchant();
        return new SignedIn(operator.getId(), merchant.getId(), merchant.getName(), phone,
                operator.getPhoneVerifiedAt() != null, operator.getCredentialVersion());
    }

    @Transactional(readOnly = true)
    public boolean isCurrent(AuthPrincipal principal) {
        return operators.findById(principal.operatorId())
                .filter(Operator::isActive)
                .map(operator -> operator.getCredentialVersion() == principal.credentialVersion())
                .orElse(false);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void changePassword(UUID operatorId, String currentPassword, String newPassword) {
        Operator operator = activeOperator(operatorId);
        confirmPassword(operator, currentPassword);
        operator.changePassword(encoder.encode(newPassword));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public void withdraw(UUID operatorId, String password) {
        Operator operator = activeOperator(operatorId);
        confirmPassword(operator, password);
        operator.withdraw(clock.instant());
    }

    private Operator activeOperator(UUID id) {
        return operators.findByIdForUpdate(id).filter(Operator::isActive)
                .orElseThrow(() -> new ApiException(401, "UNAUTHORIZED", "Authentication required"));
    }

    private void confirmPassword(Operator operator, String password) {
        Instant now = clock.instant();
        if (operator.isLoginLocked(now)) throw rateLimited();
        if (!encoder.matches(password, operator.getPasswordHash())) {
            operator.recordFailedLogin(now);
            if (operator.isLoginLocked(now)) throw rateLimited();
            throw new ApiException(400, "CURRENT_PASSWORD_INVALID", "Current password is incorrect");
        }
        operator.clearFailedLogins();
    }

    private static ApiException invalidCredentials() {
        return new ApiException(401, "INVALID_CREDENTIALS", "Invalid phone or password");
    }

    private static ApiException rateLimited() {
        return new ApiException(429, "LOGIN_RATE_LIMITED", "Try again later");
    }

    public record SignedIn(UUID operatorId, UUID merchantId, String merchantName,
                           String phone, boolean phoneVerified, long credentialVersion) { }
}

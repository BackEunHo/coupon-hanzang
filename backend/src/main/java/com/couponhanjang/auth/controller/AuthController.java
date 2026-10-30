package com.couponhanjang.auth.controller;

import com.couponhanjang.auth.dto.request.LoginRequest;
import com.couponhanjang.auth.dto.request.PasswordChangeRequest;
import com.couponhanjang.auth.dto.request.SignupRequest;
import com.couponhanjang.auth.dto.request.WithdrawalRequest;
import com.couponhanjang.auth.dto.response.AuthResponse;
import com.couponhanjang.auth.security.AuthPrincipal;
import com.couponhanjang.auth.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService service;
    private final Clock clock;
    private final HttpSessionSecurityContextRepository contextRepository;

    public AuthController(AuthService service, Clock clock, HttpSessionSecurityContextRepository contextRepository) {
        this.service = service;
        this.clock = clock;
        this.contextRepository = contextRepository;
    }

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        AuthService.SignedIn signedIn = service.signup(input.merchantName(), input.phone(), input.password());
        establishSession(signedIn, request, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(AuthResponse.from(signedIn));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        AuthService.SignedIn signedIn = service.login(input.phone(), input.password());
        establishSession(signedIn, request, response);
        return AuthResponse.from(signedIn);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        endSession(request);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody PasswordChangeRequest input,
            HttpServletRequest request) {
        service.changePassword(principal.operatorId(), input.currentPassword(), input.newPassword());
        endSession(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/withdrawal")
    public ResponseEntity<Void> withdraw(@AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody WithdrawalRequest input,
            HttpServletRequest request) {
        service.withdraw(principal.operatorId(), input.password());
        endSession(request);
        return ResponseEntity.noContent().build();
    }

    private void establishSession(AuthService.SignedIn signedIn, HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession existing = request.getSession(false);
        if (existing != null)
            existing.invalidate();
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(86_400);
        AuthPrincipal principal = new AuthPrincipal(signedIn.operatorId(), signedIn.credentialVersion(),
                clock.instant());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_MERCHANT"))));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);
    }

    private void endSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null)
            session.invalidate();
        SecurityContextHolder.clearContext();
    }

}

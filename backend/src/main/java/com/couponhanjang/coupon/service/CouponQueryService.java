package com.couponhanjang.coupon.service;

import com.couponhanjang.common.api.PageResponse;
import com.couponhanjang.common.exception.ApiException;
import com.couponhanjang.coupon.dto.CouponResponse;
import com.couponhanjang.coupon.entity.Coupon;
import com.couponhanjang.coupon.entity.CouponStatus;
import com.couponhanjang.coupon.entity.Customer;
import com.couponhanjang.coupon.repository.CouponRepository;
import com.couponhanjang.coupon.repository.CustomerRepository;
import com.couponhanjang.template.service.CouponTemplateService;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class CouponQueryService {
    private final CouponRepository coupons;
    private final CustomerRepository customers;
    private final CustomerPhoneProtector phones;
    private final Clock clock;

    public CouponQueryService(CouponRepository coupons, CustomerRepository customers,
            CustomerPhoneProtector phones, Clock clock) {
        this.coupons = coupons;
        this.customers = customers;
        this.phones = phones;
        this.clock = clock;
    }

    public CouponResponse get(UUID merchantId, UUID id) {
        return response(coupons.findByIdAndMerchantId(id, merchantId)
                .orElseThrow(CouponTemplateService::notFound));
    }

    public CouponResponse response(Coupon coupon) {
        Customer customer = coupon.getCustomerId() == null ? null
                : customers.findById(coupon.getCustomerId()).orElse(null);
        boolean deleted = customer == null || customer.isDeleted();
        CouponStatus status = coupon.effectiveStatus(clock.instant());
        String closureReason = status == CouponStatus.EXPIRED ? "EXPIRED"
                : status == CouponStatus.CLOSED_INACTIVE ? "INACTIVE_RETENTION" : null;
        return new CouponResponse(coupon.getId(), coupon.getTemplateId(), coupon.getTitle(),
                coupon.getBenefit(), coupon.getNotice(),
                deleted ? "010-****-****" : customer.displayPhone(phones), deleted,
                coupon.getIssuedAt(), coupon.getStartsAt(), coupon.getExpiresAt(), coupon.getRedeemedAt(),
                status, coupon.getIssueMessageStatus(), closureReason, coupon.getExpectedMessageType(),
                coupon.getMessageByteLength(), coupon.getActualMessageType());
    }

    public PageResponse<CouponResponse> list(UUID merchantId, String phone, String title, String status,
            int page, int size) {
        PageResponse.validate(page, size);
        if (title != null && (title.isBlank() || title.length() > 100)) {
            throw new ApiException(400, "INVALID_REQUEST", "Invalid search title");
        }
        String lookup = phone == null ? null : phones.lookup(merchantId, phones.normalize(phone));
        CouponStatus filter;
        try {
            filter = status == null ? null : CouponStatus.valueOf(status);
        } catch (IllegalArgumentException error) {
            throw new ApiException(400, "INVALID_REQUEST", "Invalid coupon status");
        }
        Instant now = clock.instant();
        var result = coupons.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("merchantId"), merchantId));
            if (title != null) {
                String escaped = title.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                predicates.add(cb.like(root.get("title"), "%" + escaped + "%", '\\'));
            }
            if (lookup != null) {
                var subquery = query.subquery(UUID.class);
                var customer = subquery.from(Customer.class);
                subquery.select(customer.get("id")).where(
                        cb.equal(customer.get("merchantId"), merchantId),
                        cb.equal(customer.get("phoneLookup"), lookup), cb.isNull(customer.get("deletedAt")));
                predicates.add(root.get("customerId").in(subquery));
            }
            if (filter != null) {
                Predicate expirable = root.get("status").in(
                        CouponStatus.PENDING_SEND, CouponStatus.ACTIVE, CouponStatus.SEND_FAILED);
                if (filter == CouponStatus.EXPIRED) {
                    predicates.add(cb.or(cb.equal(root.get("status"), filter),
                            cb.and(expirable, cb.lessThanOrEqualTo(root.get("expiresAt"), now))));
                } else {
                    predicates.add(cb.equal(root.get("status"), filter));
                    if (filter == CouponStatus.ACTIVE || filter == CouponStatus.PENDING_SEND
                            || filter == CouponStatus.SEND_FAILED) {
                        predicates.add(cb.greaterThan(root.get("expiresAt"), now));
                    }
                }
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        }, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "issuedAt", "id")));
        return PageResponse.from(result.map(this::response));
    }
}

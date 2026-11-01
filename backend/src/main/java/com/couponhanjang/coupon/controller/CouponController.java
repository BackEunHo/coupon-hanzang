package com.couponhanjang.coupon.controller;

import com.couponhanjang.auth.security.AuthPrincipal;
import com.couponhanjang.common.api.JsonInput;
import com.couponhanjang.common.api.PageResponse;
import com.couponhanjang.coupon.dto.CouponResponse;
import com.couponhanjang.coupon.service.CouponIssueService;
import com.couponhanjang.coupon.service.CouponQueryService;
import com.couponhanjang.merchant.service.MerchantAccessService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/coupons")
public class CouponController {
    private final CouponIssueService issue;
    private final CouponQueryService query;
    private final MerchantAccessService access;

    public CouponController(CouponIssueService issue, CouponQueryService query, MerchantAccessService access) {
        this.issue = issue;
        this.query = query;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<CouponResponse> issue(@AuthenticationPrincipal AuthPrincipal user,
            @RequestHeader("Idempotency-Key") String key,
            @RequestBody JsonNode body) {
        UUID requestKey = JsonInput.uuid(key);
        var result = issue.issue(access.requireActiveMerchantId(user.operatorId()),
                user.operatorId(), requestKey, body);
        return ResponseEntity.accepted().location(URI.create("/api/v1/coupons/" + result.id())).body(result);
    }

    @GetMapping
    public PageResponse<CouponResponse> list(@AuthenticationPrincipal AuthPrincipal user,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return query.list(access.requireActiveMerchantId(user.operatorId()), phone, title, status, page, size);
    }

    @GetMapping("/{id}")
    public CouponResponse get(@AuthenticationPrincipal AuthPrincipal user, @PathVariable UUID id) {
        return query.get(access.requireActiveMerchantId(user.operatorId()), id);
    }
}

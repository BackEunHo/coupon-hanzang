package com.couponhanjang.template.controller;

import com.couponhanjang.auth.security.AuthPrincipal;
import com.couponhanjang.common.api.PageResponse;
import com.couponhanjang.merchant.service.MerchantAccessService;
import com.couponhanjang.template.dto.TemplateResponse;
import com.couponhanjang.template.service.CouponTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/templates")
public class CouponTemplateController {
    private final CouponTemplateService service;
    private final MerchantAccessService access;

    public CouponTemplateController(CouponTemplateService service, MerchantAccessService access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping
    public ResponseEntity<TemplateResponse> create(@AuthenticationPrincipal AuthPrincipal user,
            @RequestBody JsonNode body) {
        var result = service.create(access.requireActiveMerchantId(user.operatorId()), body);
        return ResponseEntity.created(URI.create("/api/v1/templates/" + result.id())).body(result);
    }

    @GetMapping
    public PageResponse<TemplateResponse> list(@AuthenticationPrincipal AuthPrincipal user,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(access.requireActiveMerchantId(user.operatorId()), page, size);
    }

    @GetMapping("/{id}")
    public TemplateResponse get(@AuthenticationPrincipal AuthPrincipal user, @PathVariable UUID id) {
        return service.get(access.requireActiveMerchantId(user.operatorId()), id);
    }

    @PatchMapping("/{id}")
    public TemplateResponse patch(@AuthenticationPrincipal AuthPrincipal user, @PathVariable UUID id,
            @RequestBody JsonNode body) {
        return service.patch(access.requireActiveMerchantId(user.operatorId()), id, body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal user, @PathVariable UUID id) {
        service.delete(access.requireActiveMerchantId(user.operatorId()), id);
        return ResponseEntity.noContent().build();
    }
}

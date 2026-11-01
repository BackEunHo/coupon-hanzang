package com.couponhanjang.template.service;

import com.couponhanjang.common.api.PageResponse;
import com.couponhanjang.common.exception.ApiException;
import com.couponhanjang.template.dto.TemplateResponse;
import com.couponhanjang.template.dto.TemplateValues;
import com.couponhanjang.template.entity.CouponTemplate;
import com.couponhanjang.template.repository.CouponTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional
public class CouponTemplateService {
    private final CouponTemplateRepository templates;
    private final Clock clock;

    public CouponTemplateService(CouponTemplateRepository templates, Clock clock) {
        this.templates = templates;
        this.clock = clock;
    }

    public TemplateResponse create(UUID merchantId, JsonNode body) {
        var template = new CouponTemplate(merchantId, TemplateValues.parse(body, null), clock.instant());
        return TemplateResponse.from(templates.save(template));
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateResponse> list(UUID merchantId, int page, int size) {
        PageResponse.validate(page, size);
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return PageResponse.from(templates.findAllByMerchantIdAndDeletedAtIsNull(merchantId, pageable)
                .map(TemplateResponse::from));
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(UUID merchantId, UUID id) {
        return TemplateResponse.from(templates.findByIdAndMerchantIdAndDeletedAtIsNull(id, merchantId)
                .orElseThrow(CouponTemplateService::notFound));
    }

    public TemplateResponse patch(UUID merchantId, UUID id, JsonNode body) {
        CouponTemplate template = templates.findForChange(id, merchantId)
                .orElseThrow(CouponTemplateService::notFound);
        template.update(TemplateValues.parse(body, template.values()), clock.instant());
        return TemplateResponse.from(template);
    }

    public void delete(UUID merchantId, UUID id) {
        templates.findForChange(id, merchantId).orElseThrow(CouponTemplateService::notFound)
                .delete(clock.instant());
    }

    public static ApiException notFound() {
        return new ApiException(404, "RESOURCE_NOT_FOUND", "Resource not found");
    }
}

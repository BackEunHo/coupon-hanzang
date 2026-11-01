package com.couponhanjang.template.repository;

import com.couponhanjang.template.entity.CouponTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.domain.*;
import java.util.*;

public interface CouponTemplateRepository extends JpaRepository<CouponTemplate, UUID> {
    Page<CouponTemplate> findAllByMerchantIdAndDeletedAtIsNull(UUID merchantId, Pageable page);

    Optional<CouponTemplate> findByIdAndMerchantIdAndDeletedAtIsNull(UUID id, UUID merchantId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select t from CouponTemplate t where t.id = :id and t.merchantId = :merchantId and t.deletedAt is null")
    Optional<CouponTemplate> findForIssue(UUID id, UUID merchantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from CouponTemplate t where t.id = :id and t.merchantId = :merchantId and t.deletedAt is null")
    Optional<CouponTemplate> findForChange(UUID id, UUID merchantId);
}

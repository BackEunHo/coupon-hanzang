package com.couponhanjang.coupon.repository;

import com.couponhanjang.coupon.entity.Coupon;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface CouponRepository extends JpaRepository<Coupon, UUID>, JpaSpecificationExecutor<Coupon> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coupon c where c.merchantId=:merchantId and c.id=:couponId")
    Optional<Coupon> findForRedemption(@Param("merchantId") UUID merchantId, @Param("couponId") UUID couponId);

    Optional<Coupon> findByIdAndMerchantId(UUID id, UUID merchantId);
}

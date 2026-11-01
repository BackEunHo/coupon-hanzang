package com.couponhanjang.coupon.repository;

import com.couponhanjang.coupon.entity.Coupon;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface CouponRepository extends JpaRepository<Coupon, UUID>, JpaSpecificationExecutor<Coupon> {
    Optional<Coupon> findByIdAndMerchantId(UUID id, UUID merchantId);
}

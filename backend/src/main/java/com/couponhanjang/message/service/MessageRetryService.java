package com.couponhanjang.message.service;

import com.couponhanjang.message.repository.MessageDeliveryStore;
import com.couponhanjang.coupon.repository.CouponIssuanceStore;
import com.couponhanjang.coupon.service.CustomerPhoneProtector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.UUID;

@Service
public class MessageRetryService {
    private final MessageDeliveryStore store;
    private final CouponIssuanceStore quota;
    private final CustomerPhoneProtector phones;
    private final int limit;

    public MessageRetryService(MessageDeliveryStore store, CouponIssuanceStore quota, CustomerPhoneProtector phones,
            @Value("${app.quota.monthly-message-limit}") int limit) {
        this.store = store;
        this.quota = quota;
        this.phones = phones;
        this.limit = limit;
    }

    public MessageDeliveryStore.RetryResponse retry(UUID merchantId, UUID couponId, UUID key) {
        return store.retry(merchantId, couponId, key, quota, phones, limit);
    }
}

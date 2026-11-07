package com.couponhanjang.message.service;

import com.couponhanjang.template.dto.TemplateValues;
import com.couponhanjang.coupon.service.CouponExpiryCalculator;
import org.springframework.stereotype.Component;
import java.time.format.DateTimeFormatter;

@Component
public class CouponMessageRenderer {
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        .withZone(CouponExpiryCalculator.ZONE);

        public String render(TemplateValues values, String merchantName,
                        CouponExpiryCalculator.ResolvedExpiry validity, String displayedCode) {
                return "[" + merchantName + "] " + values.title()
                                + "\n혜택: " + values.benefit()
                                + "\n사용 기간: " + TIME.format(validity.startsAt()) + " ~ "
                                + TIME.format(validity.expiresAt())
                                + "\n안내: " + values.notice()
                                + "\n인증 번호: " + displayedCode
                                + "\n번호 사용 불가 또는 발급 후 1년 비활동 시 유효기간이 남아도 사용할 수 없습니다.";
        }
}

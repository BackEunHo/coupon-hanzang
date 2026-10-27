package com.couponhanjang;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class CouponHanjangApplication {
    public static void main(String[] args) {
        SpringApplication.run(CouponHanjangApplication.class, args);
    }
}

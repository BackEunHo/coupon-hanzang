package com.couponhanjang.coupon.repository;

import com.couponhanjang.coupon.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {
}

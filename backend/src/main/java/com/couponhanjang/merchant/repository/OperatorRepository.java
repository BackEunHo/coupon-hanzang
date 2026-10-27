package com.couponhanjang.merchant.repository;

import com.couponhanjang.merchant.entity.Operator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface OperatorRepository extends JpaRepository<Operator, UUID> {
    Optional<Operator> findByPhoneLookup(String phoneLookup);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Operator o where o.phoneLookup = :lookup")
    Optional<Operator> findByPhoneLookupForUpdate(@Param("lookup") String lookup);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Operator o where o.id = :id")
    Optional<Operator> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByPhoneLookup(String phoneLookup);
}

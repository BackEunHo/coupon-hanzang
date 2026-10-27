package com.couponhanjang.merchant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "merchants")
public class Merchant {
    public enum Status { FREE_ACTIVE, WITHDRAWN }

    @Id
    private UUID id;
    @Column(nullable = false, length = 100)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant withdrawnAt;

    protected Merchant() { }

    public Merchant(String name, Instant now) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.status = Status.FREE_ACTIVE;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Status getStatus() { return status; }

    public void withdraw(Instant now) {
        status = Status.WITHDRAWN;
        withdrawnAt = now;
    }
}

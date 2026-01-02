package com.mainthread.audit.domain;

import org.hibernate.envers.Audited;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Audited
public class CreditAccount extends PanacheEntity {

    public String owner;

    public long creditLimit;

    @Enumerated(EnumType.STRING)
    public Status status;

    public enum Status {
        ACTIVE,
        SUSPENDED
    }
}

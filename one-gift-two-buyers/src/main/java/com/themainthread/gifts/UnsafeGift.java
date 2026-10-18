package com.themainthread.gifts;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

/** Deliberately unversioned comparison for the local race demonstration. */
@Entity
@Table(name = "unsafe_gift")
public class UnsafeGift extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(nullable = false, length = 120)
    public String name;

    @Column(length = 80)
    public String reservedBy;
}

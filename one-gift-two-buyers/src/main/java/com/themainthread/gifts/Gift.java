package com.themainthread.gifts;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;

@Entity
@Table(name = "gift")
public class Gift extends PanacheEntityBase {
    @Id
    public UUID id;

    @Column(nullable = false, length = 120)
    public String name;

    @Column(length = 80)
    public String reservedBy;

    @Version
    public long version;
}

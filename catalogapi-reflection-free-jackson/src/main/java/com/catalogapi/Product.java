package com.catalogapi;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class Product extends PanacheEntity {

    @Column(nullable = false, unique = true)
    public String sku;

    @Column(nullable = false)
    public String name;

    @Column(nullable = false)
    public int priceCents;

    @Column(nullable = false)
    public String category;
}

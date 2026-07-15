package com.themainthread.checkout;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepository;

@ApplicationScoped
public class OrderRepository implements PanacheRepository<PurchaseOrder> {
}

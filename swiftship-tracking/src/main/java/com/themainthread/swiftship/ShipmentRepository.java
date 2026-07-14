package com.themainthread.swiftship;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

@ApplicationScoped
public class ShipmentRepository implements PanacheRepositoryBase<Shipment, String> {

    public List<Shipment> listByTrackingNumber() {
        return find("order by trackingNumber").list();
    }
}

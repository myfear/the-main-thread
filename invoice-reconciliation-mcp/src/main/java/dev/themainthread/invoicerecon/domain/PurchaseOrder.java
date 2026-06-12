package dev.themainthread.invoicerecon.domain;

import java.math.BigDecimal;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_order")
public class PurchaseOrder extends PanacheEntity {

    @Column(name = "po_number", nullable = false, unique = true)
    public String poNumber;

    @Column(name = "supplier_id", nullable = false)
    public String supplierId;

    @Column(nullable = false)
    public int quantity;

    @Column(name = "unit_price", nullable = false)
    public BigDecimal unitPrice;

    public static PurchaseOrder findByPoNumber(String poNumber) {
        return find("poNumber", poNumber).firstResult();
    }
}

package dev.themainthread.invoicerecon.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "goods_receipt")
public class GoodsReceipt extends PanacheEntity {

    @Column(name = "po_number", nullable = false)
    public String poNumber;

    @Column(name = "quantity_received", nullable = false)
    public int quantityReceived;

    public static GoodsReceipt findByPoNumber(String poNumber) {
        return find("poNumber", poNumber).firstResult();
    }
}

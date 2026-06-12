package dev.themainthread.invoicerecon.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice")
public class Invoice extends PanacheEntity {

    @Column(name = "invoice_number", nullable = false, unique = true)
    public String invoiceNumber;

    @Column(name = "supplier_id", nullable = false)
    public String supplierId;

    @Column(name = "invoice_date", nullable = false)
    public LocalDate invoiceDate;

    @Column(name = "purchase_order_number")
    public String purchaseOrderNumber;

    @Column(nullable = false)
    public int quantity;

    @Column(name = "unit_price", nullable = false)
    public BigDecimal unitPrice;

    @Column(name = "cost_center")
    public String costCenter;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public InvoiceStatus status = InvoiceStatus.OPEN;

    @Column(nullable = false)
    public boolean posted;

    public static java.util.List<Invoice> findOpenForSupplierAndPeriod(
            String supplierId,
            LocalDate from,
            LocalDate to) {
        return list(
                "supplierId = ?1 and status = ?2 and invoiceDate >= ?3 and invoiceDate <= ?4 order by id",
                supplierId,
                InvoiceStatus.OPEN,
                from,
                to);
    }
}

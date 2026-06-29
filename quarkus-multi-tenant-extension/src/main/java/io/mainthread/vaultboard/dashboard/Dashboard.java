package io.mainthread.vaultboard.dashboard;

import java.math.BigDecimal;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "dashboards")
public class Dashboard extends PanacheEntity {

    @Column(nullable = false)
    public String name;

    @Column(name = "owner_email", nullable = false)
    public String ownerEmail;

    @Column(name = "monthly_budget", nullable = false, precision = 12, scale = 2)
    public BigDecimal monthlyBudget;
}

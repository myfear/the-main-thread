package com.themainthread.timetraveler;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Temporal;

@Entity
@Table(name = "accounts")
@Temporal
@Temporal.HistoryTable(name = "account_history")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, length = 32)
    private String accountNumber;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccountStatus status;

    protected Account() {
    }

    public Account(String accountNumber, BigDecimal balance, AccountStatus status) {
        this.accountNumber = accountNumber;
        this.balance = balance;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void changeBalance(BigDecimal balance, AccountStatus status) {
        this.balance = balance;
        this.status = status;
    }
}

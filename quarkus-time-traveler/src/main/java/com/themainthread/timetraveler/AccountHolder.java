package com.themainthread.timetraveler;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Audited;

@Entity
@Table(name = "account_holders")
@Audited
@Audited.Table(name = "account_holder_aud")
public class AccountHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String externalId;

    @Column(nullable = false, length = 120)
    private String fullName;

    @Column(nullable = false, length = 160)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private KycStatus kycStatus;

    protected AccountHolder() {
    }

    public AccountHolder(String externalId, String fullName, String email, KycStatus kycStatus) {
        this.externalId = externalId;
        this.fullName = fullName;
        this.email = email;
        this.kycStatus = kycStatus;
    }

    public Long getId() {
        return id;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public KycStatus getKycStatus() {
        return kycStatus;
    }

    public void update(String fullName, String email, KycStatus kycStatus) {
        this.fullName = fullName;
        this.email = email;
        this.kycStatus = kycStatus;
    }
}

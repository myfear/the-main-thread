package com.themainthread.office;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "office_project")
public class Project {
    @Id
    public UUID id;
    @Column(nullable = false, length = 100)
    public String name;
    @Column(name = "created_at", nullable = false)
    public Instant createdAt;
}

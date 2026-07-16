package com.themainthread.releaseradar.domain;

import java.time.LocalDateTime;

import io.quarkiverse.qubit.QubitEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "release_issue")
public class Issue extends QubitEntity {

    @Column(name = "issue_key", nullable = false, unique = true)
    public String key;

    @Column(nullable = false)
    public String service;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IssueSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public IssueStatus status;

    @Column(name = "opened_at", nullable = false)
    public LocalDateTime openedAt;

    @Column(name = "affected_users", nullable = false)
    public int affectedUsers;
}

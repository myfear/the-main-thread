package com.themainthread.timetraveler;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import org.hibernate.annotations.Changelog;
import org.hibernate.audit.TrackingModifiedEntitiesChangelogMapping;

@Entity
@Changelog
@Table(name = "ledger_revision")
public class LedgerRevision extends TrackingModifiedEntitiesChangelogMapping {
}

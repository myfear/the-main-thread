package com.themainthread.timetraveler;

import org.hibernate.audit.spi.ChangelogSupplier;

public class LedgerRevisionSupplier extends ChangelogSupplier<Long> {

    public LedgerRevisionSupplier() {
        super(LedgerRevision.class, "id", "timestamp", "modifiedEntityNames", null);
    }
}

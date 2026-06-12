package com.themainthread.timetraveler;

import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.internal.ChangesetCoordinatorInitiator;
import org.hibernate.service.spi.ServiceContributor;

public class ChangesetCoordinatorContributor implements ServiceContributor {

    @Override
    public void contribute(StandardServiceRegistryBuilder builder) {
        builder.addInitiator(ChangesetCoordinatorInitiator.INSTANCE);
    }
}

package com.themainthread.progress.persistence;

import java.util.UUID;

import com.themainthread.progress.domain.ImportJob;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ImportJobRepository implements PanacheRepositoryBase<ImportJob, UUID> {
}

package com.themainthread.planner;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "dock_doors")
public class DockDoorEntity extends PanacheEntityBase {

    @Id
    public String id;

    public String name;
}

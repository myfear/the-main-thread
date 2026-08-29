package com.themainthread.planner;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "bookings")
public class BookingEntity extends PanacheEntityBase {

    @Id
    public String id;

    public String reference;

    @ManyToOne(optional = false)
    @JoinColumn(name = "door_id")
    public DockDoorEntity door;

    @Column(name = "starts_at")
    public Instant startsAt;

    @Column(name = "ends_at")
    public Instant endsAt;

    @Version
    public long version;
}

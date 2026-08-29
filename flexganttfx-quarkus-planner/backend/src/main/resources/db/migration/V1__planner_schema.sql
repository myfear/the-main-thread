CREATE TABLE dock_doors (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(128) NOT NULL
);

CREATE TABLE bookings (
    id VARCHAR(64) PRIMARY KEY,
    reference VARCHAR(64) NOT NULL,
    door_id VARCHAR(64) NOT NULL REFERENCES dock_doors (id),
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO dock_doors (id, name) VALUES
    ('door-1', 'Door 1'),
    ('door-2', 'Door 2'),
    ('door-3', 'Door 3'),
    ('door-4', 'Door 4'),
    ('door-5', 'Door 5');

INSERT INTO bookings (id, reference, door_id, starts_at, ends_at, version) VALUES
    ('booking-42', 'TRUCK-1042', 'door-3', '2026-08-20 08:00:00+00', '2026-08-20 09:30:00+00', 0),
    ('booking-17', 'TRUCK-2017', 'door-5', '2026-08-20 09:00:00+00', '2026-08-20 10:30:00+00', 0),
    ('booking-88', 'TRUCK-3088', 'door-1', '2026-08-20 07:00:00+00', '2026-08-20 08:00:00+00', 0);

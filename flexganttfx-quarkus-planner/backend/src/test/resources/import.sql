INSERT INTO dock_doors (id, name) VALUES
    ('door-1', 'Door 1'),
    ('door-2', 'Door 2'),
    ('door-3', 'Door 3'),
    ('door-4', 'Door 4'),
    ('door-5', 'Door 5');

INSERT INTO bookings (id, reference, door_id, starts_at, ends_at, version) VALUES
    ('booking-42', 'TRUCK-1042', 'door-3', TIMESTAMP WITH TIME ZONE '2026-08-20 08:00:00+00', TIMESTAMP WITH TIME ZONE '2026-08-20 09:30:00+00', 0),
    ('booking-17', 'TRUCK-2017', 'door-5', TIMESTAMP WITH TIME ZONE '2026-08-20 09:00:00+00', TIMESTAMP WITH TIME ZONE '2026-08-20 10:30:00+00', 0),
    ('booking-88', 'TRUCK-3088', 'door-1', TIMESTAMP WITH TIME ZONE '2026-08-20 07:00:00+00', TIMESTAMP WITH TIME ZONE '2026-08-20 08:00:00+00', 0);

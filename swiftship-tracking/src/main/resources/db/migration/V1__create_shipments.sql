CREATE TABLE shipments (
    tracking_number VARCHAR(24) PRIMARY KEY,
    destination VARCHAR(120) NOT NULL,
    current_status VARCHAR(32) NOT NULL,
    current_location VARCHAR(120) NOT NULL,
    estimated_delivery DATE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO shipments (
    tracking_number,
    destination,
    current_status,
    current_location,
    estimated_delivery,
    updated_at,
    version
) VALUES
    ('SWIFT-1001', 'Berlin, Germany', 'IN_TRANSIT', 'Leipzig Hub', '2026-07-16', '2026-07-14T06:30:00Z', 0),
    ('SWIFT-1002', 'Paris, France', 'OUT_FOR_DELIVERY', 'Paris Depot', '2026-07-14', '2026-07-14T07:15:00Z', 0),
    ('SWIFT-1003', 'Warsaw, Poland', 'DELIVERED', 'Warsaw, Poland', '2026-07-13', '2026-07-13T15:42:00Z', 0);

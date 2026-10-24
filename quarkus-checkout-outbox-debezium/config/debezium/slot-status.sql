SELECT slot_name,
       active,
       restart_lsn,
       confirmed_flush_lsn,
       wal_status,
       pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)::bigint AS wal_distance_bytes,
       pg_size_pretty(pg_wal_lsn_diff(pg_current_wal_lsn(), restart_lsn)::bigint) AS wal_distance
FROM pg_replication_slots
WHERE slot_name = 'checkout_outbox_slot';

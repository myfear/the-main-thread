SET LOCAL lock_timeout = '5s';

UPDATE customer
SET display_name = full_name
WHERE display_name IS NULL;

ALTER TABLE customer
    ADD CONSTRAINT customer_display_name_present
    CHECK (display_name IS NOT NULL) NOT VALID;

ALTER TABLE customer
    VALIDATE CONSTRAINT customer_display_name_present;

ALTER TABLE customer
    ALTER COLUMN display_name SET NOT NULL;

ALTER TABLE customer
    DROP CONSTRAINT customer_display_name_present;

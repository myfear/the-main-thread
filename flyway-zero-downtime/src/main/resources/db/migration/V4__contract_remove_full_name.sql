SET LOCAL lock_timeout = '5s';

DROP TRIGGER customer_name_compatibility ON customer;
DROP FUNCTION sync_customer_name_columns();

ALTER TABLE customer DROP COLUMN full_name;

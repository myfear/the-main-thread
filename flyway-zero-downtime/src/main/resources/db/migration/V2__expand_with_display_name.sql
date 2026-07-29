SET LOCAL lock_timeout = '5s';

ALTER TABLE customer ADD COLUMN display_name TEXT;

CREATE FUNCTION sync_customer_name_columns()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.full_name IS NULL THEN
            NEW.full_name := NEW.display_name;
        END IF;
        IF NEW.display_name IS NULL THEN
            NEW.display_name := NEW.full_name;
        END IF;
    ELSIF NEW.full_name IS DISTINCT FROM OLD.full_name THEN
        NEW.display_name := NEW.full_name;
    ELSIF NEW.display_name IS DISTINCT FROM OLD.display_name THEN
        NEW.full_name := NEW.display_name;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER customer_name_compatibility
BEFORE INSERT OR UPDATE OF full_name, display_name ON customer
FOR EACH ROW
EXECUTE FUNCTION sync_customer_name_columns();

-- The embedding integration maps @EmbeddedText to Hibernate LONG32VARCHAR.
ALTER TABLE memory_record ALTER COLUMN text TYPE text;
ALTER TABLE memory_record ADD CONSTRAINT atomic_fact_length CHECK (char_length(text) BETWEEN 1 AND 1000);

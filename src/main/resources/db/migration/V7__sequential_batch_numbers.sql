-- Preserve original supplier/legacy identifiers before introducing internal batch numbers.
ALTER TABLE material_batches ADD COLUMN supplier_batch_number VARCHAR(100);
UPDATE material_batches SET supplier_batch_number=batch_number;

CREATE TEMP TABLE batch_number_conversion ON COMMIT DROP AS
WITH candidates AS (
    SELECT id_material_batches,
           CASE WHEN batch_number ~ '^[0-9]{1,12}$' THEN batch_number::BIGINT ELSE NULL END AS numeric_number
    FROM material_batches
), ranked AS (
    SELECT *,ROW_NUMBER() OVER (PARTITION BY numeric_number ORDER BY id_material_batches) AS duplicate_position
    FROM candidates
), maximum AS (
    SELECT COALESCE(MAX(numeric_number),0) AS last_number FROM candidates
), replacements AS (
    SELECT id_material_batches,
           last_number+ROW_NUMBER() OVER (ORDER BY id_material_batches) AS new_number
    FROM ranked CROSS JOIN maximum
    WHERE numeric_number IS NULL OR numeric_number=0 OR duplicate_position>1
)
SELECT id_material_batches,numeric_number AS new_number
FROM ranked WHERE numeric_number>0 AND duplicate_position=1
UNION ALL SELECT id_material_batches,new_number FROM replacements;

DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM batch_number_conversion WHERE new_number>999999999999) THEN
        RAISE EXCEPTION 'No available 12-digit batch numbers for migration';
    END IF;
END $$;

UPDATE material_batches b SET batch_number=LPAD(c.new_number::TEXT,12,'0')
FROM batch_number_conversion c WHERE c.id_material_batches=b.id_material_batches;

CREATE SEQUENCE material_batch_number_seq MINVALUE 1 MAXVALUE 999999999999 NO CYCLE;
SELECT setval('material_batch_number_seq', GREATEST(COALESCE(MAX(batch_number::BIGINT),0),1),
              COALESCE(MAX(batch_number::BIGINT),0)>0) FROM material_batches;
ALTER TABLE material_batches ALTER COLUMN batch_number SET DEFAULT LPAD(nextval('material_batch_number_seq')::TEXT,12,'0');
ALTER TABLE material_batches ADD CONSTRAINT batch_number_format CHECK (batch_number ~ '^[0-9]{12}$' AND batch_number<>'000000000000');
CREATE UNIQUE INDEX material_batches_number_unique_idx ON material_batches(batch_number);

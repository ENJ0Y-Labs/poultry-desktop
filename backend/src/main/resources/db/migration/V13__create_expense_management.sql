ALTER TABLE expenses ADD COLUMN batch_id TEXT REFERENCES batches(id);

UPDATE expenses
SET batch_id = (
    SELECT batch_id
    FROM drug_records
    WHERE drug_records.id = expenses.reference_id
)
WHERE reference_type = 'DRUG_RECORD'
  AND batch_id IS NULL;

CREATE INDEX idx_expenses_farm_batch_date
    ON expenses(farm_id, batch_id, occurred_date);

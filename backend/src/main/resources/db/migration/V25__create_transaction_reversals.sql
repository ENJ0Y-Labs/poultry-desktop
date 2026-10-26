-- Durable, append-only links for voids and corrections.
-- Business services must create these rows in the same transaction as the reversal,
-- replacement, dependent recalculation, and audit entry.
CREATE TABLE transaction_reversals (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    batch_id TEXT,
    original_type TEXT NOT NULL CHECK (
        original_type IN (
            'BIRD_POPULATION_EVENT',
            'BIRD_COST_EVENT',
            'FEED_PURCHASE',
            'FEED_USAGE',
            'EGG_COLLECTION',
            'EGG_SALE',
            'BIRD_SALE',
            'EXPENSE',
            'DRUG_RECORD',
            'WEIGHT_RECORD'
        )
    ),
    original_id TEXT NOT NULL CHECK (length(trim(original_id)) > 0),
    operation_type TEXT NOT NULL CHECK (operation_type IN ('VOID', 'CORRECT')),
    replacement_type TEXT CHECK (
        replacement_type IS NULL OR replacement_type IN (
            'BIRD_POPULATION_EVENT',
            'BIRD_COST_EVENT',
            'FEED_PURCHASE',
            'FEED_USAGE',
            'EGG_COLLECTION',
            'EGG_SALE',
            'BIRD_SALE',
            'EXPENSE',
            'DRUG_RECORD',
            'WEIGHT_RECORD'
        )
    ),
    replacement_id TEXT,
    reason TEXT NOT NULL CHECK (length(trim(reason)) > 0),
    actor_user_id TEXT NOT NULL,
    occurred_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id),
    FOREIGN KEY (batch_id) REFERENCES batches(id),
    FOREIGN KEY (actor_user_id) REFERENCES users(id),
    CHECK (
        (operation_type = 'VOID' AND replacement_type IS NULL AND replacement_id IS NULL)
        OR
        (operation_type = 'CORRECT'
            AND replacement_type IS NOT NULL
            AND replacement_type = original_type
            AND length(trim(replacement_type)) > 0
            AND replacement_id IS NOT NULL
            AND length(trim(replacement_id)) > 0)
    ),
    UNIQUE (original_type, original_id)
);

CREATE INDEX idx_transaction_reversals_farm_time
    ON transaction_reversals(farm_id, occurred_at);

CREATE INDEX idx_transaction_reversals_batch_time
    ON transaction_reversals(batch_id, occurred_at);

CREATE INDEX idx_transaction_reversals_replacement
    ON transaction_reversals(replacement_type, replacement_id);

CREATE TRIGGER transaction_reversals_no_update
BEFORE UPDATE ON transaction_reversals
BEGIN
    SELECT RAISE(ABORT, 'TRANSACTION_REVERSALS_APPEND_ONLY');
END;

CREATE TRIGGER transaction_reversals_no_delete
BEFORE DELETE ON transaction_reversals
BEGIN
    SELECT RAISE(ABORT, 'TRANSACTION_REVERSALS_APPEND_ONLY');
END;

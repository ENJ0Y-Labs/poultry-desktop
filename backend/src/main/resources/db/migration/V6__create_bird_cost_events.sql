CREATE TABLE bird_cost_events (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    event_date TEXT NOT NULL,
    event_type TEXT NOT NULL CHECK (
        event_type IN ('ADDITIONAL_COST', 'TRANSFER_IN_COST')
    ),
    amount_minor INTEGER NOT NULL CHECK (amount_minor >= 0),
    reference_batch_id TEXT,
    reason TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id),
    FOREIGN KEY (reference_batch_id) REFERENCES batches(id),
    CHECK (
        (event_type = 'TRANSFER_IN_COST' AND reference_batch_id IS NOT NULL)
        OR
        (event_type = 'ADDITIONAL_COST' AND reference_batch_id IS NULL)
    )
);

CREATE INDEX idx_bird_cost_events_batch_date
    ON bird_cost_events(batch_id, event_date, created_at);

CREATE INDEX idx_bird_cost_events_reference_batch
    ON bird_cost_events(reference_batch_id);

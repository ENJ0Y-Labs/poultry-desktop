CREATE TABLE bird_population_events (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    event_date TEXT NOT NULL,
    event_type TEXT NOT NULL CHECK (
        event_type IN ('MORTALITY', 'CULLING', 'SOLD', 'TRANSFER_IN', 'TRANSFER_OUT')
    ),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    reference_batch_id TEXT,
    reason TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id),
    FOREIGN KEY (reference_batch_id) REFERENCES batches(id),
    CHECK (
        (event_type IN ('TRANSFER_IN', 'TRANSFER_OUT') AND reference_batch_id IS NOT NULL)
        OR
        (event_type IN ('MORTALITY', 'CULLING', 'SOLD') AND reference_batch_id IS NULL)
    )
);

CREATE INDEX idx_bird_population_events_batch_date
    ON bird_population_events(batch_id, event_date, created_at);

CREATE INDEX idx_bird_population_events_reference_batch
    ON bird_population_events(reference_batch_id);

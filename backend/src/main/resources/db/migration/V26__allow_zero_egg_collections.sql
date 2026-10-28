-- Preserve existing egg collection history while allowing an explicit zero-production day.
-- Missing dates remain absent rows; a row with 0 good and 0 cracked eggs is a recorded zero.
CREATE TABLE egg_collections_new (
    id TEXT PRIMARY KEY,
    batch_id TEXT NOT NULL,
    record_date TEXT NOT NULL,
    good_eggs INTEGER NOT NULL CHECK (good_eggs >= 0),
    cracked_eggs INTEGER NOT NULL CHECK (cracked_eggs >= 0),
    notes TEXT,
    created_at TEXT NOT NULL,
    FOREIGN KEY (batch_id) REFERENCES batches(id)
);

INSERT INTO egg_collections_new
    (id, batch_id, record_date, good_eggs, cracked_eggs, notes, created_at)
SELECT id, batch_id, record_date, good_eggs, cracked_eggs, notes, created_at
FROM egg_collections;

DROP TABLE egg_collections;
ALTER TABLE egg_collections_new RENAME TO egg_collections;

CREATE INDEX idx_egg_collections_batch_date
    ON egg_collections(batch_id, record_date, created_at);

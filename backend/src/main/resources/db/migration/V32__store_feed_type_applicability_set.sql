-- Store feed applicability as a normalized set. Existing purchases, usage rows and FIFO lots are untouched.
CREATE TABLE feed_type_applicability (
    feed_type_id TEXT NOT NULL,
    bird_type TEXT NOT NULL CHECK (bird_type IN ('LAYER', 'BROILER', 'TURKEY')),
    PRIMARY KEY (feed_type_id, bird_type),
    FOREIGN KEY (feed_type_id) REFERENCES feed_types(id) ON DELETE CASCADE
);

-- Legacy BOTH meant every supported bird type, including Turkey after V27.
INSERT INTO feed_type_applicability (feed_type_id, bird_type)
SELECT id, 'LAYER' FROM feed_types WHERE applicable_type IN ('LAYER', 'BOTH');

INSERT INTO feed_type_applicability (feed_type_id, bird_type)
SELECT id, 'BROILER' FROM feed_types WHERE applicable_type IN ('BROILER', 'BOTH');

INSERT INTO feed_type_applicability (feed_type_id, bird_type)
SELECT id, 'TURKEY' FROM feed_types WHERE applicable_type IN ('TURKEY', 'BOTH');

CREATE INDEX idx_feed_type_applicability_bird_type
    ON feed_type_applicability(bird_type, feed_type_id);

-- Extend supported bird types while preserving existing batches and feed definitions.
PRAGMA defer_foreign_keys = ON;
CREATE TABLE batches_new (
 id TEXT PRIMARY KEY, farm_id TEXT NOT NULL, house_id TEXT NOT NULL, supplier_id TEXT,
 code TEXT NOT NULL UNIQUE CHECK(length(trim(code))>0),
 batch_type TEXT NOT NULL CHECK(batch_type IN ('LAYER','BROILER','TURKEY')),
 placement_date TEXT NOT NULL, initial_bird_count INTEGER NOT NULL CHECK(initial_bird_count>0),
 original_purchase_cost_minor INTEGER CHECK(original_purchase_cost_minor IS NULL OR original_purchase_cost_minor>=0),
 status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','SOLD','CLOSED')),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
 FOREIGN KEY(farm_id) REFERENCES farms(id) ON DELETE CASCADE,
 FOREIGN KEY(house_id) REFERENCES houses(id), FOREIGN KEY(supplier_id) REFERENCES suppliers(id)
);
INSERT INTO batches_new SELECT * FROM batches;
DROP TABLE batches;
ALTER TABLE batches_new RENAME TO batches;
CREATE INDEX idx_batches_farm_id ON batches(farm_id);
CREATE INDEX idx_batches_house_id ON batches(house_id);
CREATE INDEX idx_batches_supplier_id ON batches(supplier_id);
CREATE INDEX idx_batches_type_status ON batches(batch_type,status);
CREATE TABLE batch_code_sequences_new (
 batch_type TEXT NOT NULL CHECK(batch_type IN ('LAYER','BROILER','TURKEY')),
 sequence_year INTEGER NOT NULL CHECK(sequence_year>=2000),
 next_number INTEGER NOT NULL CHECK(next_number>0), PRIMARY KEY(batch_type,sequence_year)
);
INSERT INTO batch_code_sequences_new SELECT * FROM batch_code_sequences;
DROP TABLE batch_code_sequences;
ALTER TABLE batch_code_sequences_new RENAME TO batch_code_sequences;
CREATE TABLE feed_types_new (
 id TEXT PRIMARY KEY, farm_id TEXT NOT NULL,
 name TEXT NOT NULL CHECK(length(trim(name))>0), unit TEXT NOT NULL CHECK(length(trim(unit))>0),
 applicable_type TEXT NOT NULL CHECK(applicable_type IN ('LAYER','BROILER','TURKEY','BOTH')),
 status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK(status IN ('ACTIVE','ARCHIVED')),
 created_at TEXT NOT NULL, updated_at TEXT NOT NULL,
 kg_per_unit NUMERIC CHECK(kg_per_unit IS NULL OR kg_per_unit>0),
 FOREIGN KEY(farm_id) REFERENCES farms(id), UNIQUE(farm_id,name)
);
INSERT INTO feed_types_new SELECT id,farm_id,name,unit,applicable_type,status,created_at,updated_at,kg_per_unit FROM feed_types;
DROP TABLE feed_types;
ALTER TABLE feed_types_new RENAME TO feed_types;
CREATE INDEX idx_feed_types_farm_status ON feed_types(farm_id,status);

CREATE TABLE sales_new (
 id TEXT PRIMARY KEY, farm_id TEXT NOT NULL, batch_id TEXT NOT NULL, customer_id TEXT NOT NULL,
 sale_date TEXT NOT NULL, sale_type TEXT NOT NULL CHECK(sale_type IN ('EGG','BROILER','TURKEY')),
 quantity NUMERIC NOT NULL CHECK(quantity>0), unit TEXT NOT NULL CHECK(length(trim(unit))>0),
 unit_price_minor INTEGER NOT NULL CHECK(unit_price_minor>0),
 total_amount_minor INTEGER NOT NULL CHECK(total_amount_minor>0),
 reference_type TEXT NOT NULL, reference_id TEXT NOT NULL, created_at TEXT NOT NULL,
 FOREIGN KEY(farm_id) REFERENCES farms(id), FOREIGN KEY(batch_id) REFERENCES batches(id),
 FOREIGN KEY(customer_id) REFERENCES customers(id)
);
INSERT INTO sales_new SELECT * FROM sales;
DROP TABLE sales;
ALTER TABLE sales_new RENAME TO sales;
CREATE INDEX idx_sales_farm_date ON sales(farm_id,sale_date);
CREATE INDEX idx_sales_batch_date ON sales(batch_id,sale_date);
CREATE INDEX idx_sales_customer_date ON sales(customer_id,sale_date);

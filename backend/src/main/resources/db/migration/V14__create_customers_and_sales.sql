CREATE TABLE customers (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    name TEXT NOT NULL CHECK (length(trim(name)) > 0),
    phone TEXT,
    notes TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id)
);

CREATE INDEX idx_customers_farm_name ON customers(farm_id, name);

CREATE TABLE sales (
    id TEXT PRIMARY KEY,
    farm_id TEXT NOT NULL,
    batch_id TEXT NOT NULL,
    customer_id TEXT NOT NULL,
    sale_date TEXT NOT NULL,
    sale_type TEXT NOT NULL CHECK (sale_type IN ('EGG', 'BROILER')),
    quantity NUMERIC NOT NULL CHECK (quantity > 0),
    unit TEXT NOT NULL CHECK (length(trim(unit)) > 0),
    unit_price_minor INTEGER NOT NULL CHECK (unit_price_minor > 0),
    total_amount_minor INTEGER NOT NULL CHECK (total_amount_minor > 0),
    reference_type TEXT NOT NULL,
    reference_id TEXT NOT NULL,
    created_at TEXT NOT NULL,
    FOREIGN KEY (farm_id) REFERENCES farms(id),
    FOREIGN KEY (batch_id) REFERENCES batches(id),
    FOREIGN KEY (customer_id) REFERENCES customers(id)
);

CREATE INDEX idx_sales_farm_date ON sales(farm_id, sale_date);
CREATE INDEX idx_sales_batch_date ON sales(batch_id, sale_date);
CREATE INDEX idx_sales_customer_date ON sales(customer_id, sale_date);

ALTER TABLE egg_sales ADD COLUMN customer_id TEXT REFERENCES customers(id);
ALTER TABLE bird_sales ADD COLUMN customer_id TEXT REFERENCES customers(id);

INSERT INTO customers (id, farm_id, name, phone, notes, created_at, updated_at)
SELECT lower(hex(randomblob(16))), farm_id, customer, NULL, NULL, datetime('now'), datetime('now')
FROM (
    SELECT DISTINCT b.farm_id, trim(es.customer) AS customer
    FROM egg_sales es
    JOIN batches b ON b.id = es.batch_id
    WHERE length(trim(es.customer)) > 0
    UNION
    SELECT DISTINCT b.farm_id, trim(bs.customer) AS customer
    FROM bird_sales bs
    JOIN batches b ON b.id = bs.batch_id
    WHERE length(trim(bs.customer)) > 0
);

UPDATE egg_sales
SET customer_id = (
    SELECT c.id
    FROM customers c
    JOIN batches b ON b.id = egg_sales.batch_id
    WHERE c.farm_id = b.farm_id
      AND c.name = trim(egg_sales.customer)
    LIMIT 1
)
WHERE customer_id IS NULL;

UPDATE bird_sales
SET customer_id = (
    SELECT c.id
    FROM customers c
    JOIN batches b ON b.id = bird_sales.batch_id
    WHERE c.farm_id = b.farm_id
      AND c.name = trim(bird_sales.customer)
    LIMIT 1
)
WHERE customer_id IS NULL;

INSERT INTO sales (
    id, farm_id, batch_id, customer_id, sale_date, sale_type,
    quantity, unit, unit_price_minor, total_amount_minor,
    reference_type, reference_id, created_at
)
SELECT
    es.id, b.farm_id, es.batch_id, es.customer_id, es.record_date, 'EGG',
    CAST(es.sold_eggs AS NUMERIC) / es.crate_size, 'CRATE',
    es.price_per_crate_minor, es.total_amount_minor,
    'EGG_SALE', es.id, es.created_at
FROM egg_sales es
JOIN batches b ON b.id = es.batch_id
WHERE es.customer_id IS NOT NULL;

INSERT INTO sales (
    id, farm_id, batch_id, customer_id, sale_date, sale_type,
    quantity, unit, unit_price_minor, total_amount_minor,
    reference_type, reference_id, created_at
)
SELECT
    bs.id, b.farm_id, bs.batch_id, bs.customer_id, bs.record_date, 'BROILER',
    bs.quantity, 'BIRD',
    bs.price_per_bird_minor, bs.total_amount_minor,
    'BIRD_SALE', bs.id, bs.created_at
FROM bird_sales bs
JOIN batches b ON b.id = bs.batch_id
WHERE bs.customer_id IS NOT NULL;

CREATE INDEX idx_egg_sales_customer ON egg_sales(customer_id);
CREATE INDEX idx_bird_sales_customer_id ON bird_sales(customer_id);

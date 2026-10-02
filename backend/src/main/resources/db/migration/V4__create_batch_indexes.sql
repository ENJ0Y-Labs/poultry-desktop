CREATE INDEX idx_batches_placement_date ON batches(placement_date);
CREATE INDEX idx_batches_status ON batches(status);
CREATE INDEX idx_batches_farm_type_status ON batches(farm_id, batch_type, status);

CREATE TABLE IF NOT EXISTS inventory (
    product_id VARCHAR(255) PRIMARY KEY,
    quantity INTEGER NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS processed_events (
    event_id VARCHAR(255) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Semilla de datos inicial (Seed)
INSERT INTO inventory (product_id, quantity, version) VALUES 
('notebook-gaming-01', 50, 0),
('mouse-wireless-02', 120, 0),
('keyboard-mech-03', 10, 0)
ON CONFLICT (product_id) DO NOTHING;
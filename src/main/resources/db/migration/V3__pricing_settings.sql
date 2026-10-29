-- Pricing settings with full history: rows are only inserted, never updated. The newest row is active.
-- Orders keep the id of the row they were priced with.
CREATE TABLE pricing_settings
(
    id                             UUID PRIMARY KEY,
    -- UZS for 1 CNY
    customer_rate                  NUMERIC(12, 2) NOT NULL CHECK (customer_rate > 0),
    -- percent of the goods price, 10.00 = 10%
    fee_percent                    NUMERIC(5, 2)  NOT NULL CHECK (fee_percent >= 0 AND fee_percent <= 100),
    min_service_fee                NUMERIC(14, 0) NOT NULL CHECK (min_service_fee >= 0),
    shipping_cost_per_kg           NUMERIC(14, 0) NOT NULL CHECK (shipping_cost_per_kg >= 0),
    our_markup_per_kg              NUMERIC(14, 0) NOT NULL CHECK (our_markup_per_kg >= 0),
    min_weight_kg                  NUMERIC(8, 3)  NOT NULL CHECK (min_weight_kg >= 0),
    -- used when the product weight is unknown
    default_weight_kg              NUMERIC(8, 3)  NOT NULL CHECK (default_weight_kg > 0),
    price_change_threshold_percent NUMERIC(5, 2)  NOT NULL CHECK (price_change_threshold_percent >= 0),
    effective_from                 TIMESTAMPTZ    NOT NULL,
    -- user id of the ADMIN; NULL for the seed row
    changed_by                     UUID REFERENCES users (id)
);

CREATE INDEX ix_pricing_settings_effective_from ON pricing_settings (effective_from DESC);

INSERT INTO pricing_settings (id, customer_rate, fee_percent, min_service_fee, shipping_cost_per_kg,
                              our_markup_per_kg, min_weight_kg, default_weight_kg,
                              price_change_threshold_percent, effective_from, changed_by)
VALUES (gen_random_uuid(), 1780, 10, 15000, 70000, 10000, 0.5, 0.5, 3, now(), NULL);

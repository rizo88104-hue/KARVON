-- Delivery time shown to customers ("12–18 days"). Part of the pricing settings, so it has the same history.
ALTER TABLE pricing_settings
    ADD COLUMN delivery_days_min INT NOT NULL DEFAULT 12 CHECK (delivery_days_min BETWEEN 1 AND 365),
    ADD COLUMN delivery_days_max INT NOT NULL DEFAULT 18 CHECK (delivery_days_max BETWEEN 1 AND 365),
    ADD CONSTRAINT ck_pricing_settings_delivery_days CHECK (delivery_days_max >= delivery_days_min);

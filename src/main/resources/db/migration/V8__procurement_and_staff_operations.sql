-- Buyer assignment, price differences found while purchasing, and shipping details.
ALTER TABLE orders
    ADD COLUMN buyer_id                  UUID REFERENCES users (id),
    -- accepted extra cost of items that became more expensive on Taobao; paid with purpose PRICE_DIFFERENCE
    ADD COLUMN price_difference_uzs      NUMERIC(14, 0) NOT NULL DEFAULT 0,
    ADD COLUMN price_difference_paid_uzs NUMERIC(14, 0) NOT NULL DEFAULT 0,
    ADD COLUMN carrier                   VARCHAR(100),
    ADD COLUMN expected_arrival_date     DATE,
    ADD COLUMN pickup_info               VARCHAR(500);

CREATE INDEX ix_orders_status_paid_at ON orders (status, paid_at);

-- Item states while purchasing: PENDING (not bought yet), PURCHASED, OUT_OF_STOCK, PRICE_CHANGED.
UPDATE order_items SET status = 'PENDING' WHERE status = 'OK';
ALTER TABLE order_items
    ADD COLUMN purchased_at  TIMESTAMPTZ,
    ADD COLUMN staff_comment VARCHAR(500);

-- Parcel photos taken at the warehouse (max 4 per order), shown to the customer.
CREATE TABLE order_photos
(
    id           UUID PRIMARY KEY,
    order_id     UUID         NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    storage_key  VARCHAR(300) NOT NULL,
    content_type VARCHAR(50)  NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    uploaded_by  UUID         NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_order_photos_order ON order_photos (order_id, created_at);

-- Maximum time an order may stay in a status, edited by ADMIN. Rows are only inserted; the newest is active.
CREATE TABLE sla_settings
(
    id             UUID PRIMARY KEY,
    -- JSON object: order status -> maximum hours, e.g. {"PAID": 48}
    limits         TEXT        NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL,
    changed_by     UUID REFERENCES users (id)
);

INSERT INTO sla_settings (id, limits, effective_from, changed_by)
VALUES (gen_random_uuid(), '{"PAID": 48, "PURCHASED": 240, "IN_TRANSIT": 600}', now(), NULL);

-- PRICE_DIFFERENCE_PAYMENT does not fit in 20 characters.
ALTER TABLE wallet_transactions ALTER COLUMN type TYPE VARCHAR(30);

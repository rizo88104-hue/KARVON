-- Cart lines. A product SKU is in a user's cart at most once; adding it again increases the quantity.
-- The *_snapshot columns keep what the customer saw when adding (or at the last price refresh).
CREATE TABLE cart_items
(
    id                      UUID PRIMARY KEY,
    user_id                 UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    external_product_id     VARCHAR(20)    NOT NULL,
    sku_id                  VARCHAR(40)    NOT NULL,
    title_snapshot          VARCHAR(500)   NOT NULL,
    image_snapshot          VARCHAR(1000),
    -- JSON array of {nameOriginal, valueOriginal, name, value}
    sku_properties_snapshot TEXT           NOT NULL,
    shop_name               VARCHAR(200),
    price_cny_snapshot      NUMERIC(12, 2) NOT NULL,
    china_shipping_cny      NUMERIC(12, 2) NOT NULL,
    weight_kg               NUMERIC(8, 3),
    quantity                INT            NOT NULL CHECK (quantity BETWEEN 1 AND 99),
    comment                 VARCHAR(300),
    selected                BOOLEAN        NOT NULL,
    -- set by the last price refresh: the price before it, when the price changed
    previous_price_cny      NUMERIC(12, 2),
    out_of_stock            BOOLEAN        NOT NULL DEFAULT FALSE,
    blocked                 BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ    NOT NULL,
    updated_at              TIMESTAMPTZ    NOT NULL,
    version                 BIGINT         NOT NULL,
    CONSTRAINT ux_cart_items_sku UNIQUE (user_id, external_product_id, sku_id)
);

CREATE INDEX ix_cart_items_user ON cart_items (user_id);

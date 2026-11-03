-- Favorites ("Sevimlilar"): products a customer saved for later, at most once each.
-- The summary columns keep the product as it was when saved, so the list needs no Taobao call; the UZS price is
-- calculated from price_cny with the current settings when the list is shown.
CREATE TABLE favorites
(
    id                  UUID PRIMARY KEY,
    user_id             UUID           NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    external_product_id VARCHAR(20)    NOT NULL,
    title_original      VARCHAR(500)   NOT NULL,
    image_url           VARCHAR(1000),
    price_cny           NUMERIC(12, 2) NOT NULL,
    original_price_cny  NUMERIC(12, 2),
    sales_count         BIGINT         NOT NULL,
    shop_name           VARCHAR(200),
    created_at          TIMESTAMPTZ    NOT NULL,
    updated_at          TIMESTAMPTZ    NOT NULL,
    version             BIGINT         NOT NULL,
    CONSTRAINT ux_favorites_product UNIQUE (user_id, external_product_id)
);

CREATE INDEX ix_favorites_user_created ON favorites (user_id, created_at DESC);

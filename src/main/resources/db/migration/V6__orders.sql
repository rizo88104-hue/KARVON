-- Orders made from the selected cart lines. Money in whole UZS (rounded to 100 so'm).
CREATE SEQUENCE order_number_seq START 1;

CREATE TABLE orders
(
    id                            UUID PRIMARY KEY,
    -- "KRV-" + 6 digits from order_number_seq
    number                        VARCHAR(20)    NOT NULL,
    customer_id                   UUID           NOT NULL REFERENCES users (id),
    status                        VARCHAR(30)    NOT NULL,
    delivery_method               VARCHAR(20)    NOT NULL,
    -- JSON copy of the delivery address at order time; NULL for cargo pickup without an address
    address_snapshot              TEXT,
    payment_method                VARCHAR(20)    NOT NULL,
    pricing_settings_id           UUID           NOT NULL REFERENCES pricing_settings (id),
    goods_uzs                     NUMERIC(14, 0) NOT NULL,
    service_fee_uzs               NUMERIC(14, 0) NOT NULL,
    prepaid_shipping_uzs          NUMERIC(14, 0) NOT NULL,
    -- known after weighing at the warehouse
    real_weight_kg                NUMERIC(8, 3),
    final_shipping_uzs            NUMERIC(14, 0),
    -- final - prepaid: > 0 the customer pays it, < 0 it goes back to the wallet
    shipping_difference_uzs       NUMERIC(14, 0) NOT NULL DEFAULT 0,
    total_uzs                     NUMERIC(14, 0) NOT NULL,
    paid_uzs                      NUMERIC(14, 0) NOT NULL DEFAULT 0,
    refunded_uzs                  NUMERIC(14, 0) NOT NULL DEFAULT 0,
    customer_comment              VARCHAR(500),
    international_tracking_number VARCHAR(100),
    paid_at                       TIMESTAMPTZ,
    delivered_at                  TIMESTAMPTZ,
    created_at                    TIMESTAMPTZ    NOT NULL,
    updated_at                    TIMESTAMPTZ    NOT NULL,
    version                       BIGINT         NOT NULL,
    CONSTRAINT ux_orders_number UNIQUE (number)
);

CREATE INDEX ix_orders_customer ON orders (customer_id, created_at DESC);
CREATE INDEX ix_orders_status ON orders (status, created_at);

CREATE TABLE order_items
(
    id                    UUID PRIMARY KEY,
    order_id              UUID           NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    -- copy of the cart line
    external_product_id   VARCHAR(20)    NOT NULL,
    sku_id                VARCHAR(40)    NOT NULL,
    title                 VARCHAR(500)   NOT NULL,
    image_url             VARCHAR(1000),
    sku_properties        TEXT           NOT NULL,
    shop_name             VARCHAR(200),
    price_cny             NUMERIC(12, 2) NOT NULL,
    china_shipping_cny    NUMERIC(12, 2) NOT NULL,
    weight_kg             NUMERIC(8, 3),
    quantity              INT            NOT NULL,
    comment               VARCHAR(300),
    -- its price breakdown
    goods_uzs             NUMERIC(14, 0) NOT NULL,
    service_fee_uzs       NUMERIC(14, 0) NOT NULL,
    shipping_uzs          NUMERIC(14, 0) NOT NULL,
    total_uzs             NUMERIC(14, 0) NOT NULL,
    chargeable_weight_kg  NUMERIC(8, 3)  NOT NULL,
    -- filled by staff
    actual_price_cny      NUMERIC(12, 2),
    taobao_order_number   VARCHAR(100),
    china_tracking_number VARCHAR(100),
    status                VARCHAR(20)    NOT NULL,
    created_at            TIMESTAMPTZ    NOT NULL,
    updated_at            TIMESTAMPTZ    NOT NULL,
    version               BIGINT         NOT NULL
);

CREATE INDEX ix_order_items_order ON order_items (order_id);

CREATE TABLE order_status_history
(
    id                 UUID PRIMARY KEY,
    order_id           UUID        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    -- NULL for the first entry (order created)
    from_status        VARCHAR(30),
    to_status          VARCHAR(30) NOT NULL,
    -- NULL when the system did it
    changed_by_user_id UUID,
    changed_by_role    VARCHAR(20) NOT NULL,
    comment            VARCHAR(500),
    created_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_order_status_history_order ON order_status_history (order_id, created_at);

-- Staff notes, never shown to the customer.
CREATE TABLE order_comments
(
    id         UUID PRIMARY KEY,
    order_id   UUID          NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    author_id  UUID          NOT NULL REFERENCES users (id),
    text       VARCHAR(2000) NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL
);

CREATE INDEX ix_order_comments_order ON order_comments (order_id, created_at);

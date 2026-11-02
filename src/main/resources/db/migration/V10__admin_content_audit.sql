-- Task 10: audit log of staff actions, site content (banners, pages), category editing and promo codes.

-- Every successful write call to /api/v1/admin/**. Rows are only inserted.
CREATE TABLE audit_events
(
    id          UUID PRIMARY KEY,
    actor_id    UUID         NOT NULL,
    -- the highest role of the actor at that moment: ADMIN, OPERATOR or BUYER
    actor_role  VARCHAR(16)  NOT NULL,
    -- e.g. ORDER_STATUS_CHANGE, BANNER_CREATE
    action      VARCHAR(60)  NOT NULL,
    -- e.g. ORDER, BANNER
    entity_type VARCHAR(40)  NOT NULL,
    entity_id   VARCHAR(64),
    -- JSON of the object before and after the change; NULL when there is none (create / delete)
    before_json TEXT,
    after_json  TEXT,
    ip          VARCHAR(64),
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_audit_events_created ON audit_events (created_at DESC);
CREATE INDEX ix_audit_events_entity ON audit_events (entity_type, entity_id, created_at DESC);
CREATE INDEX ix_audit_events_actor ON audit_events (actor_id, created_at DESC);

-- Home page banners. Shown when active and now is inside [active_from, active_to) (NULL = no limit).
CREATE TABLE banners
(
    id          UUID PRIMARY KEY,
    title       VARCHAR(150)  NOT NULL,
    subtitle    VARCHAR(300),
    image_url   VARCHAR(1000) NOT NULL,
    link_url    VARCHAR(1000),
    sort_order  INT           NOT NULL,
    active      BOOLEAN       NOT NULL,
    active_from TIMESTAMPTZ,
    active_to   TIMESTAMPTZ,
    created_at  TIMESTAMPTZ   NOT NULL,
    updated_at  TIMESTAMPTZ   NOT NULL,
    version     BIGINT        NOT NULL
);

CREATE INDEX ix_banners_active ON banners (active, sort_order);

-- Text pages (FAQ, delivery, returns, public offer). Body is Markdown, in Uzbek and Russian.
CREATE TABLE pages
(
    id         UUID PRIMARY KEY,
    slug       VARCHAR(60)  NOT NULL,
    title_uz   VARCHAR(200) NOT NULL,
    title_ru   VARCHAR(200) NOT NULL,
    body_uz    TEXT         NOT NULL,
    body_ru    TEXT         NOT NULL,
    published  BOOLEAN      NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL,
    CONSTRAINT ux_pages_slug UNIQUE (slug)
);

-- Categories are now edited by ADMIN; an optional icon (URL or icon name).
ALTER TABLE categories
    ADD COLUMN icon VARCHAR(300);

-- Promo codes lower the service fee: PERCENT_OF_FEE (value = percent) or FIXED_UZS (value = so'm).
CREATE TABLE promo_codes
(
    id             UUID PRIMARY KEY,
    -- stored in upper case
    code           VARCHAR(40)    NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    value          NUMERIC(14, 2) NOT NULL CHECK (value > 0),
    -- NULL = no limit
    max_uses       INT CHECK (max_uses > 0),
    per_user_limit INT CHECK (per_user_limit > 0),
    valid_from     TIMESTAMPTZ,
    valid_to       TIMESTAMPTZ,
    active         BOOLEAN        NOT NULL,
    used_count     INT            NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    version        BIGINT         NOT NULL,
    CONSTRAINT ux_promo_codes_code UNIQUE (code)
);

-- One row per order that used a code; removed when an unpaid order is cancelled.
CREATE TABLE promo_code_usages
(
    id            UUID PRIMARY KEY,
    promo_code_id UUID           NOT NULL REFERENCES promo_codes (id) ON DELETE CASCADE,
    user_id       UUID           NOT NULL,
    order_id      UUID           NOT NULL,
    discount_uzs  NUMERIC(14, 0) NOT NULL,
    created_at    TIMESTAMPTZ    NOT NULL,
    CONSTRAINT ux_promo_code_usages_order UNIQUE (order_id)
);

CREATE INDEX ix_promo_code_usages_code_user ON promo_code_usages (promo_code_id, user_id);

ALTER TABLE orders
    ADD COLUMN promo_code         VARCHAR(40),
    -- already taken off service_fee_uzs
    ADD COLUMN promo_discount_uzs NUMERIC(14, 0) NOT NULL DEFAULT 0;

-- Reports filter orders by creation time.
CREATE INDEX ix_orders_created_at ON orders (created_at);

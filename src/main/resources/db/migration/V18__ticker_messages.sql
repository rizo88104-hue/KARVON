-- Ticker: short messages that run along the site's top bar, in Uzbek and Russian, edited by ADMIN.
-- The first ones say what is true for every order; ADMIN changes or adds promotions in the admin panel.
CREATE TABLE ticker_messages
(
    id         UUID PRIMARY KEY,
    text_uz    VARCHAR(200)  NOT NULL,
    text_ru    VARCHAR(200)  NOT NULL,
    link_url   VARCHAR(1000),
    sort_order INT           NOT NULL,
    active     BOOLEAN       NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL,
    updated_at TIMESTAMPTZ   NOT NULL,
    version    BIGINT        NOT NULL
);

CREATE INDEX ix_ticker_messages_active ON ticker_messages (active, sort_order);

INSERT INTO ticker_messages (id, text_uz, text_ru, link_url, sort_order, active, created_at, updated_at, version)
VALUES (gen_random_uuid(), 'Taobao mahsulotlari — Xitoydan O''zbekistonga yetkazib berish',
        'Товары с Taobao — доставка из Китая в Узбекистан', NULL, 1, TRUE, now(), now(), 0),
       (gen_random_uuid(), 'Yakuniy narx so''mda, yashirin to''lovlarsiz',
        'Итоговая цена в сумах, без скрытых платежей', NULL, 2, TRUE, now(), now(), 0),
       (gen_random_uuid(), 'To''lov: Payme, Click yoki hamyon', 'Оплата: Payme, Click или кошелёк', NULL, 3, TRUE,
        now(), now(), 0),
       (gen_random_uuid(), 'Taobao havolasini qidiruvga joylang — narxini darhol hisoblaymiz',
        'Вставьте ссылку Taobao в поиск — сразу посчитаем цену', NULL, 4, TRUE, now(), now(), 0),
       (gen_random_uuid(), 'Savollar bo''lsa — Ko''p beriladigan savollar', 'Есть вопросы? Частые вопросы',
        '/pages/faq', 5, TRUE, now(), now(), 0);

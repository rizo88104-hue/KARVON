-- Translations done once and reused: SHA-256 (hex) of the source text + language pair -> translated text.
CREATE TABLE translation_cache
(
    source_hash VARCHAR(64) NOT NULL,
    from_lang   VARCHAR(2)  NOT NULL,
    to_lang     VARCHAR(2)  NOT NULL,
    text        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (source_hash, from_lang, to_lang)
);

-- Words that make a product impossible to bring in. Matched against the Chinese and the translated title:
-- ZH keywords as a substring (Chinese has no spaces), UZ/RU keywords as whole words, all lower case.
CREATE TABLE blocked_keywords
(
    id         UUID PRIMARY KEY,
    keyword    VARCHAR(100) NOT NULL,
    lang       VARCHAR(2)   NOT NULL,
    category   VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    updated_at TIMESTAMPTZ  NOT NULL,
    version    BIGINT       NOT NULL,
    CONSTRAINT ux_blocked_keywords UNIQUE (keyword, lang)
);

INSERT INTO blocked_keywords (id, keyword, lang, category, created_at, updated_at, version)
SELECT gen_random_uuid(), keyword, lang, category, now(), now(), 0
FROM (VALUES
          -- Liquids
          ('香水', 'ZH', 'LIQUID'), ('液体', 'ZH', 'LIQUID'), ('精油', 'ZH', 'LIQUID'), ('指甲油', 'ZH', 'LIQUID'),
          ('白酒', 'ZH', 'LIQUID'),
          ('atir', 'UZ', 'LIQUID'), ('atirlar', 'UZ', 'LIQUID'), ('suyuqlik', 'UZ', 'LIQUID'), ('parfyum', 'UZ', 'LIQUID'),
          ('духи', 'RU', 'LIQUID'), ('парфюм', 'RU', 'LIQUID'), ('парфюмерия', 'RU', 'LIQUID'), ('жидкость', 'RU', 'LIQUID'),
          -- Batteries over 100 Wh (about 27 000 mAh at 3.7 V) and power stations
          ('30000毫安', 'ZH', 'BATTERY'), ('40000毫安', 'ZH', 'BATTERY'), ('50000毫安', 'ZH', 'BATTERY'),
          ('30000mah', 'ZH', 'BATTERY'), ('40000mah', 'ZH', 'BATTERY'), ('50000mah', 'ZH', 'BATTERY'),
          ('户外电源', 'ZH', 'BATTERY'), ('储能电源', 'ZH', 'BATTERY'),
          ('50000 mah', 'UZ', 'BATTERY'), ('quvvat stansiyasi', 'UZ', 'BATTERY'),
          ('50000 мач', 'RU', 'BATTERY'), ('электростанция', 'RU', 'BATTERY'),
          -- Weapons
          ('刀具', 'ZH', 'WEAPON'), ('弹弓', 'ZH', 'WEAPON'), ('电击', 'ZH', 'WEAPON'), ('仿真枪', 'ZH', 'WEAPON'),
          ('弩', 'ZH', 'WEAPON'),
          ('qurol', 'UZ', 'WEAPON'), ('pichoq', 'UZ', 'WEAPON'), ('elektroshoker', 'UZ', 'WEAPON'),
          ('оружие', 'RU', 'WEAPON'), ('боевой нож', 'RU', 'WEAPON'), ('электрошокер', 'RU', 'WEAPON'), ('арбалет', 'RU', 'WEAPON'),
          -- Medicines
          ('药品', 'ZH', 'MEDICINE'), ('处方药', 'ZH', 'MEDICINE'), ('胶囊', 'ZH', 'MEDICINE'), ('保健品', 'ZH', 'MEDICINE'),
          ('dori', 'UZ', 'MEDICINE'), ('dorilar', 'UZ', 'MEDICINE'), ('tabletka', 'UZ', 'MEDICINE'),
          ('лекарство', 'RU', 'MEDICINE'), ('лекарства', 'RU', 'MEDICINE'), ('таблетки', 'RU', 'MEDICINE'),
          -- Replicas of brands
          ('高仿', 'ZH', 'REPLICA'), ('复刻', 'ZH', 'REPLICA'), ('原单', 'ZH', 'REPLICA'), ('a货', 'ZH', 'REPLICA'),
          ('replika', 'UZ', 'REPLICA'), ('replikasi', 'UZ', 'REPLICA'), ('brend nusxasi', 'UZ', 'REPLICA'),
          ('реплика', 'RU', 'REPLICA'), ('копия бренда', 'RU', 'REPLICA')
     ) AS seed (keyword, lang, category);

-- Our own category tree. Clicking a category searches Taobao with its Chinese keyword.
CREATE TABLE categories
(
    id                UUID PRIMARY KEY,
    parent_id         UUID REFERENCES categories (id) ON DELETE CASCADE,
    slug              VARCHAR(60)  NOT NULL,
    name_uz           VARCHAR(100) NOT NULL,
    name_ru           VARCHAR(100) NOT NULL,
    search_keyword_zh VARCHAR(100) NOT NULL,
    sort_order        INT          NOT NULL,
    CONSTRAINT ux_categories_slug UNIQUE (slug)
);

CREATE INDEX ix_categories_parent ON categories (parent_id);

INSERT INTO categories (id, parent_id, slug, name_uz, name_ru, search_keyword_zh, sort_order)
SELECT gen_random_uuid(), NULL, slug, name_uz, name_ru, keyword, sort_order
FROM (VALUES ('kiyim', 'Kiyim-kechak', 'Одежда', '服装', 1),
             ('poyabzal', 'Poyabzal', 'Обувь', '鞋', 2),
             ('sumkalar', 'Sumkalar', 'Сумки', '包', 3),
             ('elektronika', 'Elektronika', 'Электроника', '数码', 4),
             ('uy-rozgor', 'Uy-ro''zg''or', 'Дом', '家居', 5),
             ('bolalar', 'Bolalar uchun', 'Детям', '儿童', 6),
             ('aksessuarlar', 'Aksessuarlar', 'Аксессуары', '配饰', 7)
     ) AS top (slug, name_uz, name_ru, keyword, sort_order);

INSERT INTO categories (id, parent_id, slug, name_uz, name_ru, search_keyword_zh, sort_order)
SELECT gen_random_uuid(), parent.id, child.slug, child.name_uz, child.name_ru, child.keyword, child.sort_order
FROM (VALUES ('kiyim', 'erkaklar-kiyimi', 'Erkaklar kiyimi', 'Мужская одежда', '男士', 1),
             ('kiyim', 'ayollar-kiyimi', 'Ayollar kiyimi', 'Женская одежда', '女士', 2),
             ('kiyim', 'futbolkalar', 'Futbolkalar', 'Футболки', 'T恤', 3),
             ('kiyim', 'kurtkalar', 'Kurtkalar', 'Куртки', '外套', 4),
             ('kiyim', 'jinsilar', 'Jinsi shimlar', 'Джинсы', '牛仔裤', 5),
             ('poyabzal', 'krossovkalar', 'Krossovkalar', 'Кроссовки', '运动鞋', 1),
             ('poyabzal', 'tuflilar', 'Tuflilar', 'Туфли', '皮鞋', 2),
             ('poyabzal', 'etiklar', 'Etiklar', 'Сапоги и ботинки', '靴', 3),
             ('poyabzal', 'shippaklar', 'Shippaklar', 'Шлёпанцы', '拖鞋', 4),
             ('sumkalar', 'ayollar-sumkalari', 'Ayollar sumkalari', 'Женские сумки', '单肩包', 1),
             ('sumkalar', 'ryukzaklar', 'Ryukzaklar', 'Рюкзаки', '双肩包', 2),
             ('sumkalar', 'hamyonlar', 'Hamyonlar', 'Кошельки', '钱包', 3),
             ('sumkalar', 'chamadonlar', 'Chamadonlar', 'Чемоданы', '拉杆箱', 4),
             ('elektronika', 'quloqchinlar', 'Quloqchinlar', 'Наушники', '耳机', 1),
             ('elektronika', 'aqlli-soatlar', 'Aqlli soatlar', 'Умные часы', '智能手表', 2),
             ('elektronika', 'zaryadlovchilar', 'Zaryadlovchilar', 'Зарядные устройства', '充电', 3),
             ('elektronika', 'telefon-aksessuarlari', 'Telefon aksessuarlari', 'Аксессуары для телефона', '手机壳', 4),
             ('elektronika', 'kompyuter-aksessuarlari', 'Kompyuter aksessuarlari', 'Компьютерные аксессуары', '键盘', 5),
             ('uy-rozgor', 'oshxona', 'Oshxona', 'Кухня', '厨房', 1),
             ('uy-rozgor', 'choyshablar', 'Choyshablar', 'Постельное бельё', '四件套', 2),
             ('uy-rozgor', 'yoritish', 'Yoritish', 'Освещение', '灯', 3),
             ('uy-rozgor', 'saqlash', 'Saqlash', 'Хранение', '收纳', 4),
             ('bolalar', 'oyinchoqlar', 'O''yinchoqlar', 'Игрушки', '玩具', 1),
             ('bolalar', 'bolalar-kiyimi', 'Bolalar kiyimi', 'Детская одежда', '童装', 2),
             ('bolalar', 'chaqaloqlar', 'Chaqaloqlar uchun', 'Для малышей', '婴儿', 3),
             ('aksessuarlar', 'kamarlar', 'Kamarlar', 'Ремни', '腰带', 1),
             ('aksessuarlar', 'kozoynaklar', 'Ko''zoynaklar', 'Очки', '眼镜', 2),
             ('aksessuarlar', 'bosh-kiyimlar', 'Bosh kiyimlar', 'Головные уборы', '帽', 3),
             ('aksessuarlar', 'soatlar', 'Soatlar', 'Часы', '手表', 4),
             ('aksessuarlar', 'taqinchoqlar', 'Taqinchoqlar', 'Украшения', '项链', 5)
     ) AS child (parent_slug, slug, name_uz, name_ru, keyword, sort_order)
         JOIN categories parent ON parent.slug = child.parent_slug;

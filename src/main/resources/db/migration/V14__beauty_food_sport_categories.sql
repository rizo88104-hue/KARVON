-- Three more top categories (Go'zallik, Oziq-ovqat, Sport) and their children, after the seven of V4.
-- Each keyword is chosen so it is not part of another category's keyword (e.g. 露营, not 户外).
INSERT INTO categories (id, parent_id, slug, name_uz, name_ru, search_keyword_zh, sort_order)
SELECT gen_random_uuid(), NULL, slug, name_uz, name_ru, keyword, sort_order
FROM (VALUES ('gozallik', 'Go''zallik', 'Красота', '美妆', 8),
             ('oziq-ovqat', 'Oziq-ovqat', 'Продукты', '食品', 9),
             ('sport', 'Sport', 'Спорт', '运动户外', 10)
     ) AS top (slug, name_uz, name_ru, keyword, sort_order);

INSERT INTO categories (id, parent_id, slug, name_uz, name_ru, search_keyword_zh, sort_order)
SELECT gen_random_uuid(), parent.id, child.slug, child.name_uz, child.name_ru, child.keyword, child.sort_order
FROM (VALUES ('gozallik', 'pardoz', 'Pardoz', 'Декоративная косметика', '彩妆', 1),
             ('gozallik', 'teri-parvarishi', 'Teri parvarishi', 'Уход за кожей', '护肤', 2),
             ('gozallik', 'soch-parvarishi', 'Soch parvarishi', 'Уход за волосами', '美发', 3),
             ('gozallik', 'manikyur', 'Manikyur', 'Маникюр', '美甲', 4),
             ('oziq-ovqat', 'shirinliklar', 'Shirinliklar', 'Сладости', '糖果', 1),
             ('oziq-ovqat', 'choy', 'Choy', 'Чай', '茶叶', 2),
             ('oziq-ovqat', 'qahva', 'Qahva', 'Кофе', '咖啡', 3),
             ('oziq-ovqat', 'yongoqlar', 'Yong''oq va quruq mevalar', 'Орехи и сухофрукты', '坚果', 4),
             ('oziq-ovqat', 'gazaklar', 'Gazaklar', 'Снеки', '零食', 5),
             ('sport', 'fitnes', 'Fitnes', 'Фитнес', '健身', 1),
             ('sport', 'sport-kiyimi', 'Sport kiyimi', 'Спортивная одежда', '运动服', 2),
             ('sport', 'velosport', 'Velosport', 'Велоспорт', '骑行', 3),
             ('sport', 'turizm', 'Turizm', 'Туризм', '露营', 4)
     ) AS child (parent_slug, slug, name_uz, name_ru, keyword, sort_order)
         JOIN categories parent ON parent.slug = child.parent_slug;

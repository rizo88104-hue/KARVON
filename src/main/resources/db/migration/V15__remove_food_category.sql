-- Oziq-ovqat (added in V14) is not offered; its children are deleted with it (ON DELETE CASCADE).
DELETE FROM categories
WHERE slug = 'oziq-ovqat';

-- Indexes for staff searches (Task 13). Already there: orders(status, created_at), orders(customer_id, created_at),
-- the unique orders(number) (V6) and orders(created_at) (V10).

-- Look-ups by the international tracking number (bulk shipping, support questions).
CREATE INDEX ix_orders_international_tracking ON orders (international_tracking_number)
    WHERE international_tracking_number IS NOT NULL;

-- Buyer and support look-ups by Taobao order number and China tracking number.
CREATE INDEX ix_order_items_taobao_order ON order_items (taobao_order_number) WHERE taobao_order_number IS NOT NULL;
CREATE INDEX ix_order_items_china_tracking ON order_items (china_tracking_number) WHERE china_tracking_number IS NOT NULL;

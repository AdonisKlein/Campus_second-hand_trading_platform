ALTER TABLE trade_orders RENAME COLUMN reservation_expires_at TO expires_at;
ALTER TABLE trade_orders ADD COLUMN closure_reason VARCHAR(160);
UPDATE trade_orders SET status = 'PURCHASE_REQUESTED' WHERE status = 'PENDING_SELLER_CONFIRMATION';
DROP INDEX idx_orders_expiry ON trade_orders;
CREATE INDEX idx_orders_expiry ON trade_orders(status, expires_at);
CREATE INDEX idx_orders_item_status ON trade_orders(item_id, status, created_at);


CREATE INDEX idx_items_governance_lookup ON items (id, moderation_status, seller_id);
CREATE INDEX idx_messages_governance_lookup ON messages (id, sender_id, item_id);
CREATE INDEX idx_marketplace_outbox_pending ON marketplace_event_outbox (published_at, id);

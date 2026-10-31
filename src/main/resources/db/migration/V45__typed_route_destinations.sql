-- 1. CallQueue: Replace polymorphic overflow_target VARCHAR with typed foreign keys and phone
ALTER TABLE call_queue
    ADD COLUMN overflow_queue_id       BIGINT REFERENCES call_queue(id) ON DELETE SET NULL,
    ADD COLUMN overflow_agent_id       BIGINT REFERENCES ai_agent(id) ON DELETE SET NULL,
    ADD COLUMN overflow_voicemail_id   BIGINT REFERENCES voicemail_box(id) ON DELETE SET NULL,
    ADD COLUMN overflow_external_phone VARCHAR(32);

-- Backfill call_queue overflow targets
UPDATE call_queue
SET overflow_queue_id = overflow_target::BIGINT
WHERE overflow_action = 'QUEUE'
  AND overflow_target ~ '^\d+$'
  AND EXISTS (SELECT 1 FROM call_queue cq WHERE cq.id = call_queue.overflow_target::BIGINT);

UPDATE call_queue
SET overflow_agent_id = overflow_target::BIGINT
WHERE overflow_action = 'AI_AGENT'
  AND overflow_target ~ '^\d+$'
  AND EXISTS (SELECT 1 FROM ai_agent a WHERE a.id = call_queue.overflow_target::BIGINT);

UPDATE call_queue
SET overflow_voicemail_id = overflow_target::BIGINT
WHERE overflow_action = 'VOICEMAIL'
  AND overflow_target ~ '^\d+$'
  AND EXISTS (SELECT 1 FROM voicemail_box vb WHERE vb.id = call_queue.overflow_target::BIGINT);

UPDATE call_queue
SET overflow_external_phone = trim(overflow_target)
WHERE overflow_action = 'EXTERNAL_NUMBER'
  AND overflow_target IS NOT NULL AND trim(overflow_target) <> '';

CREATE INDEX idx_call_queue_overflow_queue ON call_queue(overflow_queue_id);
CREATE INDEX idx_call_queue_overflow_agent ON call_queue(overflow_agent_id);
CREATE INDEX idx_call_queue_overflow_voicemail ON call_queue(overflow_voicemail_id);

ALTER TABLE call_queue DROP COLUMN IF EXISTS overflow_target;

-- 2. InboundRoute: Replace polymorphic after_hours_destination VARCHAR with typed foreign keys and phone
ALTER TABLE inbound_route
    ADD COLUMN after_hours_agent_id      BIGINT REFERENCES ai_agent(id) ON DELETE SET NULL,
    ADD COLUMN after_hours_voicemail_id  BIGINT REFERENCES voicemail_box(id) ON DELETE SET NULL,
    ADD COLUMN after_hours_forward_phone VARCHAR(32);

-- Backfill inbound_route after hours destinations
UPDATE inbound_route
SET after_hours_agent_id = after_hours_destination::BIGINT
WHERE after_hours_action = 'AI_AGENT'
  AND after_hours_destination ~ '^\d+$'
  AND EXISTS (SELECT 1 FROM ai_agent a WHERE a.id = inbound_route.after_hours_destination::BIGINT);

UPDATE inbound_route
SET after_hours_voicemail_id = after_hours_destination::BIGINT
WHERE after_hours_action = 'VOICEMAIL'
  AND after_hours_destination ~ '^\d+$'
  AND EXISTS (SELECT 1 FROM voicemail_box vb WHERE vb.id = inbound_route.after_hours_destination::BIGINT);

UPDATE inbound_route
SET after_hours_forward_phone = trim(after_hours_destination)
WHERE after_hours_action = 'FORWARD_EXTERNAL'
  AND after_hours_destination IS NOT NULL AND trim(after_hours_destination) <> '';

CREATE INDEX idx_inbound_route_after_hours_agent ON inbound_route(after_hours_agent_id);
CREATE INDEX idx_inbound_route_after_hours_voicemail ON inbound_route(after_hours_voicemail_id);

ALTER TABLE inbound_route DROP COLUMN IF EXISTS after_hours_destination;

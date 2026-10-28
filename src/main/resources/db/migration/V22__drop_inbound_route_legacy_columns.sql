-- The pre-call-flow routing settings of inbound_route. Nothing on the call path has read them
-- since queues, IVR menus and forwards became call flow nodes, so a route keeps behaving
-- exactly as it does today. after_hours_action / after_hours_destination stay: a route without
-- a call flow still uses them.
ALTER TABLE inbound_route
    DROP COLUMN IF EXISTS route_type,
    DROP COLUMN IF EXISTS target_destination,
    DROP COLUMN IF EXISTS queue_strategy,
    DROP COLUMN IF EXISTS ring_timeout_sec,
    DROP COLUMN IF EXISTS failover_action,
    DROP COLUMN IF EXISTS failover_destination,
    DROP COLUMN IF EXISTS ivr_menu_config;

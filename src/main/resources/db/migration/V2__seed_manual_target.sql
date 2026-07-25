-- A placeholder campaign + target so manually/auto-started calls (Stages 7–9) have a
-- call_attempt parent before the dialer/campaign lifecycle exists (Stage 10). The
-- target is looked up by phone = 'MANUAL'. Uses serials (no explicit ids), so Stage 10
-- inserts are unaffected.

INSERT INTO campaign (name, type, status, goal_prompt, script_config)
VALUES ('MANUAL', 'DEBT_COLLECTION', 'ACTIVE',
        'Qo''lda/avtomatik test qo''ng''iroqlari (Bosqich 9)', '{}');

INSERT INTO campaign_target (campaign_id, client_id, phone, context_data, status)
VALUES ((SELECT id FROM campaign WHERE name = 'MANUAL' ORDER BY id LIMIT 1),
        0, 'MANUAL', '{}', 'IN_PROGRESS');

-- Extract comma-separated skills from call_queue_member into relational call_queue_member_skill table
CREATE TABLE call_queue_member_skill (
    queue_id BIGINT      NOT NULL,
    user_id  BIGINT      NOT NULL,
    skill    VARCHAR(64) NOT NULL,
    PRIMARY KEY (queue_id, user_id, skill),
    CONSTRAINT fk_cqm_skill FOREIGN KEY (queue_id, user_id)
        REFERENCES call_queue_member(queue_id, user_id) ON DELETE CASCADE
);

CREATE INDEX idx_cqm_skill ON call_queue_member_skill (skill);

INSERT INTO call_queue_member_skill (queue_id, user_id, skill)
SELECT queue_id, user_id, trim(s.val)
FROM call_queue_member cqm
CROSS JOIN LATERAL unnest(string_to_array(cqm.skills, ',')) AS s(val)
WHERE cqm.skills IS NOT NULL AND trim(s.val) <> ''
ON CONFLICT (queue_id, user_id, skill) DO NOTHING;

ALTER TABLE call_queue_member DROP COLUMN skills;

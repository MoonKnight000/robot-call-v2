-- 1. Create contact_tag relational table (1NF normalization of contact.tags)
CREATE TABLE contact_tag (
    contact_id BIGINT      NOT NULL REFERENCES contact(id) ON DELETE CASCADE,
    tag        VARCHAR(64) NOT NULL,
    PRIMARY KEY (contact_id, tag)
);

CREATE INDEX idx_contact_tag_tag ON contact_tag(tag);

-- 2. Migrate existing comma-separated tags to relational rows
INSERT INTO contact_tag (contact_id, tag)
SELECT c.id, trim(t.val)
FROM contact c,
     LATERAL unnest(string_to_array(c.tags, ',')) AS t(val)
WHERE c.tags IS NOT NULL AND trim(t.val) <> ''
ON CONFLICT (contact_id, tag) DO NOTHING;

-- 3. Drop unnormalized tags column from contact
ALTER TABLE contact DROP COLUMN IF EXISTS tags;

-- Machine access: a company's own systems calling this API without a person logging in.
--
-- Until now the only credential was a per-user JWT, which meant an integration either ran
-- as somebody's account — inheriting whatever that person may do, and dying when they
-- leave — or did not run at all.
CREATE TABLE api_key (
    id            BIGSERIAL PRIMARY KEY,
    company_id    BIGINT       NOT NULL REFERENCES company(id) ON DELETE CASCADE,
    name          VARCHAR(128) NOT NULL,
    -- The readable half of the key, shown in the console so a key can be recognised
    -- after it has been issued, and used to find the row before the hash is compared.
    key_prefix    VARCHAR(32)  NOT NULL UNIQUE,
    -- SHA-256 of the whole key. The key itself is shown once, at creation, and never
    -- stored: a leaked database must not hand anyone a working credential.
    key_hash      VARCHAR(64)  NOT NULL,
    created_by    BIGINT       REFERENCES app_user(id) ON DELETE SET NULL,
    last_used_at  TIMESTAMPTZ,
    expires_at    TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_key_company ON api_key(company_id, created_at DESC);

-- Permission names this key was granted. What it may actually do is this list intersected
-- at authentication time with the hardcoded allowlist in ApiKeyScopes, so a key cannot
-- gain a right by having its rows edited.
CREATE TABLE api_key_scope (
    api_key_id BIGINT      NOT NULL REFERENCES api_key(id) ON DELETE CASCADE,
    permission VARCHAR(64) NOT NULL,
    PRIMARY KEY (api_key_id, permission)
);

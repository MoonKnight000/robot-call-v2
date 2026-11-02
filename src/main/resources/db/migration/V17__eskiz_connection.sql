-- Each company connects its own Eskiz account (login + password) to send SMS from it. The login and the
-- password are stored encrypted; a company with no row falls back to the platform's own Eskiz login, if any.
CREATE TABLE eskiz_connection (
    company_id    BIGINT       PRIMARY KEY REFERENCES company(id) ON DELETE CASCADE,
    email_enc     TEXT         NOT NULL,
    password_enc  TEXT         NOT NULL,
    sender        VARCHAR(32),
    connected_at  TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

-- The connector now exists in code.
UPDATE integration_provider SET available = true WHERE provider_key = 'ESKIZ';

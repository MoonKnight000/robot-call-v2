-- Where a campaign's call list comes from, when it does not come from a CSV somebody
-- uploads by hand. A recurring campaign is the case that needs it: DAILY recurrence
-- already re-runs the campaign every morning, but until now it re-ran it over the same
-- list, so "call today's overdue clients every day" meant a person exporting a file and
-- uploading it every day. With a source configured, the recurrence sweep fetches the list
-- from the company's own API first and dials whatever it answers with.
--
-- One source per campaign, so the shape stays a settings page and not a pipeline builder.

CREATE TABLE campaign_target_source (
    campaign_id        BIGINT PRIMARY KEY REFERENCES campaign(id) ON DELETE CASCADE,
    url                VARCHAR(1000) NOT NULL,
    http_method        VARCHAR(10)   NOT NULL DEFAULT 'GET',   -- GET, POST
    request_body       TEXT,                                   -- POST only, sent as-is
    auth_header_name   VARCHAR(100),
    auth_header_value  TEXT,                                   -- AES-GCM (SecretCipher)
    items_path         VARCHAR(200),                           -- dot path to the array; NULL = the body is the array
    phone_field        VARCHAR(100)  NOT NULL DEFAULT 'phone',
    client_id_field    VARCHAR(100),
    language_field     VARCHAR(100),
    replace_targets    BOOLEAN       NOT NULL DEFAULT false,   -- clear the list before importing
    sync_on_recurrence BOOLEAN       NOT NULL DEFAULT true,
    enabled            BOOLEAN       NOT NULL DEFAULT true,
    last_sync_at       TIMESTAMPTZ,
    last_sync_added    INT,
    last_sync_error    VARCHAR(1000),
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);

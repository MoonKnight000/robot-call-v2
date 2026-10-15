-- Partner (reseller / white-label) program. A company with a partner_branding row is a partner:
-- it signs up its own clients and they see the partner's brand instead of ours.
CREATE TABLE partner_branding (
    company_id    BIGINT       PRIMARY KEY REFERENCES company(id),
    product_name  VARCHAR(100) NOT NULL,
    -- The partner's own console host (app.partner.uz); the login page asks for the brand by it.
    domain        VARCHAR(255) UNIQUE,
    logo_url      VARCHAR(500),
    primary_color VARCHAR(7),
    support_email VARCHAR(255),
    support_phone VARCHAR(32),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Which partner brought a client in. One partner per client; a company is never its own client.
CREATE TABLE partner_client (
    client_company_id  BIGINT      PRIMARY KEY REFERENCES company(id),
    partner_company_id BIGINT      NOT NULL REFERENCES partner_branding(company_id),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_partner_client_not_self CHECK (client_company_id <> partner_company_id)
);

CREATE INDEX idx_partner_client_partner ON partner_client (partner_company_id, created_at DESC);

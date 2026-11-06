-- Platform-wide default roles. A new company gets a copy of every template as its own roles
-- (docs/api/platform-role-templates.md). Rows are seeded once by RoleBootstrap from SystemRole,
-- because those permission sets are defined in code, not here.
CREATE TABLE role_template (
    id                    BIGSERIAL PRIMARY KEY,
    code                  VARCHAR(50),            -- DEVELOPER, ADMIN, …; NULL for a template the platform added
    name                  VARCHAR(120) NOT NULL,
    description           VARCHAR(500),
    assignable_by_company BOOLEAN      NOT NULL DEFAULT true,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- NULLs are distinct in Postgres, so this constrains seeded codes only.
CREATE UNIQUE INDEX idx_role_template_code ON role_template(code);
CREATE UNIQUE INDEX idx_role_template_name ON role_template(lower(name));

CREATE TABLE role_template_permission (
    template_id BIGINT      NOT NULL REFERENCES role_template(id) ON DELETE CASCADE,
    permission  VARCHAR(60) NOT NULL,
    PRIMARY KEY (template_id, permission)
);

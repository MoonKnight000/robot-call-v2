-- Roles become data (CLAUDE.md permission management). Instead of one of four hard-coded
-- tiers on app_user, every panel page has its own READ/EDIT permission (Permission.java)
-- and a company composes them into roles: four seeded system roles plus up to ten of its
-- own. A system role stores no permission rows at all — they are computed from its code in
-- SystemRole.java, so a permission added in a later release reaches DEVELOPER and ADMIN
-- without a data migration and can never drift from the code.

CREATE TABLE app_role (
    id          BIGSERIAL PRIMARY KEY,
    company_id  BIGINT       NOT NULL REFERENCES company(id),
    code        VARCHAR(50),             -- DEVELOPER, ADMIN, OPERATOR, VIEWER, SUPERADMIN; NULL for a company's own role
    name        VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    is_system   BOOLEAN      NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- NULLs are distinct in Postgres, so this constrains system role codes only.
CREATE UNIQUE INDEX idx_app_role_company_code ON app_role(company_id, code);
CREATE UNIQUE INDEX idx_app_role_company_name ON app_role(company_id, lower(name));
CREATE INDEX idx_app_role_company ON app_role(company_id);

CREATE TABLE app_role_permission (
    role_id    BIGINT      NOT NULL REFERENCES app_role(id) ON DELETE CASCADE,
    permission VARCHAR(60) NOT NULL,
    PRIMARY KEY (role_id, permission)
);

INSERT INTO app_role (company_id, code, name, is_system)
SELECT c.id, r.code, r.name, true
FROM company c
         CROSS JOIN (VALUES ('DEVELOPER', 'Developer'),
                            ('ADMIN', 'Administrator'),
                            ('OPERATOR', 'Operator'),
                            ('VIEWER', 'Viewer')) AS r(code, name);

-- SUPERADMIN reaches across tenants, so it exists only where such a user already lives.
INSERT INTO app_role (company_id, code, name, is_system)
SELECT DISTINCT u.company_id, 'SUPERADMIN', 'Superadmin', true
FROM app_user u
WHERE u.role = 'SUPERADMIN';

ALTER TABLE app_user ADD COLUMN role_id BIGINT REFERENCES app_role(id);

UPDATE app_user u
SET role_id = r.id
FROM app_role r
WHERE r.company_id = u.company_id
  AND r.code = u.role;

ALTER TABLE app_user ALTER COLUMN role_id SET NOT NULL;
ALTER TABLE app_user DROP COLUMN role;
CREATE INDEX idx_app_user_role ON app_user(role_id);

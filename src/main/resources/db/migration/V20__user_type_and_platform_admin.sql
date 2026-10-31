-- V20: Separate Platform Admin and Company User in app_user.
-- Platform admin is a system-wide identity without company_id and role_id.
-- Company users belong to a specific company and hold a role in that company.

ALTER TABLE app_user ADD COLUMN type VARCHAR(30) NOT NULL DEFAULT 'COMPANY_USER';

ALTER TABLE app_user ALTER COLUMN company_id DROP NOT NULL;
ALTER TABLE app_user ALTER COLUMN role_id DROP NOT NULL;

ALTER TABLE user_session ALTER COLUMN company_id DROP NOT NULL;
ALTER TABLE user_totp ALTER COLUMN company_id DROP NOT NULL;

-- Migrate existing platform administrator (platform staff, not tenant users)
UPDATE app_user
SET type = 'PLATFORM_ADMIN',
    company_id = NULL,
    role_id = NULL
WHERE username = 'platform';

-- If any company user (e.g. 'superadmin') held the old SUPERADMIN role, reassign them to their company's ADMIN role
UPDATE app_user
SET role_id = (SELECT id FROM app_role WHERE company_id = app_user.company_id AND code = 'ADMIN')
WHERE role_id IN (SELECT id FROM app_role WHERE code = 'SUPERADMIN');

-- Remove SUPERADMIN system roles
DELETE FROM app_role_permission WHERE role_id IN (SELECT id FROM app_role WHERE code = 'SUPERADMIN');
DELETE FROM app_role WHERE code = 'SUPERADMIN';

-- Enforce strict separation between PLATFORM_ADMIN and COMPANY_USER
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_type_company
    CHECK ((type = 'PLATFORM_ADMIN' AND company_id IS NULL AND role_id IS NULL)
        OR (type = 'COMPANY_USER' AND company_id IS NOT NULL AND role_id IS NOT NULL));

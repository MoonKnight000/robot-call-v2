-- Every company role becomes editable (docs/api/roles.md). What used to be decided by the code of a
-- system role — "only platform staff may hand this out" — is now a column copied from the template.
ALTER TABLE app_role ADD COLUMN assignable_by_company BOOLEAN NOT NULL DEFAULT true;
UPDATE app_role SET assignable_by_company = false WHERE code = 'DEVELOPER';

-- is_system stays for one release: RoleBootstrap still finds the rows whose permissions were computed
-- in code, writes them into app_role_permission and clears the flag. After that it is always false.

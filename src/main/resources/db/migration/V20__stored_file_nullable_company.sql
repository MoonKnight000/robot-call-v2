-- Stored files can belong to the platform (e.g. integration catalog logos, platform assets)
-- where there is no owning company (company_id is null).
ALTER TABLE stored_file
    ALTER COLUMN company_id DROP NOT NULL;

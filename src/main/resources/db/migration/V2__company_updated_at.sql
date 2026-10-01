-- V2__company_updated_at.sql
--
-- The Company aggregate tracks `updatedAt`, but V1's company table omitted the
-- column (an inconsistency carried over from domain-model.md § SQLite Schema).
-- JdbcCompanyRepository writes updated_at, so the column must exist.
--
-- Additive, per the migration strategy: add the column with a safe default so
-- any pre-existing rows remain valid, then backfill existing rows to their
-- created_at (a brand-new install has no rows, making the backfill a no-op).

ALTER TABLE company ADD COLUMN updated_at TEXT NOT NULL DEFAULT '1970-01-01T00:00:00Z';

UPDATE company SET updated_at = created_at WHERE updated_at = '1970-01-01T00:00:00Z';

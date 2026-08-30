-- Migration: add api_type column to eval_llm_configs
-- If using ddl-auto: update, JPA will add the column automatically.
-- Run this manually if the column is not added, or to set defaults for existing rows.

ALTER TABLE eval_llm_configs ADD COLUMN IF NOT EXISTS api_type VARCHAR(20) NOT NULL DEFAULT 'chat';

-- Update existing rows to have the default value
UPDATE eval_llm_configs SET api_type = 'chat' WHERE api_type IS NULL OR api_type = '';
-- Initialize database if needed
-- This script runs on first container startup

-- Ensure the database is created (already done by POSTGRES_DB env var)
-- Create extensions if needed

-- You can add any initial setup here
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Log initialization
DO $$
BEGIN
    RAISE NOTICE 'Auth database initialized successfully';
END $$;

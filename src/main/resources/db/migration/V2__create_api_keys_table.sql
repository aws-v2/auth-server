CREATE TABLE api_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Credentials
    access_key_id VARCHAR(20) NOT NULL UNIQUE,
    secret_key_hash VARCHAR(255) NOT NULL,

    -- Metadata
    name VARCHAR(100) NOT NULL,
    description TEXT,

    -- Scoping
    allowed_actions TEXT[],
    allowed_resources TEXT[],

    -- Status
    enabled BOOLEAN DEFAULT TRUE,
    last_used_at TIMESTAMP,

    -- Lifecycle
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,

    CONSTRAINT check_access_key_format CHECK (access_key_id ~ '^AKIA[A-Z0-9]{16}$')
);

CREATE INDEX idx_api_keys_user_id ON api_keys(user_id);
CREATE INDEX idx_api_keys_access_key_id ON api_keys(access_key_id);
CREATE INDEX idx_api_keys_enabled ON api_keys(enabled);

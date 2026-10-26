-- Preserve existing accounts and sessions while making username the required login identifier.
-- Existing email addresses remain unchanged; generated usernames are deterministic and unique.
CREATE TABLE user_sessions_migration_backup AS
SELECT id, user_id, token_hash, created_at, expires_at, last_seen_at
FROM user_sessions;

DROP TABLE user_sessions;

CREATE TABLE users_new (
    id TEXT PRIMARY KEY,
    username TEXT NOT NULL UNIQUE COLLATE NOCASE CHECK (
        length(username) BETWEEN 3 AND 32
        AND username = lower(username)
        AND username NOT GLOB '*[^a-z0-9._-]*'
    ),
    email TEXT UNIQUE COLLATE NOCASE CHECK (
        email IS NULL OR length(trim(email)) > 0
    ),
    password_hash TEXT NOT NULL CHECK (length(password_hash) > 0),
    full_name TEXT NOT NULL CHECK (length(trim(full_name)) > 0),
    role TEXT NOT NULL DEFAULT 'OWNER' CHECK (role = 'OWNER'),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

INSERT INTO users_new
    (id, username, email, password_hash, full_name, role, status, created_at, updated_at)
SELECT
    id,
    'owner-' || row_number() OVER (ORDER BY id),
    email,
    password_hash,
    full_name,
    role,
    status,
    created_at,
    updated_at
FROM users;

DROP TABLE users;
ALTER TABLE users_new RENAME TO users;

CREATE TABLE user_sessions (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    token_hash TEXT NOT NULL UNIQUE,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    last_seen_at TEXT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

INSERT INTO user_sessions (id, user_id, token_hash, created_at, expires_at, last_seen_at)
SELECT id, user_id, token_hash, created_at, expires_at, last_seen_at
FROM user_sessions_migration_backup;

DROP TABLE user_sessions_migration_backup;

CREATE INDEX idx_user_sessions_user_id ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_expires_at ON user_sessions(expires_at);

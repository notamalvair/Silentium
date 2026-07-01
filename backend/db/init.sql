-- Database schema for Personal E2E Messenger

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast user search
CREATE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- 2. User Devices & Public Keys Table
CREATE TABLE IF NOT EXISTS devices (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    public_key VARCHAR(128) NOT NULL, -- X25519 Public Key in Hex format (64 chars)
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index for fast device public key lookup
CREATE INDEX IF NOT EXISTS idx_devices_user_id ON devices(user_id);

-- 3. Chats Table (1:1 or Group)
CREATE TABLE IF NOT EXISTS chats (
    id SERIAL PRIMARY KEY,
    type VARCHAR(12) NOT NULL CHECK (type IN ('direct', 'group')),
    name VARCHAR(100), -- Null for direct chats
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Chat Members Table
-- Maps users to chats. For group chats, stores the group symmetric key encrypted for this user.
CREATE TABLE IF NOT EXISTS chat_members (
    chat_id INTEGER REFERENCES chats(id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    -- Group key encrypted using the shared secret of (sender identity X25519 key, receiver identity X25519 key)
    -- For group creator, it is encrypted for themselves or stored directly if needed, but standard is encrypting for all.
    encrypted_group_key TEXT, -- AES-256-GCM encrypted group key (Hex)
    group_key_nonce VARCHAR(24), -- 12-byte IV (Hex)
    joined_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (chat_id, user_id)
);

-- 5. Messages Table
-- Stores ciphertexts. The server only sees ciphertext and metadata.
CREATE TABLE IF NOT EXISTS messages (
    id SERIAL PRIMARY KEY,
    chat_id INTEGER REFERENCES chats(id) ON DELETE CASCADE,
    sender_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    ciphertext TEXT NOT NULL, -- AES-256-GCM ciphertext (Hex or Base64)
    nonce VARCHAR(24) NOT NULL, -- 12-byte IV (Hex)
    file_url TEXT, -- Path to encrypted attachment on server, if any
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_messages_chat_id ON messages(chat_id);
CREATE INDEX IF NOT EXISTS idx_messages_created_at ON messages(created_at);

-- 6. Message Statuses (Sent, Delivered, Read) for offline delivery and statuses
CREATE TABLE IF NOT EXISTS message_statuses (
    message_id INTEGER REFERENCES messages(id) ON DELETE CASCADE,
    user_id INTEGER REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(12) NOT NULL CHECK (status IN ('sent', 'delivered', 'read')),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (message_id, user_id)
);

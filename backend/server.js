/**
 * Personal E2E Messenger - Node.js Backend Server (Express + Socket.io)
 * Provides authentication, E2E key distribution, REST API, and websocket relay.
 */

const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcryptjs');
const cors = require('cors');
const multer = require('multer');
const path = require('path');
const fs = require('fs');
const { Pool } = require('pg');
const Redis = require('ioredis');

const app = express();
const server = http.createServer(app);
const io = new Server(server, {
  cors: {
    origin: '*',
    methods: ['GET', 'POST']
  }
});

const PORT = process.env.PORT || 3000;
const JWT_SECRET = process.env.JWT_SECRET || 'super_secret_personal_messenger_key_2026';

// ----------------------------------------------------
// Infrastructure Connections (with fallback)
// ----------------------------------------------------

// PostgreSQL Connection Pool
const dbConfig = {
  connectionString: process.env.DATABASE_URL || 'postgresql://messenger_user:messenger_pass@localhost:5432/messenger'
};
const pool = new Pool(dbConfig);

// Redis Connection for caching/presence
let redis;
if (process.env.REDIS_URL) {
  redis = new Redis(process.env.REDIS_URL);
} else {
  // Mock Redis fallback for easy standalone testing
  const mockStore = {};
  redis = {
    set: async (key, val, ex, sec) => { mockStore[key] = val; return 'OK'; },
    get: async (key) => mockStore[key] || null,
    del: async (key) => { delete mockStore[key]; return 1; },
    keys: async (pattern) => Object.keys(mockStore).filter(k => k.startsWith(pattern.replace('*', '')))
  };
}

// Ensure attachments directory exists
const uploadDir = path.join(__dirname, 'uploads');
if (!fs.existsSync(uploadDir)) {
  fs.mkdirSync(uploadDir);
}

// Multer storage for encrypted blob attachments
const storage = multer.diskStorage({
  destination: (req, file, cb) => cb(null, uploadDir),
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, 'blob-' + uniqueSuffix + path.extname(file.originalname));
  }
});
const upload = multer({ storage });

// Express Middlewares
app.use(cors());
app.use(express.json());
app.use('/uploads', express.static(uploadDir));

// Helper for JWT authentication on routes
const authenticateToken = (req, res, next) => {
  const authHeader = req.headers['authorization'];
  const token = authHeader && authHeader.split(' ')[1];
  if (!token) return res.status(401).json({ error: 'Token missing' });

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) return res.status(403).json({ error: 'Token invalid/expired' });
    req.user = user;
    next();
  });
};

// ----------------------------------------------------
// REST API Endpoints
// ----------------------------------------------------

// 1. Auth: Register
app.post('/api/auth/register', async (req, res) => {
  const { username, password, publicKey } = req.body;
  if (!username || !password || !publicKey) {
    return res.status(400).json({ error: 'Username, password and X25519 public key are required' });
  }

  try {
    const userCheck = await pool.query('SELECT * FROM users WHERE username = $1', [username]);
    if (userCheck.rows.length > 0) {
      return res.status(400).json({ error: 'Username already taken' });
    }

    const salt = await bcrypt.genSalt(10);
    const passwordHash = await bcrypt.hash(password, salt);

    // Insert user and their device public key in a transaction
    const client = await pool.connect();
    try {
      await client.query('BEGIN');
      const userRes = await client.query(
        'INSERT INTO users (username, password_hash) VALUES ($1, $2) RETURNING id, username',
        [username, passwordHash]
      );
      const userId = userRes.rows[0].id;

      await client.query(
        'INSERT INTO devices (user_id, public_key) VALUES ($1, $2)',
        [userId, publicKey]
      );

      await client.query('COMMIT');

      const token = jwt.sign({ id: userId, username }, JWT_SECRET, { expiresIn: '30d' });
      res.status(201).json({ token, user: { id: userId, username, publicKey } });
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  } catch (error) {
    console.error('Registration error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// 2. Auth: Login
app.post('/api/auth/login', async (req, res) => {
  const { username, password, publicKey } = req.body;
  if (!username || !password) {
    return res.status(400).json({ error: 'Username and password are required' });
  }

  try {
    const userRes = await pool.query('SELECT * FROM users WHERE username = $1', [username]);
    if (userRes.rows.length === 0) {
      return res.status(400).json({ error: 'Invalid username or password' });
    }

    const user = userRes.rows[0];
    const isMatch = await bcrypt.compare(password, user.password_hash);
    if (!isMatch) {
      return res.status(400).json({ error: 'Invalid username or password' });
    }

    // Optional: update public key on login if they changed device
    if (publicKey) {
      await pool.query(
        'INSERT INTO devices (user_id, public_key) VALUES ($1, $2) ON CONFLICT (id) DO UPDATE SET public_key = EXCLUDED.public_key',
        [user.id, publicKey]
      );
    }

    // Fetch active device public key
    const devRes = await pool.query('SELECT public_key FROM devices WHERE user_id = $1 ORDER BY id DESC LIMIT 1', [user.id]);
    const activePublicKey = devRes.rows[0] ? devRes.rows[0].public_key : '';

    const token = jwt.sign({ id: user.id, username: user.username }, JWT_SECRET, { expiresIn: '30d' });
    res.json({ token, user: { id: user.id, username: user.username, publicKey: activePublicKey } });
  } catch (error) {
    console.error('Login error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// 3. Contacts: Search users and get their public keys
app.get('/api/users/search', authenticateToken, async (req, res) => {
  const { query } = req.query;
  if (!query) return res.json([]);

  try {
    // Search users and return username, id, and public key
    const result = await pool.query(
      `SELECT u.id, u.username, d.public_key 
       FROM users u
       JOIN devices d ON u.id = d.user_id
       WHERE u.username ILIKE $1 AND u.id != $2
       LIMIT 10`,
      [`%${query}%`, req.user.id]
    );
    res.json(result.rows);
  } catch (error) {
    console.error('User search error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// 4. Chats: Create a Direct or Group Chat
app.post('/api/chats', authenticateToken, async (req, res) => {
  const { type, name, members } = req.body; // type: 'direct' | 'group', members: Array of { userId, encryptedGroupKey, groupKeyNonce }
  if (!type || !members || !Array.isArray(members) || members.length === 0) {
    return res.status(400).json({ error: 'Type and members list are required' });
  }

  try {
    const client = await pool.connect();
    try {
      await client.query('BEGIN');

      // If direct, check if chat already exists
      if (type === 'direct' && members.length === 1) {
        const peerId = members[0].userId;
        const existCheck = await client.query(
          `SELECT c.id FROM chats c
           JOIN chat_members cm1 ON c.id = cm1.chat_id AND cm1.user_id = $1
           JOIN chat_members cm2 ON c.id = cm2.chat_id AND cm2.user_id = $2
           WHERE c.type = 'direct'`,
          [req.user.id, peerId]
        );
        if (existCheck.rows.length > 0) {
          await client.query('COMMIT');
          return res.json({ id: existCheck.rows[0].id, isNew: false });
        }
      }

      // Create Chat record
      const chatRes = await client.query(
        'INSERT INTO chats (type, name) VALUES ($1, $2) RETURNING id, type, name',
        [type, name || null]
      );
      const chatId = chatRes.rows[0].id;

      // Add creator to chat_members
      const creatorKey = members.find(m => m.userId === req.user.id);
      await client.query(
        `INSERT INTO chat_members (chat_id, user_id, encrypted_group_key, group_key_nonce) 
         VALUES ($1, $2, $3, $4)`,
        [
          chatId,
          req.user.id,
          creatorKey ? creatorKey.encryptedGroupKey : null,
          creatorKey ? creatorKey.groupKeyNonce : null
        ]
      );

      // Add all other members
      for (const member of members) {
        if (member.userId === req.user.id) continue;
        await client.query(
          `INSERT INTO chat_members (chat_id, user_id, encrypted_group_key, group_key_nonce) 
           VALUES ($1, $2, $3, $4)`,
          [chatId, member.userId, member.encryptedGroupKey || null, member.groupKeyNonce || null]
        );
      }

      await client.query('COMMIT');
      res.status(201).json({ id: chatId, type, name, isNew: true });
    } catch (err) {
      await client.query('ROLLBACK');
      throw err;
    } finally {
      client.release();
    }
  } catch (error) {
    console.error('Chat creation error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// 5. Chats: List my chats
app.get('/api/chats', authenticateToken, async (req, res) => {
  try {
    const result = await pool.query(
      `SELECT c.id, c.type, c.name, 
              cm.encrypted_group_key as "encryptedGroupKey", 
              cm.group_key_nonce as "groupKeyNonce",
              (SELECT json_agg(json_build_object('id', u.id, 'username', u.username, 'publicKey', d.public_key))
               FROM chat_members cm2 
               JOIN users u ON cm2.user_id = u.id
               JOIN devices d ON u.id = d.user_id
               WHERE cm2.chat_id = c.id) as members,
              (SELECT json_build_object('ciphertext', m.ciphertext, 'nonce', m.nonce, 'senderId', m.sender_id, 'createdAt', m.created_at)
               FROM messages m 
               WHERE m.chat_id = c.id 
               ORDER BY m.created_at DESC LIMIT 1) as "lastMessage"
       FROM chats c
       JOIN chat_members cm ON c.id = cm.chat_id
       WHERE cm.user_id = $1`,
      [req.user.id]
    );
    res.json(result.rows);
  } catch (error) {
    console.error('Fetch chats error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// 6. Messages: Fetch message history for a specific chat
app.get('/api/chats/:chatId/messages', authenticateToken, async (req, res) => {
  const { chatId } = req.params;
  try {
    // Verify membership
    const memberCheck = await pool.query(
      'SELECT 1 FROM chat_members WHERE chat_id = $1 AND user_id = $2',
      [chatId, req.user.id]
    );
    if (memberCheck.rows.length === 0) {
      return res.status(403).json({ error: 'Not a member of this chat' });
    }

    const messages = await pool.query(
      `SELECT m.id, m.chat_id as "chatId", m.sender_id as "senderId", 
              m.ciphertext, m.nonce, m.file_url as "fileUrl", m.created_at as "createdAt",
              u.username as "senderUsername"
       FROM messages m
       JOIN users u ON m.sender_id = u.id
       WHERE m.chat_id = $1
       ORDER BY m.created_at ASC`,
      [chatId]
    );

    res.json(messages.rows);
  } catch (error) {
    console.error('Fetch messages error:', error);
    res.status(500).json({ error: 'Server error' });
  }
});

// 7. File Attachment: Upload raw encrypted blob
app.post('/api/attachments/upload', authenticateToken, upload.single('file'), (req, res) => {
  if (!req.file) {
    return res.status(400).json({ error: 'No file uploaded' });
  }
  const fileUrl = `/uploads/${req.file.filename}`;
  res.json({ fileUrl });
});

// ----------------------------------------------------
// WebSocket Messaging Protocols (Socket.io)
// ----------------------------------------------------

io.use((socket, next) => {
  const token = socket.handshake.query.token;
  if (!token) {
    return next(new Error('Authentication error: Token missing'));
  }

  jwt.verify(token, JWT_SECRET, (err, user) => {
    if (err) return next(new Error('Authentication error: Token invalid'));
    socket.user = user;
    next();
  });
});

io.on('connection', async (socket) => {
  const userId = socket.user.id;
  const username = socket.user.username;
  console.log(`User connected: ${username} (${userId})`);

  // Track online presence
  await redis.set(`presence:${userId}`, 'online');
  socket.broadcast.emit('user_presence', { userId, status: 'online' });

  // Let the client join rooms for all their active chats automatically
  try {
    const userChats = await pool.query('SELECT chat_id FROM chat_members WHERE user_id = $1', [userId]);
    userChats.rows.forEach(chat => {
      socket.join(`chat:${chat.chat_id}`);
      console.log(`User ${username} joined room chat:${chat.chat_id}`);
    });
  } catch (err) {
    console.error('Error joining user to rooms:', err);
  }

  // Event 1: Join specific chat (useful when creating a chat dynamically)
  socket.on('join_chat', ({ chatId }) => {
    socket.join(`chat:${chatId}`);
    console.log(`Socket ${socket.id} joined chat:${chatId}`);
  });

  // Event 2: Send Message (Server is purely a blind relay of ciphertexts)
  socket.on('send_message', async (data, ack) => {
    // Expected data: { chatId, ciphertext, nonce, fileUrl }
    const { chatId, ciphertext, nonce, fileUrl } = data;
    if (!chatId || !ciphertext || !nonce) {
      if (ack) ack({ status: 'error', message: 'Missing parameters' });
      return;
    }

    try {
      // 1. Double check sender membership
      const memberCheck = await pool.query(
        'SELECT 1 FROM chat_members WHERE chat_id = $1 AND user_id = $2',
        [chatId, userId]
      );
      if (memberCheck.rows.length === 0) {
        if (ack) ack({ status: 'error', message: 'Unauthorized chat member' });
        return;
      }

      // 2. Save ciphertext blindly in Database
      const insertRes = await pool.query(
        `INSERT INTO messages (chat_id, sender_id, ciphertext, nonce, file_url) 
         VALUES ($1, $2, $3, $4, $5) 
         RETURNING id, created_at`,
        [chatId, userId, ciphertext, nonce, fileUrl || null]
      );
      const messageId = insertRes.rows[0].id;
      const createdAt = insertRes.rows[0].created_at;

      const outgoingMessage = {
        id: messageId,
        chatId,
        senderId: userId,
        senderUsername: username,
        ciphertext,
        nonce,
        fileUrl: fileUrl || null,
        createdAt
      };

      // 3. Broadcast to all members of the chat room
      io.to(`chat:${chatId}`).emit('receive_message', outgoingMessage);

      // 4. Acknowledge back to sender
      if (ack) ack({ status: 'ok', messageId, createdAt });

    } catch (error) {
      console.error('Error processing socket message:', error);
      if (ack) ack({ status: 'error', message: 'Internal Database Error' });
    }
  });

  // Event 3: Message Receipt Status (delivered, read)
  socket.on('message_status', async (data) => {
    // Expected: { messageId, chatId, status }
    const { messageId, chatId, status } = data;
    if (!messageId || !chatId || !status) return;

    try {
      // Upsert receipt status
      await pool.query(
        `INSERT INTO message_statuses (message_id, user_id, status) 
         VALUES ($1, $2, $3)
         ON CONFLICT (message_id, user_id) 
         DO UPDATE SET status = EXCLUDED.status, updated_at = CURRENT_TIMESTAMP`,
        [messageId, userId, status]
      );

      // Broadcast receipt status to the chat members
      io.to(`chat:${chatId}`).emit('receive_message_status', {
        messageId,
        chatId,
        userId,
        status,
        updated_at: new Date()
      });
    } catch (err) {
      console.error('Error tracking message status:', err);
    }
  });

  // Disconnection
  socket.on('disconnect', async () => {
    console.log(`User disconnected: ${username} (${userId})`);
    await redis.del(`presence:${userId}`);
    socket.broadcast.emit('user_presence', { userId, status: 'offline' });
  });
});

// Start the server
server.listen(PORT, '0.0.0.0', () => {
  console.log(`====================================================`);
  console.log(`🔒 Personal E2E Messenger Server Running on Port ${PORT}`);
  console.log(`🏠 Mode: Autonomous Private Instance (Max 10 users)`);
  console.log(`====================================================`);
});

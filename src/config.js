require('dotenv').config();

function required(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Missing required environment variable: ${name}`);
  }
  return value;
}

const config = {
  discord: {
    token: required('DISCORD_TOKEN'),
    clientId: required('DISCORD_CLIENT_ID'),
    guildId: process.env.DISCORD_GUILD_ID || null,
    channelId: process.env.DISCORD_CHANNEL_ID || null,
    adminUserIds: (process.env.ADMIN_USER_IDS || '')
      .split(',')
      .map((id) => id.trim())
      .filter(Boolean),
  },
  minecraft: {
    host: process.env.MC_HOST || 'donutsmp.net',
    port: Number(process.env.MC_PORT || 25565),
    username: required('MC_USERNAME'),
    version: process.env.MC_VERSION || false,
    autoReconnect: (process.env.AUTO_RECONNECT || 'true').toLowerCase() !== 'false',
    sellCommand: process.env.MC_SELL_COMMAND || '/sell all',
    autosellIntervalSeconds: Number(process.env.MC_AUTOSELL_INTERVAL_SECONDS || 300),
  },
};

module.exports = config;

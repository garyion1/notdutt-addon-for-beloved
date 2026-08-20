const path = require('path');
const mineflayer = require('mineflayer');
const { EventEmitter } = require('events');
const config = require('./config');

const RECONNECT_DELAY_MS = 15_000;

class MinecraftManager extends EventEmitter {
  constructor() {
    super();
    this.bot = null;
    this.status = 'disconnected'; // disconnected | connecting | connected
    this.manualDisconnect = false;
    this.reconnectTimer = null;
    this.spawnedAt = null;
  }

  isConnected() {
    return this.status === 'connected';
  }

  /**
   * Connect to the server. onMsaCode is called with { user_code, verification_uri, message }
   * when Microsoft's device-code login needs to be completed by a human.
   * Returns a promise that resolves once the bot has spawned, or rejects on failure.
   */
  connect({ onMsaCode } = {}) {
    if (this.status === 'connecting' || this.status === 'connected') {
      return Promise.reject(new Error(`Already ${this.status}.`));
    }

    this.manualDisconnect = false;
    this.status = 'connecting';

    return new Promise((resolve, reject) => {
      let settled = false;

      const bot = mineflayer.createBot({
        host: config.minecraft.host,
        port: config.minecraft.port,
        username: config.minecraft.username,
        auth: 'microsoft',
        version: config.minecraft.version,
        profilesFolder: path.join(process.cwd(), '.auth-cache'),
        onMsaCode: (data) => {
          if (onMsaCode) onMsaCode(data);
        },
      });

      this.bot = bot;

      bot.once('spawn', () => {
        this.status = 'connected';
        this.spawnedAt = Date.now();
        this.emit('spawn');
        if (!settled) {
          settled = true;
          resolve(bot);
        }
      });

      bot.on('chat', (username, message) => {
        if (username === bot.username) return;
        this.emit('chat', { username, message });
      });

      bot.on('kicked', (reason) => {
        this.emit('kicked', reason);
      });

      bot.on('error', (err) => {
        this.emit('error', err);
        if (!settled) {
          settled = true;
          this.status = 'disconnected';
          reject(err);
        }
      });

      bot.on('end', (reason) => {
        const wasConnected = this.status === 'connected';
        this.status = 'disconnected';
        this.bot = null;
        this.spawnedAt = null;
        this.emit('end', reason);

        if (!settled) {
          settled = true;
          reject(new Error(reason || 'Disconnected before spawning.'));
          return;
        }

        if (wasConnected && !this.manualDisconnect && config.minecraft.autoReconnect) {
          this.scheduleReconnect();
        }
      });
    });
  }

  scheduleReconnect() {
    if (this.reconnectTimer) return;
    this.emit('reconnecting', RECONNECT_DELAY_MS);
    this.reconnectTimer = setTimeout(() => {
      this.reconnectTimer = null;
      this.connect().catch((err) => this.emit('error', err));
    }, RECONNECT_DELAY_MS);
  }

  disconnect() {
    this.manualDisconnect = true;
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    if (this.bot) {
      this.bot.quit();
    }
    this.status = 'disconnected';
    this.bot = null;
    this.spawnedAt = null;
  }

  sendChat(message) {
    if (!this.isConnected() || !this.bot) {
      throw new Error('Not connected to the Minecraft server.');
    }
    this.bot.chat(message);
  }

  getStatusSummary() {
    if (!this.isConnected() || !this.bot) {
      return { connected: false, status: this.status };
    }
    const { health, food, position } = this.bot;
    return {
      connected: true,
      status: this.status,
      username: this.bot.username,
      health,
      food,
      position: position
        ? { x: Math.round(position.x), y: Math.round(position.y), z: Math.round(position.z) }
        : null,
      uptimeMs: this.spawnedAt ? Date.now() - this.spawnedAt : 0,
    };
  }
}

module.exports = new MinecraftManager();

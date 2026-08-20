const fs = require('fs');
const path = require('path');
const { Client, GatewayIntentBits, Collection, Events } = require('discord.js');
const config = require('./config');
const minecraftManager = require('./minecraftManager');
const relayState = require('./relayState');

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent,
  ],
});

client.commands = new Collection();
const commandsPath = path.join(__dirname, 'commands');
for (const file of fs.readdirSync(commandsPath).filter((f) => f.endsWith('.js'))) {
  const command = require(path.join(commandsPath, file));
  client.commands.set(command.data.name, command);
}

client.once(Events.ClientReady, (c) => {
  console.log(`Discord bot logged in as ${c.user.tag}`);
});

client.on(Events.InteractionCreate, async (interaction) => {
  if (!interaction.isChatInputCommand()) return;

  const command = client.commands.get(interaction.commandName);
  if (!command) return;

  try {
    await command.execute(interaction);
  } catch (err) {
    console.error(`Error executing ${interaction.commandName}:`, err);
    const payload = { content: 'Something went wrong running that command.', ephemeral: true };
    if (interaction.deferred || interaction.replied) {
      await interaction.editReply(payload).catch(() => {});
    } else {
      await interaction.reply(payload).catch(() => {});
    }
  }
});

// Discord -> Minecraft relay
client.on(Events.MessageCreate, (message) => {
  if (!relayState.enabled) return;
  if (message.author.bot) return;
  if (!config.discord.channelId || message.channelId !== config.discord.channelId) return;
  if (!minecraftManager.isConnected()) return;

  try {
    minecraftManager.sendChat(`<${message.author.username}> ${message.content}`.slice(0, 256));
  } catch (err) {
    console.error('Failed to relay Discord message to Minecraft:', err);
  }
});

// Minecraft -> Discord relay + notifications
minecraftManager.on('chat', ({ username, message }) => {
  if (!relayState.enabled || !config.discord.channelId) return;
  const channel = client.channels.cache.get(config.discord.channelId);
  if (channel) channel.send(`**${username}**: ${message}`).catch(() => {});
});

minecraftManager.on('kicked', (reason) => {
  console.warn('Kicked from donutsmp.net:', reason);
  notifyChannel(`Kicked from donutsmp.net: ${reason}`);
});

minecraftManager.on('error', (err) => {
  console.error('Minecraft bot error:', err);
});

minecraftManager.on('end', (reason) => {
  console.warn('Disconnected from donutsmp.net:', reason);
});

minecraftManager.on('reconnecting', (delayMs) => {
  notifyChannel(`Connection lost, reconnecting in ${Math.round(delayMs / 1000)}s...`);
});

function notifyChannel(text) {
  if (!config.discord.channelId) return;
  const channel = client.channels.cache.get(config.discord.channelId);
  if (channel) channel.send(text).catch(() => {});
}

client.login(config.discord.token);

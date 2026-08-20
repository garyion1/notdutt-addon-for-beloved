const fs = require('fs');
const path = require('path');
const { REST, Routes } = require('discord.js');
const config = require('./config');

const commandsPath = path.join(__dirname, 'commands');
const commands = fs
  .readdirSync(commandsPath)
  .filter((file) => file.endsWith('.js'))
  .map((file) => require(path.join(commandsPath, file)).data.toJSON());

const rest = new REST().setToken(config.discord.token);

(async () => {
  try {
    const route = config.discord.guildId
      ? Routes.applicationGuildCommands(config.discord.clientId, config.discord.guildId)
      : Routes.applicationCommands(config.discord.clientId);

    const data = await rest.put(route, { body: commands });
    console.log(`Registered ${data.length} slash command(s)${config.discord.guildId ? ' for the guild' : ' globally'}.`);
  } catch (err) {
    console.error('Failed to register slash commands:', err);
    process.exitCode = 1;
  }
})();

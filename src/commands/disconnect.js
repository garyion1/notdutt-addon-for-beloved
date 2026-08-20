const { SlashCommandBuilder } = require('discord.js');
const isAdmin = require('../isAdmin');
const minecraftManager = require('../minecraftManager');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('mc-disconnect')
    .setDescription('Log the Minecraft account out of donutsmp.net'),

  async execute(interaction) {
    if (!isAdmin(interaction)) {
      return interaction.reply({ content: 'You are not allowed to run this command.', ephemeral: true });
    }

    if (minecraftManager.status === 'disconnected') {
      return interaction.reply({ content: 'Already disconnected.', ephemeral: true });
    }

    minecraftManager.disconnect();
    await interaction.reply({ content: 'Disconnected from donutsmp.net.', ephemeral: true });
  },
};

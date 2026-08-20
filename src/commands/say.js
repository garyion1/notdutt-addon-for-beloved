const { SlashCommandBuilder } = require('discord.js');
const isAdmin = require('../isAdmin');
const minecraftManager = require('../minecraftManager');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('mc-say')
    .setDescription('Send a chat message as the Minecraft account')
    .addStringOption((option) =>
      option.setName('message').setDescription('Message to send in-game').setRequired(true)
    ),

  async execute(interaction) {
    if (!isAdmin(interaction)) {
      return interaction.reply({ content: 'You are not allowed to run this command.', ephemeral: true });
    }

    const message = interaction.options.getString('message', true);

    try {
      minecraftManager.sendChat(message);
      await interaction.reply({ content: `Sent: \`${message}\``, ephemeral: true });
    } catch (err) {
      await interaction.reply({ content: `Failed: ${err.message}`, ephemeral: true });
    }
  },
};

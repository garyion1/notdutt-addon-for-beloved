const { SlashCommandBuilder } = require('discord.js');
const isAdmin = require('../isAdmin');
const config = require('../config');
const relayState = require('../relayState');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('mc-relay')
    .setDescription('Toggle relaying chat between this Discord channel and the Minecraft server')
    .addStringOption((option) =>
      option
        .setName('state')
        .setDescription('on or off')
        .setRequired(true)
        .addChoices({ name: 'on', value: 'on' }, { name: 'off', value: 'off' })
    ),

  async execute(interaction) {
    if (!isAdmin(interaction)) {
      return interaction.reply({ content: 'You are not allowed to run this command.', ephemeral: true });
    }

    if (!config.discord.channelId) {
      return interaction.reply({
        content: 'Set DISCORD_CHANNEL_ID in the environment before enabling the relay.',
        ephemeral: true,
      });
    }

    const state = interaction.options.getString('state', true);
    relayState.enabled = state === 'on';

    await interaction.reply({ content: `Chat relay is now **${state}**.`, ephemeral: true });
  },
};

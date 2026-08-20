const { SlashCommandBuilder } = require('discord.js');
const isAdmin = require('../isAdmin');
const config = require('../config');
const minecraftManager = require('../minecraftManager');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('mc-autosell')
    .setDescription(`Repeatedly run "${config.minecraft.sellCommand}" in-game to sell your inventory`)
    .addStringOption((option) =>
      option
        .setName('action')
        .setDescription('start, stop, or run it once right now')
        .setRequired(true)
        .addChoices(
          { name: 'start', value: 'start' },
          { name: 'stop', value: 'stop' },
          { name: 'once', value: 'once' }
        )
    )
    .addIntegerOption((option) =>
      option
        .setName('interval_seconds')
        .setDescription(`Seconds between sells (default ${config.minecraft.autosellIntervalSeconds}, only used with start)`)
        .setMinValue(5)
        .setMaxValue(3600)
    ),

  async execute(interaction) {
    if (!isAdmin(interaction)) {
      return interaction.reply({ content: 'You are not allowed to run this command.', ephemeral: true });
    }

    if (!minecraftManager.isConnected()) {
      return interaction.reply({ content: 'Not connected to donutsmp.net. Run /mc-connect first.', ephemeral: true });
    }

    const action = interaction.options.getString('action', true);
    const intervalSeconds = interaction.options.getInteger('interval_seconds') ?? config.minecraft.autosellIntervalSeconds;

    try {
      if (action === 'start') {
        minecraftManager.startAutosell(intervalSeconds);
        await interaction.reply({
          content: `Autosell started: sending \`${config.minecraft.sellCommand}\` every ${intervalSeconds}s.`,
          ephemeral: true,
        });
      } else if (action === 'stop') {
        minecraftManager.stopAutosell();
        await interaction.reply({ content: 'Autosell stopped.', ephemeral: true });
      } else {
        minecraftManager.sendChat(config.minecraft.sellCommand);
        await interaction.reply({ content: `Sent \`${config.minecraft.sellCommand}\` once.`, ephemeral: true });
      }
    } catch (err) {
      await interaction.reply({ content: `Failed: ${err.message}`, ephemeral: true });
    }
  },
};

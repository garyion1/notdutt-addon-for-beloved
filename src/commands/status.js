const { SlashCommandBuilder, EmbedBuilder } = require('discord.js');
const minecraftManager = require('../minecraftManager');

function formatUptime(ms) {
  const totalSeconds = Math.floor(ms / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return `${hours}h ${minutes}m ${seconds}s`;
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName('mc-status')
    .setDescription('Show the current donutsmp.net connection status'),

  async execute(interaction) {
    const summary = minecraftManager.getStatusSummary();

    if (!summary.connected) {
      return interaction.reply({
        content: `Status: **${summary.status}**`,
        ephemeral: true,
      });
    }

    const embed = new EmbedBuilder()
      .setTitle(`Connected as ${summary.username}`)
      .addFields(
        { name: 'Health', value: `${summary.health ?? '?'}/20`, inline: true },
        { name: 'Food', value: `${summary.food ?? '?'}/20`, inline: true },
        { name: 'Uptime', value: formatUptime(summary.uptimeMs), inline: true },
        {
          name: 'Position',
          value: summary.position ? `${summary.position.x}, ${summary.position.y}, ${summary.position.z}` : 'unknown',
        },
        {
          name: 'Autosell',
          value: summary.autoselling ? `on (every ${Math.round(summary.autosellIntervalMs / 1000)}s)` : 'off',
        }
      )
      .setColor(0x2ecc71);

    await interaction.reply({ embeds: [embed], ephemeral: true });
  },
};

const { SlashCommandBuilder } = require('discord.js');
const isAdmin = require('../isAdmin');
const minecraftManager = require('../minecraftManager');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('mc-pay')
    .setDescription('Pay another player in-game via /pay')
    .addStringOption((option) =>
      option.setName('player').setDescription('In-game username to pay').setRequired(true)
    )
    .addNumberOption((option) =>
      option.setName('amount').setDescription('Amount to send').setRequired(true).setMinValue(0.01)
    ),

  async execute(interaction) {
    if (!isAdmin(interaction)) {
      return interaction.reply({ content: 'You are not allowed to run this command.', ephemeral: true });
    }

    const player = interaction.options.getString('player', true);
    const amount = interaction.options.getNumber('amount', true);

    try {
      minecraftManager.sendChat(`/pay ${player} ${amount}`);
      await interaction.reply({ content: `Sent: \`/pay ${player} ${amount}\``, ephemeral: true });
    } catch (err) {
      await interaction.reply({ content: `Failed: ${err.message}`, ephemeral: true });
    }
  },
};

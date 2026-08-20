const { SlashCommandBuilder } = require('discord.js');
const isAdmin = require('../isAdmin');
const minecraftManager = require('../minecraftManager');

module.exports = {
  data: new SlashCommandBuilder()
    .setName('mc-connect')
    .setDescription('Log the Minecraft account into donutsmp.net'),

  async execute(interaction) {
    if (!isAdmin(interaction)) {
      return interaction.reply({ content: 'You are not allowed to run this command.', ephemeral: true });
    }

    if (minecraftManager.status !== 'disconnected') {
      return interaction.reply({
        content: `Already ${minecraftManager.status}.`,
        ephemeral: true,
      });
    }

    await interaction.deferReply({ ephemeral: true });

    minecraftManager
      .connect({
        onMsaCode: (data) => {
          interaction
            .editReply(
              `**Microsoft sign-in required.**\n${data.message || `Open ${data.verification_uri} and enter code \`${data.user_code}\`.`}`
            )
            .catch(() => {});
        },
      })
      .then((bot) => {
        interaction.editReply(`Connected to donutsmp.net as \`${bot.username}\`.`).catch(() => {});
      })
      .catch((err) => {
        interaction.editReply(`Failed to connect: ${err.message}`).catch(() => {});
      });
  },
};

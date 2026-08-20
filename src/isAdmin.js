const { PermissionsBitField } = require('discord.js');
const config = require('./config');

function isAdmin(interaction) {
  if (config.discord.adminUserIds.length > 0) {
    return config.discord.adminUserIds.includes(interaction.user.id);
  }
  return Boolean(
    interaction.memberPermissions &&
      interaction.memberPermissions.has(PermissionsBitField.Flags.ManageGuild)
  );
}

module.exports = isAdmin;

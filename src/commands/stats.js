const { SlashCommandBuilder, EmbedBuilder, AttachmentBuilder } = require('discord.js');
const { createCanvas, loadImage } = require('@napi-rs/canvas');
const minecraftManager = require('../minecraftManager');

const WIDTH = 900;
const HEIGHT = 420;
const AVATAR_SIZE = 180;

function drawBar(ctx, x, y, w, h, ratio, fillColor) {
  ctx.fillStyle = 'rgba(0, 0, 0, 0.45)';
  roundRect(ctx, x, y, w, h, h / 2);
  ctx.fill();

  const clamped = Math.max(0, Math.min(1, ratio));
  if (clamped > 0) {
    ctx.fillStyle = fillColor;
    roundRect(ctx, x, y, Math.max(h, w * clamped), h, h / 2);
    ctx.fill();
  }
}

function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

// Vector icons instead of emoji glyphs — headless Linux hosts usually lack an emoji font,
// which renders emoji as blank tofu boxes on the generated image.
function drawHeartIcon(ctx, cx, cy, size, color) {
  const s = size / 2;
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.moveTo(cx, cy + s * 0.6);
  ctx.bezierCurveTo(cx - s * 1.3, cy - s * 0.5, cx - s * 0.4, cy - s * 1.4, cx, cy - s * 0.4);
  ctx.bezierCurveTo(cx + s * 0.4, cy - s * 1.4, cx + s * 1.3, cy - s * 0.5, cx, cy + s * 0.6);
  ctx.closePath();
  ctx.fill();
}

function drawFoodIcon(ctx, cx, cy, size, color) {
  const r = size * 0.22;
  const offsets = [
    [-size * 0.28, size * 0.05],
    [0, -size * 0.22],
    [size * 0.28, size * 0.05],
    [0, size * 0.28],
  ];
  ctx.fillStyle = color;
  for (const [dx, dy] of offsets) {
    ctx.beginPath();
    ctx.arc(cx + dx, cy + dy, r, 0, Math.PI * 2);
    ctx.fill();
  }
}

async function fetchAvatar(username) {
  try {
    const res = await fetch(`https://mc-heads.net/avatar/${encodeURIComponent(username)}/${AVATAR_SIZE}.png`);
    if (!res.ok) return null;
    const buffer = Buffer.from(await res.arrayBuffer());
    return await loadImage(buffer);
  } catch {
    return null;
  }
}

async function renderStatsCard(bot) {
  const canvas = createCanvas(WIDTH, HEIGHT);
  const ctx = canvas.getContext('2d');

  const bg = ctx.createLinearGradient(0, 0, WIDTH, HEIGHT);
  bg.addColorStop(0, '#1e2a1c');
  bg.addColorStop(1, '#2f4230');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, WIDTH, HEIGHT);

  roundRect(ctx, 20, 20, WIDTH - 40, HEIGHT - 40, 24);
  ctx.fillStyle = 'rgba(0, 0, 0, 0.25)';
  ctx.fill();

  const avatar = await fetchAvatar(bot.username);
  const avatarX = 55;
  const avatarY = 60;
  if (avatar) {
    ctx.drawImage(avatar, avatarX, avatarY, AVATAR_SIZE, AVATAR_SIZE);
  } else {
    ctx.fillStyle = '#555';
    ctx.fillRect(avatarX, avatarY, AVATAR_SIZE, AVATAR_SIZE);
  }
  ctx.strokeStyle = '#ffffff33';
  ctx.lineWidth = 3;
  ctx.strokeRect(avatarX, avatarY, AVATAR_SIZE, AVATAR_SIZE);

  ctx.fillStyle = '#ffffff';
  ctx.font = 'bold 34px sans-serif';
  ctx.fillText(bot.username, avatarX, avatarY + AVATAR_SIZE + 45);
  ctx.font = '20px sans-serif';
  ctx.fillStyle = '#b6c9b6';
  ctx.fillText('donutsmp.net', avatarX, avatarY + AVATAR_SIZE + 75);

  const infoX = avatarX + AVATAR_SIZE + 50;
  let infoY = 75;
  const lineGap = 46;

  const health = bot.health ?? 0;
  const food = bot.food ?? 0;
  const level = bot.experience ? bot.experience.level : 0;
  const gamemode = bot.game ? bot.game.gameMode : 'unknown';
  const dimension = bot.game ? bot.game.dimension : 'unknown';
  const pos = bot.entity ? bot.entity.position : null;
  const playersOnline = bot.players ? Object.keys(bot.players).length : 0;
  const ping = bot.players && bot.players[bot.username] ? bot.players[bot.username].ping : null;

  ctx.font = 'bold 22px sans-serif';
  ctx.fillStyle = '#ffffff';
  drawHeartIcon(ctx, infoX + 10, infoY - 8, 22, '#e0483f');
  ctx.fillText(`Health  ${health.toFixed(1)}/20`, infoX + 30, infoY);
  drawBar(ctx, infoX, infoY + 12, 340, 18, health / 20, '#e0483f');
  infoY += lineGap;

  drawFoodIcon(ctx, infoX + 10, infoY - 8, 22, '#c98a3e');
  ctx.fillText(`Food  ${food.toFixed(1)}/20`, infoX + 30, infoY);
  drawBar(ctx, infoX, infoY + 12, 340, 18, food / 20, '#c98a3e');
  infoY += lineGap + 10;

  ctx.font = '22px sans-serif';
  const rows = [
    ['XP Level', `${level}`],
    ['Gamemode', `${gamemode}`],
    ['Dimension', `${dimension}`],
    ['Position', pos ? `${Math.round(pos.x)}, ${Math.round(pos.y)}, ${Math.round(pos.z)}` : 'unknown'],
    ['Players online', `${playersOnline}`],
    ['Ping', ping != null ? `${ping}ms` : 'unknown'],
  ];

  for (const [label, value] of rows) {
    ctx.fillStyle = '#b6c9b6';
    ctx.fillText(label, infoX, infoY);
    ctx.fillStyle = '#ffffff';
    ctx.fillText(value, infoX + 220, infoY);
    infoY += 34;
  }

  return canvas.encode('png');
}

module.exports = {
  data: new SlashCommandBuilder()
    .setName('mc-stats')
    .setDescription('Generate a stats card for the bot\'s current in-game state'),

  async execute(interaction) {
    const bot = minecraftManager.getBot();
    if (!bot) {
      return interaction.reply({ content: 'Not connected to the Minecraft server.', ephemeral: true });
    }

    await interaction.deferReply();

    try {
      const pngBuffer = await renderStatsCard(bot);
      const attachment = new AttachmentBuilder(pngBuffer, { name: 'stats.png' });
      const embed = new EmbedBuilder()
        .setTitle(`${bot.username}'s stats`)
        .setImage('attachment://stats.png')
        .setColor(0x2ecc71)
        .setTimestamp();

      await interaction.editReply({ embeds: [embed], files: [attachment] });
    } catch (err) {
      await interaction.editReply({ content: `Failed to generate stats card: ${err.message}` });
    }
  },
};

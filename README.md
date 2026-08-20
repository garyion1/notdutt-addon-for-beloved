# donutsmp-discord-bot

A Discord bot that logs a Minecraft account into `donutsmp.net` and lets you control it from Discord.

Built with [discord.js](https://discord.js.org/) and [mineflayer](https://github.com/PrismarineJS/mineflayer).

## What it does

- `/mc-connect` — logs the configured Microsoft account into donutsmp.net. Since the server requires a premium account, sign-in uses Microsoft's device-code OAuth flow; the bot posts the sign-in link and code back to you in Discord (ephemeral reply) instead of needing terminal access. After the first successful login the auth token is cached locally (`.auth-cache/`, gitignored) so you won't need to repeat this every time.
- `/mc-disconnect` — logs out.
- `/mc-status` — health, food, position, uptime.
- `/mc-say <message>` — sends a chat message in-game.
- `/mc-pay <player> <amount>` — sends `/pay <player> <amount>` in-game.
- `/mc-relay on|off` — mirrors chat between a designated Discord channel and in-game chat.
- `/mc-autosell start|stop|once [interval_seconds]` — repeatedly sends the sell command (`MC_SELL_COMMAND`, default `/sell all`) in-game so it keeps selling whatever's in the inventory. `start` sells immediately then repeats on the given interval (default `MC_AUTOSELL_INTERVAL_SECONDS`, 300s); `once` sells a single time; `stop` cancels the loop. Autosell automatically stops if the bot disconnects.
- `/mc-stats` — generates a custom stats card image (skin avatar, health/food bars, XP level, gamemode, dimension, position, players online, ping) and posts it in Discord. There's no real in-game screenshot to take — mineflayer is a headless protocol client with no renderer — so this draws the card itself from live bot state instead.

`/mc-connect`, `/mc-disconnect`, `/mc-say`, `/mc-pay`, `/mc-relay`, and `/mc-autosell` are restricted to admins: either the Discord user IDs listed in `ADMIN_USER_IDS`, or anyone with "Manage Server" if that variable is left blank.

The bot auto-reconnects after an unexpected disconnect (kick, network drop) unless you disconnected it manually, controlled by `AUTO_RECONNECT`.

## Setup

1. **Create a Discord application** at https://discord.com/developers/applications, add a Bot user, and copy the bot token and application (client) ID. Invite the bot to your server with the `applications.commands` and `bot` scopes (Send Messages / Use Slash Commands permissions are enough).

2. **Install dependencies:**
   ```bash
   npm install
   ```

3. **Configure environment variables:**
   ```bash
   cp .env.example .env
   ```
   Fill in `DISCORD_TOKEN`, `DISCORD_CLIENT_ID`, `MC_USERNAME` (the Microsoft account email for the premium Minecraft account you're logging in), and optionally `DISCORD_GUILD_ID` (for instant command registration during dev), `DISCORD_CHANNEL_ID` (for the chat relay), and `ADMIN_USER_IDS`.

4. **Register the slash commands:**
   ```bash
   npm run deploy-commands
   ```

5. **Run the bot:**
   ```bash
   npm start
   ```

6. In Discord, run `/mc-connect`. The bot will reply with a Microsoft sign-in link and one-time code — open the link in a browser, enter the code, and sign in with the Minecraft account. Once that completes, the bot spawns into donutsmp.net.

## A note on server rules

Check donutsmp.net's current rules before running any automation on your account (this project sends chat commands like `/sell all` and `/pay` on your behalf via `/mc-autosell`, in addition to login/chat/status) — use this at your own risk.

## Project structure

```
src/
  config.js            environment variable loading/validation
  isAdmin.js            permission check shared by sensitive commands
  minecraftManager.js   mineflayer connection lifecycle (singleton, event emitter)
  relayState.js         in-memory on/off flag for the chat relay
  deploy-commands.js    registers slash commands with Discord
  index.js              Discord client bootstrap + event wiring
  commands/
    connect.js
    disconnect.js
    status.js
    stats.js
    say.js
    pay.js
    relay.js
    autosell.js
```

## Requirements

Node.js 18+ (uses the global `fetch` API to pull the player's skin avatar for `/mc-stats`).

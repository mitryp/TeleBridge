# TeleBridge

Bridges a Forge 1.20.1 server and a Telegram group chat.

- **Minecraft → Telegram**: chat, joins and leaves (with the disconnect reason), deaths, advancements, server start/stop.
- **Telegram → Minecraft**: `/say`, `/online`, account linking, and admin commands for allowlisted users.

Server-side only; clients don't need it.

## Install

1. Create a bot with [@BotFather](https://t.me/BotFather) and add it to the group. It needs no admin rights,
   and privacy mode can stay on — the bot only reads commands and replies to its own messages.
2. Put `telebridge-<version>.jar` in the server's `mods/` and start it once to generate
   `config/telebridge-common.toml`.
3. Fill in the config (below) and restart the server.

To find the group's chat id, send any message in the group and open
`https://api.telegram.org/bot<token>/getUpdates` — it is `message.chat.id` (negative for groups).

## Config

`config/telebridge-common.toml`. Restart the server after editing.

| Key | Default | |
|---|---|---|
| `telegram.enabled` | `false` | Master switch |
| `telegram.bot_token` | | From BotFather |
| `telegram.chat_id` | | The bridged group |
| `telegram.use_markdown_v2` | `true` | Renders join/leave/death lines as quotes |
| `telegram.inbound.enabled` | `false` | Accept commands from Telegram |
| `telegram.inbound.poll_seconds` | `20` | Long-poll timeout |
| `telegram.inbound.prefix` | `/` | Command prefix |
| `telegram.admin.user_ids` | `[]` | Numeric Telegram user ids allowed to run admin commands |
| `service.chat` | `true` | Forward chat |
| `service.join_quit` | `true` | Forward joins and leaves |
| `service.deaths` | `true` | Forward deaths |
| `service.advancements` | `true` | Forward advancements (also follows the `announceAdvancements` gamerule) |
| `service.start_stop` | `true` | Forward server start/stop |

The bot's command menu is published by the mod on startup: everyone sees the public commands, admins also
see the admin ones. Telegram clients may cache the menu for a while.

## Telegram commands

| Command | |
|---|---|
| `/say <text>` | Send a message to the game. Bare `/say` asks for the text and deletes the prompt afterwards |
| `/online` | Players online |
| `/link <code>` | Link your Telegram account to your player (code from `/tglink` in game). Bare `/link` asks for the code |
| `/unlink` | Remove your link |

Admin only (`telegram.admin.user_ids`); every use is logged and echoed to online operators:

| Command | |
|---|---|
| `/tps` | TPS, tick time, players online |
| `/kick <player> [reason]` | Plain player names only — selectors like `@a` are refused |
| `/keyauth list`, `/keyauth reset <player>` | [KeyAuth](https://github.com/mitryp/KeyAuth) key management |
| `/save` | Save the world |
| `/restart confirm` | Warns players, stops the server after 10 s. Needs a supervisor that restarts it (e.g. Docker `restart: unless-stopped`) |

Commands picked from the group's command menu (`/online@your_bot`) work; commands addressed to other bots are ignored.
Messages sent while the server was down are skipped, not replayed.

## In-game commands

| Command | |
|---|---|
| `/tglink` | Shows your link, or a one-time code (click to copy `/link <code>`) valid for 10 minutes |
| `/tg_unlink` | Remove your link |

## Linking

Linking ties a Telegram **user id** to a Minecraft name, so `/say` from that account shows the player name instead of
the Telegram username. The code proves the Telegram user is also the player: it is shown only to the player in game,
works once, and is replaced by a newer `/tglink`. Each account and each player can have one link. Five wrong codes lock
a Telegram user out for 10 minutes.

Links live in `config/telebridge-links.json`. Files from 0.5.0 and earlier (username → name) are still read; each of
those links is converted to a user-id link the first time that user sends a message the bot sees.

## Delivery

Outgoing messages go out in order from one thread. Lines arriving within a second are merged into one message, and
each player's advancements are collected for 5 s, which keeps the group under Telegram's rate limit. On a 429 the
bot waits as long as Telegram asks. On shutdown it waits up to 5 s to deliver what is queued.

## Build

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 17) ./gradlew build   # → build/libs/telebridge-<version>.jar, runs the tests
```

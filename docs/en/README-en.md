# BlockRacing · Draftout

[简体中文](../../README.md) · [Translation](TranslationTutorial-en.md)

A score-based Minecraft team race derived from [LQSnow/BlockRacing](https://github.com/LQSnow/BlockRacing). Targets are shared and exclusive: completing a task removes it from the other team's list. The first team to earn at least half of the initial regular-task score wins, rounded up.

Target server: Paper 26.2. Build/runtime requirements: JDK 25 or newer and Maven. Plugin version: 26.2.1. Upstream `26.2` (original commit `971489d`) is merged while retaining Draftout scoring.

## Branches

- `main` was renamed from `local-3.5-patch`. It contains scored tasks, Bonus tasks, CSV definitions and the task progress menu.
- `feature/per-team-worlds` is **on hold due to excessive performance overhead**. Its separate team worlds, respawn handling and portal routing are retained for reference and are not being merged into `main`.
- `3.0` and `26.2` preserve upstream snapshots with server binaries removed from history.

## Gameplay

- By default, each game draws 64 regular tasks worth 1–10 points, with at most eight 1-point tasks.
- Three additional Bonus tasks are drawn from tasks worth 11 or more points. Their scores reward spendable currency and do not contribute to the win threshold.
- Regular tasks contribute their score to victory progress and award one spendable point, or three in speed mode. Spending currency does not reduce victory progress.
- Both modes share exclusive tasks. Normal mode also gives the opposing team a stack of a completed item; racing mode, the default, does not.
- Open `/menu` or press Shift+F to select a team and get ready. All online players must be ready to start. Single-player and one-team games are allowed for testing.
- Open `/menu targets` to see tasks, scores and progress. The menu also provides team chests, waypoints, locate access and random teleportation. The game starts on Hard difficulty.

## Build and install

Task icons now identify concrete objects, with category filtering and an operator-only `/menu targetpreview` before a game. An optional 26.2 resource pack adds action/quantity badges; unmodified clients retain native icons. See [task UI and pack maintenance](../task-ui.md) for installation, limitations and offline build instructions.

```sh
mvn clean verify
```

Copy `target/BlockRacing-26.2.1.jar` into the `plugins/` directory of a separately installed Paper 26.2 server. Server binaries, worlds and generated outputs are excluded from Git.

On first startup, the plugin creates `plugins/BlockRacing/` with configuration, language resources and `Targets.csv`. Existing resources are not automatically replaced. Back up and compare your files when upgrading; edit server files while the server is stopped.

`mvn verify` runs tests for sampling, resources, task-menu grouping, saved progress and upstream utilities. Multiplayer behavior still needs in-game verification.

The upgrade includes per-player Chinese/English (`/language`), team chat and global `/shout`, teammate teleport menus, speed supplies and saved-game recovery. Recovery preserves currency, victory points, Bonus tasks and custom goal progress separately. Incompatible task definitions cause the saved game to be backed up instead of resumed. Back up worlds and update your existing configuration, CSV and both language files before upgrading; old 1.21.11 servers cannot load this plugin.

The paused feature branch still targets 1.21.11/JDK 21 and does not receive this upgrade. On `feature/per-team-worlds`, the six `world_red*` and `world_blue*` worlds belong to the plugin and are cleaned up after a win and on the next startup. Reserve these names for the game. `main` does not manage separate team worlds.

## Configuration and commands

See [the canonical configuration](../../src/main/resources/config.yml) for defaults. Regular-task count, the easy-task cap, Bonus threshold/count, available tasks, locate cost and teleport cost are configurable. Each team has eight chests and eight waypoints by default.

| Command | Purpose |
| --- | --- |
| `/menu`, `/menu targets` | Main menu and task list |
| `/menu chest [index]`, `/menu waypoints` | Team storage and waypoints |
| `/menu locate` | Buy locate access |
| `/locatebiome <biome>`, `/locatestructure <structure>` | Locate a biome or structure |
| `/menu randomTP` | Random teleport; the first menu teleport is free |
| `/tp <teammate>` | Team teleport; use `/minecraft:tp` for the vanilla command |
| `/block <red or blue> <index>` | Inspect a current target |
| `/language [auto or zh_cn or en_us]` | Choose a per-player language |
| `/shout <message>` | Global message during a game; normal chat is team-only |
| `/randomteam` | Assign teams randomly after confirmation |
| `/restartgame` | Shut down after confirmation; back up/reset worlds on next startup; restarting needs an external launcher |
| `/sampleblocks [amount]` | Admin preview of regular targets; use before a game |
| `/debug reload` | Player-admin message reload; also reload targets before a game |
| `/debug setscore <red or blue> <score>` | Set spendable currency, not victory progress |

`/debug start` is available only on `feature/per-team-worlds` for admin testing.

Detailed Chinese documentation: [target definitions and sampling](../targets.md), [development and manual checks](../development.md), [changelog](../../CHANGELOG.md). Historical weight curves are [archived](../archive/weight-curves.md) and no longer describe current sampling.

Licensed under [GNU AGPL v3.0](../../LICENSE), retaining upstream attribution. This fork is hosted at [DarkPaper2022/BlockRacing](https://github.com/DarkPaper2022/BlockRacing).

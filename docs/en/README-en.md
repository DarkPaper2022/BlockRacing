# BlockRacing · Draftout

[简体中文](../../README.md) · [Translation](TranslationTutorial-en.md)

A score-based Minecraft team race derived from [LQSnow/BlockRacing](https://github.com/LQSnow/BlockRacing). Targets are shared and exclusive: completing a task removes it from the other team's list. The first team to earn at least half of the initial regular-task score wins, rounded up.

Target server: Minecraft 1.21.11. Build requirements: JDK 21 and Maven. Plugin version: 3.5.

## Branches

- `main` was renamed from `local-3.5-patch`. It contains scored tasks, Bonus tasks, CSV definitions and the task progress menu.
- `feature/per-team-worlds` adds separate red/blue worlds with the same seed, respawn handling and portal routing. These features are not merged into `main` and still need multiplayer regression testing.
- `3.0` and `26.2` preserve upstream snapshots with server binaries removed from history.

## Gameplay

- By default, each game draws 64 regular tasks worth 1–10 points, with at most eight 1-point tasks.
- Three additional Bonus tasks are drawn from tasks worth 11 or more points. Their scores reward spendable currency and do not contribute to the win threshold.
- Regular tasks contribute their score to victory progress and award one spendable point, or three in speed mode. Spending currency does not reduce victory progress.
- Both modes share exclusive tasks. Normal mode also gives the opposing team a stack of a completed item; racing mode, the default, does not.
- Open `/menu` or press Shift+F to select a team and get ready. All online players must be ready to start. Single-player and one-team games are allowed for testing.
- Open `/menu targets` to see tasks, scores and progress. The menu also provides team chests, waypoints, locate access and random teleportation. The game starts on Hard difficulty.

## Build and install

```sh
mvn clean verify
```

Copy `target/BlockRacing-3.5.jar` into the `plugins/` directory of a separately installed Paper 1.21.11 server. Server binaries, worlds and generated outputs are excluded from Git.

On first startup, the plugin creates `plugins/BlockRacing/` with configuration, language resources and `Targets.csv`. Existing resources are not automatically replaced. Back up and compare your files when upgrading; edit server files while the server is stopped.

The project currently has no automated tests. A successful build confirms compilation and packaging, not multiplayer behavior.

On `feature/per-team-worlds`, the six `world_red*` and `world_blue*` worlds belong to the plugin and are cleaned up after a win and on the next startup. Reserve these names for the game. `main` does not manage separate team worlds.

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
| `/randomteam` | Assign teams randomly |
| `/restartgame` | Shut down after player confirmation; restarting needs an external launcher |
| `/sampleblocks [amount]` | Admin preview of regular targets; use before a game |
| `/debug reload` | Player-admin message reload; also reload targets before a game |
| `/debug setscore <red or blue> <score>` | Set spendable currency, not victory progress |

`/debug start` is available only on `feature/per-team-worlds` for admin testing.

Detailed Chinese documentation: [target definitions and sampling](../targets.md), [development and manual checks](../development.md), [changelog](../../CHANGELOG.md). Historical weight curves are [archived](../archive/weight-curves.md) and no longer describe current sampling.

Licensed under [GNU AGPL v3.0](../../LICENSE), retaining upstream attribution. This fork is hosted at [DarkPaper2022/BlockRacing](https://github.com/DarkPaper2022/BlockRacing).

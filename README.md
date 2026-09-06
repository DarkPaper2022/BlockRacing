# BlockRacing · Draftout

[English](docs/en/README-en.md) · [任务库与抽取规则](docs/targets.md) · [开发与验证](docs/development.md) · [翻译](TranslationTutorial.md)

基于 [LQSnow/BlockRacing](https://github.com/LQSnow/BlockRacing) 的 Minecraft 红蓝队任务竞速插件。当前分支使用积分制：双方争夺同一组任务，先拿到本局普通任务总分至少 50% 的队伍获胜。

目标服务器为 Paper 26.2，构建及运行使用 JDK 25 或更新版本，插件版本为 26.2.1。已合并上游 `26.2`（原提交 `971489d`）；保留 Draftout 的积分与任务规则。

## 分支

- `main`：原 `local-3.5-patch`，包含积分制、Bonus、CSV 任务库和任务进度菜单，是后续开发的基线。
- `feature/per-team-worlds`：**暂缓开发**，同种子队伍独立世界的性能开销过大；保留代码作参考，不合入 `main`。
- `3.0`、`26.2`：保留的上游版本快照，历史中的服务端二进制已移除；`26.2` 已合并进 `main`。

独立世界功能尚未合并进 `main`。分支整理不代表完成了游戏内验收。

## 当前玩法

1. 每局默认抽取 64 个普通任务，分数为 1–10；1 分任务最多 8 个，上限可配置。
2. 另抽取 3 个 Bonus 任务，默认来自 11 分及以上的任务池。Bonus 不计入胜利总分，完成后按其分值奖励可用积分。
3. 普通任务完成后增加胜利进度，并奖励 1 点可用积分；极速模式奖励 3 点。定位和随机传送消耗可用积分，不扣胜利进度。
4. 双方共享任务列表；一方完成后，该任务也从对方列表移除。胜利门槛为开局普通任务总分的一半，向上取整。
5. 普通模式完成物品任务会向对方队伍箱子赠送一组该物品；竞速模式不赠送，默认使用竞速模式。

加入服务器后，按 Shift+F 或输入 `/menu`，选队并准备。所有在线玩家准备后可开始；允许单人或只有一支队伍参与，便于本地测试。

开局后通过 `/menu targets` 查看任务、分值和进度。菜单按物品、行为与 Bonus 分组分页，保留原任务编号。菜单还提供队伍箱子、路径点、队友传送、定位和随机传送；游戏难度设置为困难。

任务图标优先显示具体对象，支持筛选和管理员 `/menu targetpreview` 预览。可选轻量资源包提供“对象＋动作＋数量”图标，不装也保留原版显示；详见[任务 UI 与资源包](docs/task-ui.md)。

多候选目标支持轮播；资源包生成后运行 `python tools/export_task_audit.py`，可离线打开 `target/task-ui/audit/task-audit.html`，审计动画、全部静态帧及完整描述。

上游新增功能包括按玩家切换中英文（`/language`）、队内聊天和全服喊话（`/shout`）、随机分队确认、极速模式补给、规则书，以及对局自动保存/恢复。恢复数据包含胜利进度、可用积分、Bonus 和行为任务进度；任务定义不兼容时保留备份，不自动恢复。

## 构建与安装

```sh
mvn clean verify
```

构建产物为 `target/BlockRacing-26.2.1.jar`。`mvn verify` 会运行抽样、资源、任务菜单分组、进度持久化及上游逻辑的单元测试；仍需游戏内回归。

自行准备 Paper 26.2 服务端（旧版 1.21.11 不能加载此插件），将插件 JAR 放入其 `plugins/` 目录并启动。服务端 JAR、世界存档、依赖缓存和编译产物不纳入 Git。

首次启动会在 `plugins/BlockRacing/` 生成配置、语言文件和 `Targets.csv`。已有文件不会自动被新的默认资源覆盖；升级时先备份，停服后比较并更新配置、任务库及中英文消息。详细迁移说明见[开发文档](docs/development.md#262-升级注意事项)。

暂停的 `feature/per-team-worlds` 仍基于旧版 1.21.11/JDK 21，不随本次升级。若运行该分支，插件会创建 `world_red*`、`world_blue*` 六个队伍世界，并在胜利后以及下次启动时清理这些队伍世界。请将这些名称专用于本小游戏。`main` 没有这套世界创建与清理逻辑。

## 配置

默认值以 [src/main/resources/config.yml](src/main/resources/config.yml) 为准。游戏运行期间通过菜单调整支持的选项，其他设置停服后修改。

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `block-amount` | 64 | 普通任务目标数量 |
| `max-easy-targets-per-game` | 8 | 1 分任务数量上限 |
| `bonus-score-threshold` | 11 | Bonus 起始分值，可设 11–99 |
| `bonus-target-amount` | 3 | Bonus 数量，可设 1–10 |
| `available-task-amount` | 64 | 同时可做的普通任务数，Bonus 另行显示 |
| `medium-block` / `hard-block` | true / true | 启用 2 分 / 3 分至 Bonus 阈值以下任务 |
| `locate-cost` | 5 | 购买定位权限的可用积分消耗 |
| `random-teleport-cost` | 2 | 随机传送消耗；首次菜单随机传送免费 |
| `max-team-chest-num` / `max-team-waypoint-num` | 8 / 8 | 每队箱子数 / 路径点数 |
| `game-mode` / `speed-mode` | racing / false | 模式 / 极速模式 |

## 常用命令

| 命令 | 用途 |
| --- | --- |
| `/menu`、`/menu targets` | 主菜单、任务列表 |
| `/menu chest [编号]` | 队伍箱子 |
| `/menu waypoints` | 路径点菜单 |
| `/menu locate` | 购买定位权限 |
| `/locatebiome <群系>`、`/locatestructure <结构>` | 使用定位权限 |
| `/menu randomTP` | 随机传送 |
| `/tp <队友>` | 队内传送；原版命令使用 `/minecraft:tp` |
| `/block <red或blue> <编号>` | 查询当前目标 |
| `/language [auto或zh_cn或en_us]` | 按客户端语言自动选择，或手动切换 |
| `/shout <内容>` | 对局中向所有人喊话；普通聊天在队内发送 |
| `/randomteam` | 确认后随机分队 |
| `/restartgame` | 玩家共同确认后关闭服务器；下次启动将备份并重置世界，自动重启需外部脚本 |
| `/sampleblocks [数量]` | 管理员预览普通任务抽样，默认 64，仅在准备阶段可用 |
| `/debug reload` | 玩家管理员重载消息；准备阶段还会重载任务库 |
| `/debug setscore <red或blue> <分数>` | 调整可用积分，不改变胜利进度 |

`/debug start` 仅在独立世界功能分支提供，可用于管理员测试开局。完整命令注册见 [plugin.yml](src/main/resources/plugin.yml)。

## 文档与许可

- [任务库、分数、组合目标与关联降权](docs/targets.md)
- [开发流程、分支维护、部署与手工验证](docs/development.md)
- [语言配置](TranslationTutorial.md)
- [变更记录](CHANGELOG.md)
- [已归档的旧权重曲线](docs/archive/weight-curves.md)

保留上游作者署名，项目采用 [GNU Affero General Public License v3.0](LICENSE)。本 fork 托管于 [DarkPaper2022/BlockRacing](https://github.com/DarkPaper2022/BlockRacing)。

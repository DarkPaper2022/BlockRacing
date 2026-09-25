# BlockRacing 交接与 TODO

最后核对：2026-09-25。本文记录 `main`、客户端子模块和本机游玩实例的当前状态；随时间变化的进程、地址与文件应在操作前重新确认。

## 一句话状态

`main` 已完成 Paper 26.2 升级、积分制与 Bonus、队伍共享进度、目标语义修复、任务图标/审计页，以及可选 Fabric 8×8 Tab 面板，并已部署到本机新世界。服务端能启动，客户端能加入；剩余工作的核心是**多人游戏内验收**，而不是继续堆功能。

## 仓库与分支

- 服务端仓库：`https://github.com/DarkPaper2022/BlockRacing`，当前开发分支为 `main`。
- 客户端仓库：`https://github.com/DarkPaper2022/BlockRacing-Client`，作为 `client-mod/` Git submodule 引入；交接时固定在 `87e6cf0`。
- `main` 是原 `local-3.5-patch` 的后继，已合并清理后的上游 `26.2` 和队伍共享进度。
- `feature/shared-team-progress` 已完整合入 `main`，仅作为开发过程参考；不要在该分支继续开发。
- `audit/target-consistency` 的审计结果已进入 `main`，后续修复也已直接完成在 `main`。
- `feature/per-team-worlds` 有 8 个未合入 `main` 的独有提交，但因多世界性能开销过大而**暂停**。不要合并、升级或删除该分支，除非用户明确恢复该方向。
- `3.0`、`26.2` 是上游快照。`feat/lightkeeper-testing` 已因内容重复删除。
- Git 历史已移除 `server.jar` 等大对象；服务端、世界、构建输出和本地备份不得重新提交。

开始工作前：

```sh
git status --short --branch
git submodule update --init --recursive
git -C client-mod status --short --branch
```

父仓库和子模块是两个独立 Git 仓库。修改客户端时应先在客户端仓库提交并推送，再在父仓库提交新的 submodule 指针。

## 已完成的主要功能

- 每局默认抽取 64 个 1–10 分普通任务，其中 1 分任务最多 8 个；另抽 3 个 11 分及以上的 Bonus。
- 胜利门槛为普通任务总分的 50%，向上取整；Bonus 不进入胜利总分。
- 开局使用困难难度；定位、随机传送、队伍箱、路径点等功能保留。
- 队伍共享目标进度已合入：可累加事件求和，独特实体/物品/进度按集合并集，同一进度由多人重复完成只算一次；装备、效果、等级等要求同一玩家满足。
- 彩色物品总量、牛奶、织布机、炼药锅、堆肥桶、唱片机等目标已改为与文案一致的判定；普通钻石/绿宝石等主世界矿石任务接受对应深层矿石。
- 任务菜单会根据具体目标选择图标；多候选目标用动画轮播，并有可搜索、逐帧查看的 HTML 审计页。
- 可选 Fabric 客户端模组提供 8×8 Tab 面板：普通 Tab 开关目标面板，Shift+Tab 保留原版玩家列表，Bonus 独立显示。服务端同步是只读协议，不接受客户端提交进度或分数。
- 764 条 CSV 目标已完成一次静态一致性审计，其中 682 条启用；审计发现的问题已另行修复。原审计表是历史基线，不应误解为修复后重新跑过的全量审计。

设计和边界详见：

- [任务与抽取规则](targets.md)
- [队伍共享进度](shared-team-progress.md)
- [任务 UI 与资源包](task-ui.md)
- [Tab 目标面板](client-task-board.md)
- [目标审计修复记录](audits/target-consistency-20260906/fixes.md)

## 当前部署快照

运行目录为 `/home/darkpaper/Game/blockracing-draftout`，客户端为 HMCL 的 `26.2` 实例。完整部署记录见 [2026-09-25 部署记录](deployment-20260925.md)。

2026-09-25 最后核对时：

- Paper 26.2 build 129 正在运行，Java 27，参数为 `-Xms2G -Xmx4G -XX:+UseG1GC`。
- 服务端监听双栈通配地址 TCP 25565；`net.ipv6.bindv6only=0`。实际 LAN、ZeroTier 和 IPv6 地址用 `ip -brief address show` 现场查询，不在公开仓库记录临时地址。
- BlockRacing 26.2.1 是唯一启用的外部插件；加载 682 个启用目标。
- 运行配置为 64 个普通目标、最多 8 个一分目标、3 个 Bonus、64 项同时开放、竞速模式、非极速模式。
- `darkpaper2026` 已通过本机客户端进入服务器，证明 Paper、插件、Fabric 客户端和网络基线可共同启动；该事实**不能代替** Tab 面板和多人玩法验收。
- 服务端仍为 `online-mode=false`，只适合受信任的本地或私有网络；不要直接暴露到公网。若要公开运行，应先讨论认证、防火墙、白名单和备份策略。

已部署文件校验值：

| 文件 | SHA-256 |
| --- | --- |
| Paper build 129 `server.jar` | `b1d8f6bfa1b6101fa8e947b53041cb3bdf5540e7b83b6547ca19ba7edefeb083` |
| `BlockRacing-26.2.1.jar` | `0e700fc665eac3db5feddd3969e36689771738fd03126673d337091f69c65eda` |
| `blockracing-client-0.1.0.jar` | `392fb2c0daecb653ced6bd4560ac73464f31981d2e671de59d908fa26e629b68` |
| `BlockRacing-TaskIcons-26.2.zip` | `5d6756662292f66c19c26f52414de1691eae52e975449774d363f969ced317b7` |

不要依据本文中的旧 PID 操作进程。先用以下命令确认准确 PID 和命令行；优先在服务端控制台输入 `stop`。若控制台不可用，再在确认目标后向单个 PID 发送 `TERM`，不要使用宽泛的 `pkill`。

```sh
ps -eo pid,lstart,args | rg '[j]ava .*server\.jar'
ss -ltnp '( sport = :25565 )'
```

## 备份与恢复

- 仓库整理前的完整工作目录：`.local-backups/20260906-171230/repository.tar.gz`。
- 历史重写前 refs：`.local-backups/20260906-171230/all-refs-before-cleanup.bundle`。
- 部署前运行服备份：`/home/darkpaper/Game/blockracing-draftout/deploy-backups/20260925-before-26.2/server-runtime.tar.gz`。
- 同目录还保存旧客户端配置、模组、资源包、旧插件和 Paper remap 缓存。
- 旧世界因 Paper 目录结构迁移冲突被整体移到 `conflicting-worlds-after-first-start/`，没有删除。用户已确认旧世界不重要；当前运行服使用全新 26.2 世界。

恢复旧世界前必须正常停服，并恢复与旧 Paper 版本匹配的一整套世界目录；不要把 CraftBukkit 旧维度布局和 Paper 26.2 新布局混用。所有 `.local-backups/` 与运行目录内容均为本机资料，不上传 GitHub。

## TODO

### P0：下一次开发优先做

1. **实机确认 Tab 面板。** 在当前 HMCL 26.2 实例中验证普通 Tab 打开/关闭 8×8 面板、Shift+Tab 玩家列表、Esc、滚轮/方向键、Bonus、不同 GUI 缩放和资源包动画。当前只确认客户端成功入服，尚无明确记录证明面板每项交互均通过。
2. **做两人、两队共享进度验收。** 至少覆盖伤害小数求和、同一成就重复完成只计一次、30 个不同进度的并集、不同生物/唱片去重、跨背包和队伍箱分配、离线重登与正常重启恢复，以及红蓝队隔离。
3. **实测语义修复过的动作目标。** 覆盖织布机普通/Shift/快捷栏/丢弃取出、牛奶实际清除效果、炼药锅清洗、堆肥填满和骨粉拾取、唱片实际播放、深层钻石/绿宝石矿石，并检查取消事件不能误记。
4. **完整跑一局结算。** 检查 64/8/3 抽样约束、Bonus 不进入胜利总分、达到 `ceil(普通总分/2)` 只结算一次、双方共享目标被一方完成后正确移除、正常停服重启能恢复未完成对局。

把实测结果按日期补回本文或单独建立验收记录；失败时同时保存服务端 `logs/latest.log` 和客户端实例的 `logs/latest.log`。

### P1：已知设计问题与工程改进

1. **确认“完成 N 个进度”的时间语义。** 当前会导入玩家开局前已完成的有效原版进度；管理员之后撤销也不会从本局账本扣除。去重逻辑已有测试且未复现重复累计，但这可能不同于“仅统计本局新获得”的玩家直觉。先由用户确认规则，再改实现和文案。
2. **修订全量审计基线。** 原 764 项审计表保留发现问题时的历史结论；可在完成 P0 实测后，重新基于当前 `main` 生成一份修复后的全量报告，避免读者在 summary 与 fixes 之间交叉判断。
3. **补集成测试。** 现有自动测试覆盖聚合和协议契约，但真实 Paper 事件时序、InventoryClick 分支、延后一 tick 的状态检查、重启恢复仍主要依赖手工回归。优先为 P0 中实际发现的问题建立可复现测试。
4. **改善客户端可配置性。** 第一版固定使用普通 Tab；若与其他模组或玩家习惯冲突，再增加按键配置。协议变更必须同时更新服务端与客户端并保留版本拒绝逻辑。
5. **确认公开联机方案。** 当前离线认证模式只能用于可信私网。若需要公网 IPv6 或端口转发，先启用可靠认证/白名单并从外部网络验证防火墙和可达性。

### P2：暂缓或维护性事项

- `feature/per-team-worlds` 继续冻结。其独立世界功能需要先解决性能与生命周期成本，且仍基于旧版 1.21.11/JDK 21；不得顺手合入 `main`。
- `feature/shared-team-progress` 与 `audit/target-consistency` 已被 `main` 覆盖。确认不再需要过程分支后可以删除，但不是当前优先事项。
- 后续同步上游时，从清理后的提交合并并检查大对象；不要直接合入可能重新带回 `server.jar` 的旧历史。
- 任务定义变更需同步仓库资源与运行服 `plugins/BlockRacing/Targets.csv`。插件不会覆盖既有配置；进行中的对局也不应替换任务表。

## 构建、检查与部署

服务端：

```sh
JAVA_HOME=/path/to/jdk-25-or-newer mvn clean verify
```

客户端：

```sh
cd client-mod
JAVA_HOME=/path/to/jdk-25-or-newer ./gradlew build
```

双端协议变更时，先由父仓库 Maven 测试生成 `target/task-board-contract.bin`，再按 [Tab 面板文档](client-task-board.md) 用 `-PserverFixture=...` 构建客户端。不要修改系统默认 Java；本机部署运行时当前使用 Java 27。

部署顺序：

1. 确认源码分支、父仓库与 submodule 均干净，测试通过。
2. 正常停服，备份整个运行目录；不要用 `/restartgame` 代替普通停服，因为该命令会安排下次启动重置世界。
3. 将产物先放入运行目录的新 `builds/<日期或功能>/` 子目录并记录校验值。
4. 备份旧插件后替换 JAR；比较并按需更新 `Targets.csv`、`config.yml`、`lang.yml`，不要假设默认资源会覆盖已有文件。
5. 启动后检查 Paper/插件版本、目标加载数量、错误日志和监听端口，再做客户端连接与最小玩法回归。
6. 提交源码和文档，推送对应仓库；不得提交 Token、IP、世界、日志、服务端 JAR 或本地备份。

## 交接完成标准

下一位开发者如果只处理近期工作，应以这些结果为完成标准：P0 四项有可追溯的实测记录；发现的问题有回归测试或明确的复现步骤；父仓库和客户端仓库状态干净且已推送；部署产物与源码提交能用校验值对应；运行服能正常重启和恢复，且没有把暂停的独立世界代码带入 `main`。

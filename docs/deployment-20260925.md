# 2026-09-25 本地游玩部署记录

目标目录：`/home/darkpaper/Game/blockracing-draftout`；客户端实例：HMCL `26.2`。

## 已部署

- Paper 26.2 build 129（STABLE，官方 SHA-256 `b1d8f6bfa1b6101fa8e947b53041cb3bdf5540e7b83b6547ca19ba7edefeb083`）。
- BlockRacing 26.2.1；仅启用这一项外部插件，加载 682 个启用目标。
- 规则配置：64 个普通目标、最多 8 个一分目标、3 个 Bonus、64 项同时开放，竞速模式，非极速模式。
- HMCL 26.2：Fabric Loader 0.19.3、Fabric API 0.154.0；安装 `blockracing-client-0.1.0.jar` 并启用 `BlockRacing-TaskIcons-26.2.zip`。
- 启动脚本固定使用本机 Java 27，内存 `-Xms2G -Xmx4G`，不再在每次启动前运行旧 `clean.py`。

服务端最终启动日志确认 Paper build 129、BlockRacing 26.2.1、`Done`，并监听 TCP 25565。客户端模组基线在真实已安装的 Loader/API 版本上重新编译，客户端 11 项测试通过。

## 备份与迁移

部署前完整运行时备份：

`/home/darkpaper/Game/blockracing-draftout/deploy-backups/20260925-before-26.2/server-runtime.tar.gz`

客户端原始 `options.txt`、`mods/`、`resourcepacks/` 也在同一目录。旧的 BlockRacing 3.5、DragonOneHP、JustEnoughRecipes 移入 `disabled-plugins/`，没有删除。

旧服同时存在 CraftBukkit 独立维度目录与 Paper 26.2 新维度目录，build 129 为避免覆盖而拒绝迁移。用户确认旧世界不重要后，全部 `world*` 目录被移动到：

`/home/darkpaper/Game/blockracing-draftout/deploy-backups/20260925-before-26.2/conflicting-worlds-after-first-start/`

当前服务端使用全新生成的 26.2 世界。恢复旧世界前必须先停服，并按对应旧 Paper 版本恢复整套目录；不要把两种目录结构混合回当前世界。

## 使用

在 HMCL 启动 `26.2`，连接 `localhost:25565`（同机）或主机的局域网/Tailscale 地址。进入支持协议的服务器后，普通 Tab 打开目标面板，Shift+Tab 保留原版玩家列表。

服务端当前保留 `online-mode=false`，沿用部署前配置；只适合受信任的本地或私有网络，不应直接暴露到公网。

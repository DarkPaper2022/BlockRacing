# Tab 目标面板（Fabric 客户端）

[首页](../README.md) · [现有任务 UI](task-ui.md) · [客户端源码](https://github.com/DarkPaper2022/BlockRacing-Client)

## 布局与交互

`client-mod/` 是独立 Git 子模块，服务端仍为本仓库的 Paper 插件。客户端为 Minecraft 26.2 / Fabric，Java 25+。无模组客户端继续使用 `/menu targets`，不强制安装。

- 在支持协议的服务器上，普通 Tab 单次按下打开面板，再按 Tab 或 Esc 关闭。长按不反复开关。Shift+Tab 在游戏画面中传给原版玩家列表；聊天、背包等其他界面的 Tab 不拦截。不会修改 `options.txt` 或玩家已有键位；第一版固定拦截普通 Tab。
- 中央固定 **8×8**，默认 64 个普通目标。图标、两行目标名、分数、进度/状态常驻；悬停显示完整名称与 requirement。方向键选择，滚轮或 PgUp/PgDn 翻页。
- 目标按本局原始顺序固定位置，不因结算而重新排序。格子不足补空位，超过 64 个普通目标分页，绝不截掉第 65 项。
- 下方独立 **3 个 Bonus**，不占普通任务格，不计胜利分；配置更多 Bonus 时按 B 翻页。
- 顶部显示本队胜利进度、胜利门槛与普通目标总分。未参赛、尚未抽取目标、同步失败与超过 4 秒未更新均有单独提示。
- `active` 为当前允许完成；`queued` 为本局已抽取但当前未开放。UI 不改变 `availableTaskAmount` 或任何判定规则。
- `resolved` 显示“已结算”：当前服务器在任一队完成后会从双方移除目标，旧存档没有逐目标归属，因此不能据此推断是本队完成。第一版不伪造红/蓝归属或历史进度。
- 直接使用客户端 ItemStack 渲染。可选现有任务资源包继续支持组合图标和轮播，未安装时回退原版物品；望远镜保持原版模型。客户端不内置 Mojang 素材。
- 面板按窗口/GUI 缩放等比适配，不暂停多人游戏；打开时鼠标用于面板，玩家仍可能受伤。

## 同步与性能边界

使用 Paper plugin messaging 与 Fabric 自定义 payload，协议版本 1：

| 方向 / 频道 | 内容 |
| --- | --- |
| C→S `blockracing:board_request` | 严格一个字节，`1` 订阅/续租，`0` 取消 |
| S→C `blockracing:board_v1` | 原始 GZIP 字节，解压后为 UTF-8 JSON，无额外字符串长度前缀 |

客户端每 5 秒续租，服务端 12 秒过期。关闭面板取消订阅，断线清理。服务端每秒最多生成一次每队/每语言快照，只为已订阅且注册接收频道的客户端工作；只计算当前可完成的行为目标进度，不扫描全任务库，不导入成就，也不修改物品栏。客户端不得提交队伍、目标完成状态或分数；这些信息只由服务端产生。

包体最多 30,000 字节；解压 JSON 最多 512 KiB；最多 512 项目标。超限返回明确错误，不静默丢弃目标。客户端校验协议版本、重复 ID、数量、状态和分母，限制解压大小；断服清空快照，数据错误不沿用“实时”标识。通信不包含对手背包、位置或进度。

字段定义见客户端 `BoardState` 与服务端 `TaskBoardBridge`。修改协议需同时更新双端并保留版本校验。

## 开发与预览

```sh
git submodule update --init --recursive
JAVA_HOME=/path/to/jdk-25-or-newer mvn verify
cd client-mod
JAVA_HOME=/path/to/jdk-25-or-newer ./gradlew build
```

客户端依赖固定版本，Gradle 分发有 SHA-256 校验。首次构建需联网；本次构建缓存留在父仓库 `.local-backups/gradle-client/`，没有安装全局工具。

UI 设计预览使用真实 CSV 与现有任务图标，但队伍状态是示例，**不是实机截图**。已有图标资源包产物后运行：

```sh
python tools/export_board_preview.py --client-jar /path/to/26.2.jar
```

输出 `target/task-ui/board-preview.html`，离线可打开，支持 Tab 切换与悬停；`tools/test_board_preview.cjs` 验证布局/交互并生成 PNG。HTML 对普通方块采用原版纹理预览，游戏里由客户端渲染实际物品模型。

构建应暂存到约定部署目录的新 `builds/` 子目录；不得直接覆盖现有 1.21.11 服务器。实机验收需要配套 Paper 26.2 服务端、26.2 Fabric 客户端及 Fabric API，重点检查按键拦截、频道注册握手、断线/换队/新局、各 GUI 缩放与资源包动画。

开发接口参考：[Fabric 26.2 网络通信](https://docs.fabricmc.net/develop/networking)、[Fabric Loom](https://docs.fabricmc.net/develop/loom/)。具体 Minecraft 渲染与输入签名以本机官方 26.2 客户端类为准。

## 本次验证（2026-09-06）

- 服务端 `mvn verify`：61 项测试通过，包括频道请求约束、压缩/解压协议、尺寸上限、互斥目标状态、非参赛者隔离和准备阶段不泄露旧任务。
- 客户端 `gradle build`：编译成功，11 项测试通过，包括布局、Tab/Shift/释放按键策略、超大解压数据拒绝、版本与重复 ID 校验，以及读取 **Paper 实际编码器** 输出的契约测试数据。
- HTML 预览浏览器验证：64+3 格、图标加载、悬停、Tab 开关、4 种窗口宽度、离线无外部请求；PNG 保存为 `target/task-ui/board-preview.png`。
- 产物暂存在约定部署目录的 `builds/task-board-20260906/`。**未启动配套游戏联调，未替换旧版测试服。**

复现双端契约测试：先运行父仓库 Maven 测试生成 `target/task-board-contract.bin`，再在客户端构建时添加 `-PserverFixture=/absolute/path/to/parent/target/task-board-contract.bin`。独立构建子模块时此可选测试跳过，其余测试照常运行。

# 目标图标与任务菜单

[首页](../README.md) · [开发说明](development.md)

## 使用

- 游戏中 `/menu targets`：默认显示当前队伍的可做任务。底部漏斗依次切换全部、物品、行为、Bonus，翻页保留筛选；任务编号始终对应 `/block` 的原编号。
- 准备阶段，管理员使用 `/menu targetpreview` 预览整个启用任务库，默认打开行为目标。它不抽样、不重载 CSV、不改变对局或任务进度。
- 普通物品显示自身；158 个启用的组合/行为目标使用具体工具、动物、矿石、工作站、进度关联物等图标，不再把所有收集画成箱子、击杀画成剑。
- 图标叠放数表示**需求数量，不是任务分数**。仅对不带耐久且数量为 2–64 的展示物品使用叠放；100、200、400 和时间单位不会假装成 64。武器与盔甲不会为了显示数字而修改耐久规则。
- Bonus 发光并在完整标题中加 `★`，可独立筛选。悬停直接显示完整目标、分数和个人进度，无需点击进入二级详情。附魔目标也可能发光，不应只凭发光判断 Bonus。
- 点击底部页码刷新；无常驻刷新任务、地图渲染或额外实体。此改动不影响任务判定、分数、抽样及暂停的 per-team-worlds 分支。

原版箱子菜单不能在每个格子下常驻完整文字。不开资源包时，具体对象和部分数量可见，但同一对象的不同动作仍需看标题；不要将原版回退宣传为每个目标都无需悬停。

## 可选资源包

`BlockRacing-TaskIcons-26.2.zip` 是 **26.2 客户端**资源包，无需客户端模组。放进客户端的 `resourcepacks/` 并启用；服务器自动分发可在后续配置，本次没有修改任何客户端目录或服务器分发设置。

- 左上：动作符号（镐＝挖掘、交叉剑＝击杀、心＝繁殖、骷髅＝死亡等），形状与颜色共同区分。
- 中央：具体对象；套装/集合用多个物品组成图标。凋零使用三头剪影，工作站使用其方块外观。
- 右上：精确数量；`ANY` 表示任一、`ALL` 表示全套、`5M` 表示 5 分钟、`MAX` 表示满级/完整附魔。
- 金色角标：当前配置下属于 Bonus，动态读取运行时分类，不把默认 11 分阈值烘焙到图片中。

每个目标使用 `custom_model_data.strings[0] = blockracing:task/<id>`；`flags[0]` 选择 Bonus 变体。资源包只匹配这些命名空间键，保留 26.2 原版物品模型和 tint 等属性作为 fallback。**没有安装资源包也不会因为自定义 item_model 引用而出现紫黑缺失贴图**，而是正常显示原版对象。

资源包覆盖了 131 种基础物品的模型选择文件；其他修改相同文件的资源包可能发生优先级冲突，需要合并选择规则。生成时同时提供全部目标和精选目标的 PNG 预览，方便人工检查。预览是离线图标图集，不是游戏截图；较低 GUI 缩放下动作小符号可能需要悬停确认。

## 维护与构建

图标数据在 [target-icons.json](../src/main/resources/target-icons.json)，图标策略与离线绘制在 [build_task_icons.py](../tools/build_task_icons.py)。它们只负责展示，不参与游戏规则。

新增/修改 CSV 后，运行 `python tools/build_task_icons.py --catalog` 检查并更新 JSON；未覆盖的目标类型使生成失败，避免悄悄回退为无意义图标。服务端自行修改 requirement 时，插件检测定义不匹配并回退原图标，不使用过期数量/对象。

先构建插件，再生成资源包（`mvn clean` 会清空 `target/`）：

```sh
JAVA_HOME=/usr/lib/jvm/java-26-openjdk mvn -o clean verify
python tools/build_task_icons.py --client-jar /path/to/26.2.jar
python tools/test_task_icons.py --client-jar /path/to/26.2.jar
```

资源包构建需要 Python 3 与 Pillow，以及自己拥有的 Minecraft 26.2 客户端 JAR。脚本不下载客户端、访问 Gemini、读取凭据或修改全局环境。产物：

- `target/BlockRacing-26.2.1.jar`
- `target/task-ui/BlockRacing-TaskIcons-26.2.zip`
- `target/task-ui/preview.png`、`all-goals.png`

生成器使用固定 ZIP 时间戳，可重复构建。生成图片组合了本机 Minecraft 素材，版权属于 Mojang/Microsoft，不作为本项目 AGPL 原创图片提交到 Git；自绘动作符号和生成代码遵循仓库许可。没有复制 Draftout 官网图标到交付资源包，也没有使用 Gemini 服务。

## 设计参考与验证

参考 [Draftout 官方目标库](https://draftoutmc.com/wiki) 的具体对象/组合物品展示思路，自行实现像素组合。模型选择机制依据 [Minecraft 官方自定义模型数据说明](https://www.minecraft.net/zh-hans/article/minecraft-snapshot-24w45a)，版本使用本机官方 26.2 客户端的 `version.json`（资源格式 88.0）。

2026-09-06：32 项 Java 测试通过；5 项离线资源包测试通过，覆盖目录与 CSV 一致性、完整图标覆盖、原版 fallback 保留、Bonus 模型引用、透明图片和易混目标区别。测试日志位于本机 `.local-backups/task-ui-20260906/`。

实机待验：Paper 26.2 启动、`/menu targetpreview` 翻页与筛选、原版/启用资源包两种显示、叠放数、Bonus 动态阈值、中英文、拒绝或移除资源包、其他包叠加。现有 1.21.11 测试服未替换；本次产物暂存在约定部署目录的 `builds/task-ui-20260906/`。

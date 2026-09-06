# 任务库与抽取规则

[返回首页](../README.md)

实现入口为 [Block.java](../src/main/java/top/lqsnow/blockracing/managers/Block.java) 和 [Goal.java](../src/main/java/top/lqsnow/blockracing/managers/Goal.java)。内置任务库为 [Targets.csv](../src/main/resources/Targets.csv)，运行时读取服务器 `plugins/BlockRacing/Targets.csv`。

## CSV 格式

```csv
id,type,score,display_name,requirement,中文显示
SLIME_BLOCK,block,2,,,黏液块
KILL_WITHER,goal,10,Kill Wither,kill:WITHER,击杀凋零
OBTAIN_ANY_HORSE_ARMOR,goal,2,Obtain any Horse Armor,"item-unique:1:LEATHER_HORSE_ARMOR,IRON_HORSE_ARMOR,GOLDEN_HORSE_ARMOR,DIAMOND_HORSE_ARMOR",获得任一一种马铠
```

- 获取单个具体物品使用 `block`，ID 必须是目标服务器 API 的 `Material` 枚举名，如 `PHANTOM_MEMBRANE`。
- 多物品、任选一种、击杀、进度及其他行为使用 `goal`，ID 必须唯一。`display_name` 是非中文目标名称，`requirement` 描述判定方式。
- `score=-1` 禁用条目；启用任务使用正整数。保持 ID 唯一，重复 ID 会覆盖先前定义。
- 含逗号的字段需加双引号，字段中的双引号需写为两个双引号。每条任务写在一个物理行上。
- 中文名称放在第六列。不要把显示名当作 Material 或实体标识符。

常见 requirement：

| 格式 | 含义 |
| --- | --- |
| `item:FURNACE,BLAST_FURNACE,SMOKER` | 取得所列全部物品 |
| `item:ARROW*64` | 取得指定数量 |
| `item-unique:5:<Material列表>` | 取得列表中至少 5 种不同物品 |
| `kill:WITHER` | 击杀指定实体 |
| `advancement:adventure/revaulting` | 完成指定进度 |
| `break:<Material>` | 追踪破坏方块事件 |

其他行为判定以 `Goal.parseRequirement(...)` 为准。CSV 能解析并不证明任务在当前版本的生存玩法中可完成，新任务需要游戏内验证。

## 普通任务和 Bonus

默认普通分值范围为 1–10，Bonus 为 11 及以上。`bonus-score-threshold` 改变二者边界；`bonus-target-amount` 控制 Bonus 数量。

普通任务抽取过程：

1. 建立 1 分候选池及其他普通任务候选池。2 分受 `medium-block` 控制，3 分至 Bonus 阈值以下受 `hard-block` 控制。
2. 先抽取 `min(简单上限, 1分候选数, 普通任务数量)` 个 1 分任务。
3. 从其他普通任务中补足数量，抽取不重复。
4. 打乱顺序，再将单独抽取的 Bonus 加入双方共享列表。

因此，默认池足够时会抽到恰好 8 个简单任务。若其他普通任务不足，不会突破简单上限补齐，最终总数可能少于 `block-amount`。Bonus 池不足时也只抽取现有数量。

获胜门槛为 `ceil(本局普通任务总分 / 2)`，Bonus 不计入该总分。当前目标菜单限制普通任务同时可见/可做的数量，但所有尚未完成的本局 Bonus 会额外显示。

## 木种与类别降权

普通任务抽取时，候选初始权重为 1。抽到某个木头相关方块后，后续候选只要命中其木种、具体类别或类别组，权重变为 0.1；多次命中不会继续乘以 0.1。

例如抽到 `DARK_OAK_SIGN` 后，其他 `DARK_OAK_*`、其他木种的 `*_SIGN`，以及同属告示牌组的 `*_HANGING_SIGN` 都会降权。

维护项位于 `Block.java`：

- `RELATED_WOOD_SERIES_WEIGHT_MULTIPLIER`：默认 0.1。
- `WOOD_FAMILIES`：木种前缀，较长前缀须放在 `OAK` 等短前缀之前。
- `WOOD_CATEGORY_GROUPS`：目前合并告示牌/悬挂式告示牌，以及树苗/胎生苗/菌类。
- `STRIPPED_` 前缀会先剥离，再识别木种和类别。

同一次抽样的 1 分阶段和其他普通任务阶段共享已抽中标签；Bonus 单独均匀抽取，不使用这套降权。旧的简单/普通/困难动态权重曲线已移除，其文档仅作[历史记录](archive/weight-curves.md)。

## 修改与验证

停服后备份并修改服务器的 CSV；仓库默认值另在 `src/main/resources/Targets.csv` 维护。修改仓库资源不会自动覆盖服务器已有文件。

准备阶段可使用 `/debug reload` 重载任务库，再用 `/sampleblocks 64` 预览普通任务样本。样本不包含 Bonus，也不是下一局的固定列表；该命令会重载定义，避免在游戏进行中用它调参。

检查任务数量、重复 ID、简单上限、分值范围以及 Material/进度 ID。新增 goal 至少实际完成一次，并检查双方互斥、普通分数和 Bonus 奖励是否正确。完整回归建议见[开发文档](development.md)。

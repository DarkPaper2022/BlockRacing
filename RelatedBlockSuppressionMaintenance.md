# 关联方块降权维护说明

这份文档说明如何维护“出现某个木头相关方块后，关联方块出现概率降到原来的 10%”这套逻辑。

## 核心文件

逻辑定义在：

- `/home/darkpaper/Game/blockeracing-src/src/main/java/top/lqsnow/blockracing/managers/Block.java`

核心入口是：

- `generateBlocks()`
- `selectWeightedBlockFromList(...)`
- `calculateRelatedBlockSelectionWeight(...)`
- `getRelatedBlockTags(...)`

## 当前规则

当前实现不是只特判 `DARK_OAK_SIGN（深色橡木告示牌）`，而是统一做“木种标签 + 类别标签”的降权。

当一个木头相关方块已经进入目标列表后，后续候选方块只要命中下面任意一种关联，就会降到原权重的 `10%`：

- 同木种
- 同具体类别
- 同类别组

### 1. 同木种

例如已经抽到：

- `DARK_OAK_SIGN（深色橡木告示牌）`

那么所有 `DARK_OAK_*` 都会降权，包括：

- `DARK_OAK_PLANKS（深色橡木木板）`
- `DARK_OAK_SAPLING（深色橡木树苗）`
- `DARK_OAK_TRAPDOOR（深色橡木活板门）`

### 2. 同具体类别

例如已经抽到：

- `DARK_OAK_PLANKS（深色橡木木板）`

那么所有 `*_PLANKS` 都会降权，包括：

- `OAK_PLANKS（橡木木板）`
- `BIRCH_PLANKS（白桦木板）`
- `CRIMSON_PLANKS（绯红木板）`

同理：

- `*_SAPLING`
- `*_LOG`
- `*_WOOD`
- `*_DOOR`
- `*_FENCE`
- `*_BUTTON`
- `*_STAIRS`

这些木头类别都会按同样方式处理。

### 3. 同类别组

有些类别名字不同，但玩法上应该视为同一类，所以做了额外分组。

当前分组有：

- `SIGN_SERIES`
  - `SIGN`
  - `HANGING_SIGN`
- `SAPLING_SERIES`
  - `SAPLING`
  - `PROPAGULE`
  - `FUNGUS`

所以：

- 抽到 `DARK_OAK_SIGN（深色橡木告示牌）` 后，`PALE_OAK_HANGING_SIGN（苍白橡木悬挂式告示牌）` 也会降权
- 抽到 `MANGROVE_PROPAGULE（红树胎生苗）` 后，`OAK_SAPLING（橡树树苗）` 也会降权
- 抽到 `CRIMSON_FUNGUS（绯红菌）` 后，`BIRCH_SAPLING（白桦树苗）` 也会降权

## 当前可调常量

在 `Block.java` 顶部维护：

- `RELATED_WOOD_SERIES_WEIGHT_MULTIPLIER`
- `WOOD_FAMILIES`
- `WOOD_CATEGORY_GROUPS`

### `RELATED_WOOD_SERIES_WEIGHT_MULTIPLIER`

当前值：

- `0.1D`

含义：

- 命中关联规则后，候选方块权重变为原来的 `10%`

例子：

- 改成 `0.25D`：只降低到 `25%`
- 改成 `0.05D`：降低到 `5%`

### `WOOD_FAMILIES`

当前识别的木种有：

- `DARK_OAK`
- `PALE_OAK`
- `MANGROVE`
- `CRIMSON`
- `SPRUCE`
- `JUNGLE`
- `CHERRY`
- `BAMBOO`
- `WARPED`
- `BIRCH`
- `ACACIA`
- `OAK`

如果以后 Mojang 新增新的木头体系，或者你在清单里加入了新的同类前缀，要把它补进这里。

注意顺序有意义：

- `DARK_OAK` 必须放在 `OAK` 前面
- `PALE_OAK` 也必须放在 `OAK` 前面

否则前缀匹配会把 `DARK_OAK_*` 错认成 `OAK_*`

### `WOOD_CATEGORY_GROUPS`

这里维护“跨名字但同类”的组合。

如果你以后还想让下面这些也视为同一类，可以在这里继续加组：

- 原木和木头合并
- 树叶和树苗合并
- 门和活板门合并

## 剥皮木头的处理

当前实现会先把 `STRIPPED_` 去掉再识别。

所以：

- `STRIPPED_DARK_OAK_LOG（去皮深色橡木原木）`

会按：

- 木种：`DARK_OAK`
- 类别：`LOG`

参与联动降权。

这意味着：

- `DARK_OAK_LOG（深色橡木原木）`
- `STRIPPED_DARK_OAK_LOG（去皮深色橡木原木）`

会被视为同木种、同类别链条的一部分。

## 生效范围

当前这套降权只影响“某个难度池内部选哪个方块”。

它不会改变：

- `简单 / 普通 / 困难 / 染色 / 末地` 这几个难度池本身的权重

也就是说：

- 先用 `calculate*Weight()` 决定抽哪个池
- 再在池内按这套“关联降权规则”选具体方块

## 推荐维护流程

1. 修改 `Block.java`
2. 执行 `mvn -DskipTests package`
3. 替换服务器里的 `plugins/BlockRacing-3.5.jar`
4. 运行 `start.sh`
5. 执行 `brcheck reload`
6. 如需验证联动效果，用脚本或 `jshell` 连续模拟，检查目标样本里同木种/同类别是否明显下降

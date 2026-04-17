# 权重曲线维护说明

这份文档说明如何维护 `简单`、`普通`、`困难` 等方块池在总列表里的抽取权重。

## 核心文件

权重曲线定义在：

- `/home/darkpaper/Game/blockeracing-src/src/main/java/top/lqsnow/blockracing/managers/Block.java`

真正控制比例的是以下方法：

- `calculateEasyBlocksWeight(float progress)`
- `calculateMediumBlocksWeight(float progress)`
- `calculateHardBlocksWeight(float progress)`
- `calculateDyedBlocksWeight(float progress)`
- `calculateEndBlocksWeight(float progress)`

其中 `progress` 的计算方式是：

- `当前目标序号 / block-amount`

例如 `block-amount: 64` 时：

- 第 1 个目标接近 `0.0`
- 中盘大约在 `0.5`
- 后期大约在 `0.75`
- 终盘接近 `1.0`

## 当前实现

当前版本把主要可调参数提成了 `Block.java` 顶部常量：

- `EASY_DISABLE_PROGRESS`
- `EASY_START_WEIGHT`
- `MEDIUM_EARLY_START_WEIGHT`
- `MEDIUM_MID_WEIGHT`
- `MEDIUM_LATE_WEIGHT`
- `MEDIUM_WEIGHT_TURNING_POINT`
- `HARD_EARLY_START_WEIGHT`
- `HARD_MID_WEIGHT`
- `HARD_LATE_WEIGHT`
- `HARD_WEIGHT_TURNING_POINT`

当前逻辑是：

- `简单`：从 `90` 线性下降，并在 `progress >= 0.68` 时直接停出
- `普通`：前 `35%` 从 `30 -> 70`，后 `65%` 从 `70 -> 84`
- `困难`：前 `50%` 从 `1 -> 30`，后 `50%` 从 `30 -> 90`

## 如何调整

### 1. 调整“后期什么时候不再出简单”

改：

- `EASY_DISABLE_PROGRESS`

例子：

- 改成 `0.60f`：更早停出 `简单`
- 改成 `0.85f`：更晚停出 `简单`

### 2. 调整“普通”在前中后期的强度

改：

- `MEDIUM_EARLY_START_WEIGHT`
- `MEDIUM_MID_WEIGHT`
- `MEDIUM_LATE_WEIGHT`
- `MEDIUM_WEIGHT_TURNING_POINT`

例子：

- 提高 `MEDIUM_LATE_WEIGHT`：后期更容易出 `普通`
- 降低 `MEDIUM_WEIGHT_TURNING_POINT`：更早进入后半段曲线

### 3. 调整“困难”在后期的压强

改：

- `HARD_EARLY_START_WEIGHT`
- `HARD_MID_WEIGHT`
- `HARD_LATE_WEIGHT`
- `HARD_WEIGHT_TURNING_POINT`

例子：

- 提高 `HARD_LATE_WEIGHT`：终盘更偏向 `困难`
- 提高 `HARD_MID_WEIGHT`：中盘开始就更常出 `困难`

## 调整原则

### 如果你想“前期更平滑”

- 不要把 `HARD_EARLY_START_WEIGHT` 拉太高
- 不要把 `MEDIUM_WEIGHT_TURNING_POINT` 设得太小

### 如果你想“后期明显加压”

- 把 `EASY_DISABLE_PROGRESS` 维持在 `0.60f ~ 0.75f`
- 提高 `MEDIUM_LATE_WEIGHT`
- 明显提高 `HARD_LATE_WEIGHT`

### 如果你想“中盘就开始转难”

- 提高 `HARD_MID_WEIGHT`
- 降低 `HARD_WEIGHT_TURNING_POINT`

## 列表数量和权重的区别

要区分两件事：

- `EasyBlocks.txt`、`MediumBlocks.txt`、`HardBlocks.txt` 里的条目数
- `calculate*Weight()` 的权重

条目数不会直接决定 `简单 / 普通 / 困难` 的整体占比。

条目数只会影响：

- 某个池被抽中后，池内有哪些方块可能出现
- 某个池在被抽空后是否还能继续参与抽取

真正决定总体占比的是 `calculate*Weight()`。

## 推荐维护流程

1. 修改 `Block.java` 里的权重常量或公式
2. 执行 `mvn -DskipTests package`
3. 用构建产物替换服务器里的 `plugins/BlockRacing-3.5.jar`
4. 运行 `start.sh`
5. 执行 `brcheck reload`
6. 如果需要，再用 `jshell` 或 `javap` 验证关键进度点的返回值

## 这台服务器当前验证过的关键点

当前权重函数对应的结果是：

- `progress = 0.00`：`easy=90`，`medium=30`，`hard=1`
- `progress = 0.35`：`easy=43`，`medium=70`，`hard=21`
- `progress = 0.68`：`easy=0`，`medium=77`，`hard=51`
- `progress = 0.90`：`easy=0`，`medium=81`，`hard=78`

按 `block-amount: 64`、`Easy/Medium/Hard` 三池开启、当前清单内容不变的前提做 10000 次模拟，平均结果大约是：

- `简单`：`15.718 / 64`，约 `24.56%`
- `普通`：`31.672 / 64`，约 `49.49%`
- `困难`：`16.610 / 64`，约 `25.95%`

同一套参数下，示例单次 64 格实抽结果为：

- `简单`：`17`
- `普通`：`35`
- `困难`：`12`

这意味着当前服的 `简单` 已经明显早于之前版本下滑，`普通` 成为主导档位，而 `困难` 在后半局会继续追上来。

# 逐目标一致性审计报告 (Target Consistency Audit)

> 本报告保留审计时的基线结论；main 后续修复和累计进度复查见 [修复记录](fixes.md)。

- **基线提交 (Commit SHA)**: `b6efc020ab329926a389a193374e2b4269410570`
- **审计环境**: Paper 26.2 API (`io.papermc.paper:paper-api:26.2.build.84-stable`), Java 26
- **审计时间**: 2026-09-06T21:30:18+08:00 开始，复核修订会话于 2026-09-06T21:49:00+08:00 结束（以本地进程日志为准）。
- **执行与复核**: 独立 `agy` 进程完成逐行初审及更正；主代理校验 764 行身份/顺序/字段/结论覆盖，并复核问题项与引用方法。审计与复核均未修改玩法代码或任务规则。
- **审计范围**: `src/main/resources/Targets.csv` 全量 764 个目标（第 2 ~ 765 行，包含 `score=-1` 禁用目标）
- **审计性质声明**: 本次审计为基于静态源码、配置文件、Paper 26.2 API 结构与官方 26.2 客户端字节码的**静态一致性审计**，不代表多人联机实服验收。结论中的 `consistent` 表示本次静态检查中未发现中文显示、Requirement 与代码逻辑存在结构性矛盾，不构成无任何潜在运行时 bug 的绝对承诺。

---

## 1. 审计统计概览

| 统计指标 | 数量 | 占比 / 说明 |
| :--- | :--- | :--- |
| **目标总数 (Total Targets)** | **764** | 完整覆盖 `Targets.csv` 所有数据行（100% 审计覆盖） |
| **启用目标数 (Enabled Targets)** | **682** | `score != -1` |
| **禁用目标数 (Disabled Targets)** | **82** | `score == -1`（已全量审计，标明禁用状态） |
| **目标类型 - 物品方块 (block)** | **597** | 依赖 Material 枚举与背包/队伍箱快照轮询 |
| **目标类型 - 特殊行为 (goal)** | **167** | 包含事件监听、状态判定、去重统计、伤害求和与进度判定 |
| **一致 (consistent)** | **755** | 中文显示、requirement、底层代码逻辑与队伍账本/快照语义静态一致 |
| **不一致 (mismatch)** | **7** | 包含文案与物品限制不符（2项）、状态判定条件缺失（1项）、方块交互语义及材质偏差（4项） |
| **需确认 (needs_verification)** | **2** | 涉及 1.17+ 深层矿石 Material 严格匹配的玩法意图确认 |
| **待审计 (pending)** | **0** | **已全部清空，无遗漏** |

---

## 2. 发现问题详析 (按严重程度分类)

### 🟡 中严重度 (文案与物品限定存在实质性矛盾 / 条件过窄)

#### 1. `OBTAIN_64_COLORED_WOOL` (Line 572)
- **目标 ID**: `OBTAIN_64_COLORED_WOOL`
- **分值**: 2 分
- **中文显示**: `获得 64 个彩色羊毛`
- **Requirement**: `item:WHITE_WOOL*64`
- **代码证据**: `src/main/java/top/lqsnow/blockracing/managers/Goal.java (sharedItemProgress)`
- **分析与逻辑冲突**:
  - 中文文案为“获得 64 个彩色羊毛”，给玩家的预期是收集任意染色羊毛。
  - requirement 仅指定 `item:WHITE_WOOL*64`。在 `Goal.sharedItemProgress` 中，按目标材料类别统计进度（未达成时 UI 显示 0/1，达成时为 1/1），其严格比对物品材质为 `WHITE_WOOL`（白色羊毛）。玩家如果收集了 64 个红色或黑色羊毛，无法完成该目标。
- **修改建议**:
  - 将中文文案修改为 `获得 64 个白色羊毛`，或将 requirement 修改为支持任意彩色羊毛。

#### 2. `OBTAIN_64_COLORED_CONCRETE` (Line 573)
- **目标 ID**: `OBTAIN_64_COLORED_CONCRETE`
- **分值**: 2 分
- **中文显示**: `获得 64 个彩色混凝土`
- **Requirement**: `item:WHITE_CONCRETE*64`
- **代码证据**: `src/main/java/top/lqsnow/blockracing/managers/Goal.java (sharedItemProgress)`
- **分析与逻辑冲突**:
  - 中文文案为“获得 64 个彩色混凝土”，但 requirement 仅指定 `item:WHITE_CONCRETE*64`（未达成时 UI 显示 0/1，达成时为 1/1）。玩家收集非白色混凝土无法达成目标。
- **修改建议**:
  - 将中文文案修改为 `获得 64 个白色混凝土`，或将 requirement 支持任意彩色混凝土。

#### 3. `REMOVE_STATUS_EFFECT_USING_MILK` (Line 624)
- **目标 ID**: `REMOVE_STATUS_EFFECT_USING_MILK`
- **分值**: 1 分
- **中文显示**: `使用牛奶桶清除状态效果`
- **Requirement**: `consume:MILK_BUCKET`
- **代码证据**: `src/main/java/top/lqsnow/blockracing/listeners/BasicListener.java (onPlayerItemConsume)`
- **分析与逻辑冲突**:
  - 中文描述包含两个条件：“饮用牛奶” + “清除身上的状态效果”。
  - 代码在 `onPlayerItemConsume` 中仅记录消费了 `Material.MILK_BUCKET`，未检测饮用前玩家身上是否有激活的药水效果。玩家在没有任何状态效果时饮用牛奶也会触发完成。
- **修改建议**:
  - 将中文文案修改为 `喝下一桶牛奶`，或在消费事件中判定 `!player.getActivePotionEffects().isEmpty()` 时才记录。

---

### 🟢 低严重度 (方块交互语义、材质匹配及粗粒度触发)

#### 4. `USE_CAULDRON` (Line 640)
- **目标 ID**: `USE_CAULDRON` (1分) | **中文**: `使用炼药锅清洗物品` | **Requirement**: `use-block:CAULDRON`
- **代码证据**: `src/main/java/top/lqsnow/blockracing/listeners/BasicListener.java (onPlayerInteract)`
- **分析**:
  1. **方块材质偏差**: 在 Minecraft 中，盛水炼药锅方块材质为 `WATER_CAULDRON`。玩家右键盛水炼药锅清洗物品时，`BasicListener.onPlayerInteract`（`ignoreCancelled=true` 收到未取消的 `RIGHT_CLICK_BLOCK`）记录的是 `WATER_CAULDRON`，无法匹配 requirement 中的 `CAULDRON`。
  2. **行为校验偏差**: 若收到针对空炼药锅（`CAULDRON`）的未取消右键事件，也会记录为完成，未校验是否发生清洗行为；不能保证所有空手或无效右键都会产生未取消事件。
- **修改建议**: 中文文案简化为 `使用炼药锅` 并支持 `WATER_CAULDRON` 等变种，或增加对清洗行为的专项监听。

#### 5. `USE_LOOM` (Line 638)
- **目标 ID**: `USE_LOOM` (1分) | **中文**: `使用织布机设计旗帜` | **Requirement**: `use-block:LOOM`
- **分析**: 收到未取消的 `RIGHT_CLICK_BLOCK` 事件时直接记录方块 `LOOM` 即判定完成，未校验是否在织布机中完成旗帜图案设计。建议中文文案简化为 `使用织布机`。

#### 6. `USE_COMPOSTER` (Line 641)
- **目标 ID**: `USE_COMPOSTER` (1分) | **中文**: `将堆肥桶填满并获得骨粉` | **Requirement**: `use-block:COMPOSTER`
- **分析**: 收到未取消的 `RIGHT_CLICK_BLOCK` 事件时直接记录方块 `COMPOSTER` 即判定完成，未校验堆肥桶是否填满 7 层并产出骨粉。建议中文文案简化为 `使用堆肥桶`。

#### 7. `USE_JUKEBOX` (Line 642)
- **目标 ID**: `USE_JUKEBOX` (2分) | **中文**: `使用唱片机播放唱片` | **Requirement**: `use-block:JUKEBOX`
- **分析**: 收到未取消的 `RIGHT_CLICK_BLOCK` 事件时直接记录方块 `JUKEBOX` 即判定完成，未校验手持物是否为唱片或唱片机是否播放。建议中文文案简化为 `使用唱片机`。

---

### ⚪ 需玩法设计确认项 (Needs Verification)

#### 8. `MINE_DIAMOND_ORE` (Line 513) & `MINE_EMERALD_ORE` (Line 514)
- **目标 ID**: `MINE_DIAMOND_ORE` / `MINE_EMERALD_ORE`
- **中文显示**: `挖掘钻石矿石` / `挖掘绿宝石矿石`
- **Requirement**: `break:DIAMOND_ORE` / `break:EMERALD_ORE`
- **代码证据**: `src/main/java/top/lqsnow/blockracing/listeners/BasicListener.java (onBlockBreak)`
- **分析说明**:
  - `BasicListener.onBlockBreak` 严格按 `event.getBlock().getType()` 比对 `DIAMOND_ORE` / `EMERALD_ORE`。
  - 在 1.17+ 中，深层变种（`DEEPSLATE_DIAMOND_ORE`、`DEEPSLATE_EMERALD_ORE`）为独立 Material 枚举，挖掘深层矿石不会触发该目标。
- **确认建议**:
  - 确认玩法设计是否要求严格挖掘普通石头基底的矿石，或需合并支持深层变种。

---

## 3. 核心机制与复核更正说明

1. **`BREED_TRADER_LLAMA` 复核结论 (已更正为一致)**:
   - **字节码证据**: 检查本机自有官方 26.2 客户端中的 `TraderLlama.makeNewLlama()`，该方法以 `EntitySpawnReason.BREEDING` 创建 `EntityTypes.TRADER_LLAMA` 并设置持久性；`Llama.getBreedOffspring()` 动态调用该方法。可用以下只读命令对自己的客户端复核，不将 Minecraft 字节码文件纳入仓库：

     ```sh
     javap -c -p -classpath /path/to/26.2.jar net.minecraft.world.entity.animal.equine.TraderLlama
     javap -c -p -classpath /path/to/26.2.jar net.minecraft.world.entity.animal.equine.Llama
     ```
   - 在 26.2 中，`TraderLlama.makeNewLlama()` 明确创建 `TRADER_LLAMA` 实体；`EntityBreedEvent.getEntityType()` 返回幼崽类型 `TRADER_LLAMA`，与 `Goal.recordBreed` 及 requirement `breed:TRADER_LLAMA` 逻辑一致。因此撤回先前的不可繁育误报。

2. **队伍账本与快照聚合机制**:
   - **历史累计与并集账本**: `advancement`、`advancement-count`、`breed`、`breed-unique`、`tame`、`kill`、`kill-unique-hostile`、`kill-undead`、`kill-arthropod`、`kill-total`、`consume`、`consume-all`、`consume-unique`、`consume-potion`、`craft-unique`、`use-block`、`break`、`fish-treasure`、`spy-unique`、`villager-max-level`、`damage-taken`、`damage-dealt`、`death-*` 均基于队伍固定成员名单 `names` 聚合，包含当前离线成员在在线期间已记入的历史贡献。
   - **实时物品快照**: `item:...`、`item-unique:...`、`enchanted-item:...` 依赖全队当前在线队员背包与共享箱的实时状态。
   - **瞬时个人状态**: `equipment-*`、`wear-continuous`、`effect`、`location`、`hunger`、`level` 依赖在线队员瞬时状态，取全队最佳值。

3. **597 个 Block 目标静态核验**:
   - 全部 597 个材质 ID 均在 Paper 26.2 `Material` 枚举中存在。
   - 549 个目标可直接在本地 `zh_cn.json` 找到精确键值匹配；其余 48 项在 `Targets.csv` 第 6 列均提供标准中文名称。
   - 轮询判定在 `Game.runPer5Tick` 中通过全队在线队员背包与队伍箱实时检测。

---

## 4. 交付文件路径

- 逐项审计详情表: `docs/audits/target-consistency-20260906/targets.csv`
- 审计报告总结: `docs/audits/target-consistency-20260906/summary.md`
- 本地备份目录: `.local-backups/target-consistency-audit/`

## 5. 后续实机验证边界

未启动多人 Paper 26.2 实服。取消事件、合成结果实际取出、交易升级归属、瞬时效果与连续穿戴的采样边界、断线恢复、对手抢先结算等仍需运行时回归；本表并非这些边界行为的通过证明。表中的两个 `needs_verification` 专指已识别出的矿石变种设计选择，不表示仅剩两个运行时风险。

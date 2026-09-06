# 逐目标一致性审计任务

你是主开发代理启动的独立 agy 审计进程。工作目录为当前 audit/target-consistency worktree，审计基线提交 b6efc02，已包含 main 的轮播 UI 与队伍共享进度。用户明确要求：逐个审计各目标的「中文显示 vs requirement vs 代码逻辑」是否一致。

**准确的项目绝对路径是 `/home/darkpaper/Game/blockeracing-src/.worktrees/target-consistency-audit`。你的终端工具可能默认在 `/home/darkpaper/.gemini/antigravity-cli/scratch`，那不是本项目。每次终端命令必须显式以 `cd /home/darkpaper/Game/blockeracing-src/.worktrees/target-consistency-audit &&` 开头；文件工具必须用本项目下的绝对路径。第一步只在上述精确路径核验 `pwd`、`git rev-parse HEAD`、读取 `AGENTS.md`，禁止搜索家目录、外层目录或整个文件系统来“找项目”。如果该精确路径不可访问，立即停止并报告，不自行猜其他路径。**

## 范围与权限

- 完整阅读本目录 AGENTS.md；仅在本 worktree 工作。禁止改 Java 源码、Targets.csv、配置、测试、任务分值或任何游戏逻辑；禁止 git commit/push/checkout、网络发布、读取凭据、修改全局环境及启动游戏服。
- 唯一允许写入的目录是 docs/audits/target-consistency-20260906/ 和 .local-backups/target-consistency-audit/。只生成审计报告和临时审计数据；不要写到主 worktree，也不要启动额外代理。
- 可以使用本地只读命令检查源码与测试。无需跑大型实验。遇到工具权限拒绝，明确报告，不绕过。
- src/main/resources/Targets.csv 的每一个数据行均在范围内，包含 score=-1 的禁用目标，不能略过普通 block 目标。以 Python csv 或同等可靠的 CSV 解析器读取，不按逗号手工 split。

## 审计方法

1. 先读 CSV 加载/注册/有效性筛选逻辑、Goal requirement 解析器、teamProgress 与 individualProgress、事件监听器、Game 完成判定、Team 和持久化、UI 中文显示选择；使用当前实际代码，不凭方法名推断。
2. 逐行核对：中文对象是否和 ID / Material / EntityType / advancement key 相同，数量、任一/全部/互异、动作（获得/穿戴/消耗/合成/使用/挖掘/击杀等）、位置和持续时长是否对应 requirement；再沿解析器→事件或状态→队内聚合→完成阈值核对语义。
3. block 类型 requirement 为空是正常 schema：实际规则来自 id 指定的 Material 与 Game 的持有检查，不要误报“缺少 requirement”；但要逐个检查中文对应的材料是否正确。禁用项仍审计，标明未启用，不把禁用当作逻辑不存在。
4. 重点检查：数值求和 vs 唯一 ID 去重；伤害按最终伤害及取消事件；完成 30 个进度排除配方/无展示进度并去重；成就需同一人完成全部 criteria；物品使用全队在线背包+队伍箱当前快照而非历史；全套装备/多效果/经验/持续穿戴须同一人满足；离线/恢复；任意鹦鹉螺铠甲/马铠候选完备性；唱片/树苗/铜变种清单；繁殖行商羊驼；特定附魔/死亡来源/使用工作站/合成的真实事件触发条件。也检查没有列在重点中的所有行。
5. 不要把“同类型代表目标已检查”写成所有行均已检查；允许共享代码依据，但每一行的中文、参数、候选必须实际核对。不要把推测写成已确认；缺少实服证据或无法确定的版本语义标为需确认，注明原因。测试通过不等于中文与游戏语义正确。
6. 发现不一致时区分：明确 bug、文案误导、版本/事件语义待实机验证、设计需用户确认；不要自行修复或改变用户想要的玩法。

## 交付文件

持续、分批写入报告，避免最后一次超长输出丢失；先建立全部行清单，再逐项更新结论。CSV 使用 UTF-8、标准引号转义，每行必须对应输入的一行，顺序一致，不重复不遗漏。

1. `targets.csv` 列名严格为：

   `csv_line,id,type,score,chinese,requirement,status,chinese_vs_requirement,requirement_vs_code,code_refs,recommendation`

   - csv_line 为源 CSV 的逻辑记录序号（含标题，第一目标是 2）；chinese 原样复制「中文显示」，requirement 原样复制。目标 type/id/score 也原样保留。
   - status 仅取 `consistent` / `mismatch` / `needs_verification` / `pending`。完成时不得有 pending；无法判定的明确写 needs_verification 而非 consistent。
   - chinese_vs_requirement 和 requirement_vs_code 分别给出中文理由，明确数字/对象/动作/集合与共享语义；code_refs 给出实际相对文件路径:行号或方法名，须可追溯。
   - recommendation 无需改动时写“无”；有问题时写最小建议，不直接改源码。

2. `summary.md`：基线完整 SHA、开始/结束时间、总行数/启用/禁用/三种结论计数；按严重度列出真实问题（目标 ID、中文、requirement、代码证据、可复现场景、建议）；说明待实机验证与审计限制。所有行 consistent 也必须说明检查依据，禁止空泛宣称。

3. 结束前自行用程序验证输入输出逐行 id/中文/requirement/type/score 一致、所有行覆盖且没有 pending，验证 summary 的计数相符。

最后回复简短摘要、文件路径、覆盖数量和最重要问题，不在终端输出整张大表。

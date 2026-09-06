# 主代理复核：必须纠正后才能交付

工作目录仍是 `/home/darkpaper/Game/blockeracing-src/.worktrees/target-consistency-audit`，每条命令显式 cd，不改源码。已有 764 行 CSV 覆盖/字段校验通过，但报告存在误报与证据错误。请仅修订审计报告并重新核验统计，不重做无关事项。

1. **BREED_TRADER_LLAMA 的“永久无法繁殖 / 后代只能 LLAMA”是误报，必须撤回。** 主代理检查本机官方 26.2 客户端字节码发现：`net.minecraft.world.entity.animal.equine.TraderLlama.makeNewLlama()` 明确调用 `EntityTypes.TRADER_LLAMA.create(..., EntitySpawnReason.BREEDING)`，随后 setPersistenceRequired。`Llama.getBreedOffspring()` 动态调用 makeNewLlama，TraderLlama 继承 canMate。允许为复核此项只读访问这个精确的官方文件：`/home/darkpaper/.config/hmcl/.minecraft/versions/26.2/26.2.jar`，使用 `/usr/lib/jvm/java-26-openjdk/bin/javap -c -p -classpath <jar> <class>`。不复制完整反编译代码进 Git，只写方法级证据和复现命令。纠正该行 status、分析、建议和 summary，不能沿用旧版游戏记忆。没有实服测试就不要承诺具体驯服步骤已实测。
2. **多个源码行号完全不对应内容。** 例如真实 Goal.java:484 是击杀类型分支，不是 ItemRequirement；真实物品聚合是 teamProgress 520..538，sharedItemProgress 549..566；真实 advancement-count 在 516，recordAdvancement/refreshAdvancements 在 636/644。请逐项核验 code_refs 和正文中的行号，无法稳定定位时用准确的相对路径 + 方法名（任务原允许），绝不能随意编行号。尤其检查 Game.java 与 Block.java 的共同引用是否真的对应加载/判定。
3. **队伍成就等累计账本不是只聚合在线玩家。** 例如 GET_30_ADVANCEMENTS 行现在写“队伍所有在线玩家”，实际 union 按固定成员 names 聚合，包含已记录的离线成员。检查其他累计/唯一目标的同类文案；仅物品快照与瞬时个人状态依赖在线。
4. **彩色羊毛例子的 UI 不是 0/64。** 这个 ItemRequirement 按完成材料类别计进度，未达到 WHITE_WOOL*64 时是 0/1，达到后 1/1。保留颜色不一致结论，但纠正演示数值。
5. **USE_CAULDRON 还存在实际 block type 不匹配。** 空锅是 CAULDRON，装水清洗用 WATER_CAULDRON；当前监听器直接记录 clickedBlock.getType，requirement 只接受 CAULDRON。正文不能只说“有无水不检查”。此外监听器 ignoreCancelled=true，不能笼统保证所有右键（包括被取消/预测无效交互）都会完成；正确表述是“收到未取消的 RIGHT_CLICK_BLOCK 事件时记录该方块类型，未校验具体操作”。同样修正其它 use-block 行。
6. **收敛没有证据的完整性承诺。** summary 写“全部中文与官方译名完全一致”“全部 isItem()==true”，但你先前输出本地 zh_cn.json 有 48 项无对应键。请明确静态核对依据及局限，不把存在于 Material 枚举等同实测 isItem 或实服可获得。版本当前是 26.2，不要笼统套用旧版本知识。深层矿石也只需说明 Material 严格匹配，不必声称深层绿宝石占绝大多数。
7. 报告需有明确“静态审计，不代表多人实服验收”限制，consistent 表示本次静态检查未发现差异，不承诺没有遗漏 bug。保留正确的 7 个文案/requirement 或事件粒度问题，不自动修改玩法，也不建议在未获用户确认时删除他明确要求的行商羊驼目标。

请完成上述修订，重新验证 764 行身份一致与统计、无 pending；最终仅回复修订后统计和主要更正。上次流式 result 残留旧网络 error 字段但 exit code=0 且已写报告，这次无需因该历史字段反复重做。

# 语言与翻译

[返回首页](README.md) · [English](docs/en/TranslationTutorial-en.md)

26.2 合并版采用上游的按玩家语言机制，支持简体中文和英文。玩家使用 `/language` 打开菜单，或使用 `/language auto`、`/language zh_cn`、`/language en_us`。自动模式按客户端语言选择，中文客户端使用中文，其他语言使用英文。

| 内容 | 来源 |
| --- | --- |
| 中文菜单、记分板、提示 | `plugins/BlockRacing/lang.yml` |
| 英文菜单、记分板、提示 | `plugins/BlockRacing/languages/en_us/lang.yml` |
| 玩家选择 | `plugins/BlockRacing/language-preferences.yml` |
| 中文任务名称 | `Targets.csv` 的第六列 `中文显示` |
| 英文物品名称 / 英文行为任务名 | `en_us.json` / CSV 的 `display_name` |
| 通用物品名称 | `zh_cn.json`、`en_us.json` |

首次启动复制缺失文件，但不覆盖现有文件。升级旧版时，先备份并停服，比较更新上述资源；**不要再通过把英文文件覆盖到顶层 lang.yml 来切换所有玩家的语言**。根目录 [en-us/lang.yml](en-us/lang.yml) 是打包进 `languages/en_us/lang.yml` 的英文源文件，[src/main/resources/lang.yml](src/main/resources/lang.yml) 是中文源文件。根目录 `zh-cn/` 保留同结构示例。

保留 YAML 键、颜色代码和 `%player%` 等占位符。语言管理器对缺失消息回退到内置默认值，但不会纠正旧文件中已存在的过时文案，尤其应核对积分、Bonus 和规则书。修改后可用管理员 `/debug reload` 重载消息，完整升级建议重启。

`lang.yml` 的 `lang` 字段仍用于无玩家上下文的旧式消息/翻译；它不是玩家语言偏好的开关。部分行为任务的进度明细仍沿用原有中文标签，尚非所有自定义目标都完整双语。

## 任务与新增语言

中文任务名维护在 CSV 的 `中文显示`，英文行为任务名维护在 `display_name`。不要修改任务 ID、Material 名或 requirement 技术标识符来翻译文本。物品 JSON 中使用 Minecraft 翻译键。

目前玩家选择器只支持中文、英文和自动模式。新增第三种语言需要同时扩展 `LanguageManager`、命令和菜单，不能只放入另一份 JSON。任务格式见[任务库维护](docs/targets.md)。

# 语言与翻译

[返回首页](README.md) · [English](docs/en/TranslationTutorial-en.md)

当前版本的文本来自三处：

| 内容 | 来源 |
| --- | --- |
| 菜单、记分板、提示语 | 服务器 `plugins/BlockRacing/lang.yml` |
| 简体中文任务名称 | `Targets.csv` 的第六列 `中文显示` |
| 非中文物品名称 | 对应 `<语言代码>.json` 中的 Minecraft 翻译键 |

自定义 goal 在非中文模式下使用 CSV 的 `display_name`。`zh_cn.json` 已不随本 fork 打包，中文任务名称直接维护在 CSV 中。

## 切换语言

1. 先备份服务器的 `plugins/BlockRacing/`，然后停服。
2. 中文使用 [内置 lang.yml](src/main/resources/lang.yml)，英文参考 [en-us/lang.yml](en-us/lang.yml)，将所需语言内容保存为服务器的 `lang.yml`。
3. 设置 `lang.yml` 中的 `lang`：中文为 `zh_cn`，英文为 `en_us`。实际翻译选择读取的是该字段；`config.yml` 中保留的 `lang` 字段不参与当前 `Config` 枚举的配置读取。
4. 英文物品名称需要同目录中的 `en_us.json`，首次启动时会自动生成。其他语言需自行提供同名 JSON。
5. 重启服务器，检查任务菜单、提示语和物品名称。

修改语言时保留 YAML 结构、颜色代码和 `%player%` 等占位符。以 [内置消息资源](src/main/resources/lang.yml) 为完整键集合，翻译文件缺少的新键应同步补齐。

根目录 `en-us/`、`zh-cn/` 的配置是历史语言示例；更新服务器设置时，以 [内置 config.yml](src/main/resources/config.yml) 为准，不要用旧示例覆盖现有设置。

## 增加其他语言

将当前消息文件翻译为所需语言，设置 `lang.yml` 的 `lang`，再提供该语言的 Minecraft JSON。使用当前客户端对应的资源索引查找 `minecraft/lang/<语言代码>.json`，通过其 hash 找到客户端 assets 中的对象文件；索引文件名随游戏版本变化。

组合任务的外语名称仍取自 `Targets.csv` 的 `display_name`，需自行翻译该列。简体中文则编辑 `中文显示` 列。不要改动任务 ID、Material 名称或 requirement 中的技术标识符。

任务格式详见 [任务库维护](docs/targets.md)。

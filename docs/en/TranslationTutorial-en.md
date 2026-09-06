# Language and translation

[README](README-en.md) · [简体中文](../../TranslationTutorial.md)

The 26.2 merge supports per-player Chinese and English. Use `/language` or `/language auto|zh_cn|en_us`. Auto selects Chinese for Chinese clients and English otherwise.

| Content | File under plugins/BlockRacing |
| --- | --- |
| Chinese messages | `lang.yml` |
| English messages | `languages/en_us/lang.yml` |
| Player preferences | `language-preferences.yml` |
| Material translations | `zh_cn.json`, `en_us.json` |
| Chinese task names | `Targets.csv`, column `中文显示` |
| English custom goal names | `Targets.csv`, column `display_name` |

Missing files are copied on first startup; existing files are not replaced. Back up, stop the server and compare/update resources when upgrading. Do not overwrite the top-level Chinese message file with English to switch languages.

Edit [the English source](../../en-us/lang.yml) and [the Chinese source](../../src/main/resources/lang.yml), retaining YAML keys, color codes and placeholders. Missing message keys fall back to defaults, but existing outdated scoring or rule-book text must be updated manually. Admin `/debug reload` reloads messages; restart for a complete upgrade.

The top-level `lang` field still controls legacy messages without player context, not player preferences. Some custom-goal progress details retain Chinese labels. A third selectable language requires changes to `LanguageManager`, the command and the menu, not just another JSON file.

Translate CSV labels, never task IDs, Material enum names or requirement identifiers. See [target maintenance](../targets.md).

# Language and translation

[README](README-en.md) · [简体中文](../../TranslationTutorial.md)

Messages and menus use the server's `plugins/BlockRacing/lang.yml`. Simplified Chinese task names come from the sixth column of `Targets.csv`; non-Chinese item names use `<language>.json`. Custom goals use the CSV `display_name` outside Chinese mode.

## Switching languages

1. Back up `plugins/BlockRacing/` and stop the server.
2. Use the [bundled Chinese messages](../../src/main/resources/lang.yml) or [English messages](../../en-us/lang.yml) as the server's `lang.yml`.
3. Set `lang` in `lang.yml` to `zh_cn`, `en_us`, or your language code. This is the field the translation code reads; the legacy `lang` field in `config.yml` is not read by the current `Config` enum.
4. For English, keep `en_us.json` beside `lang.yml`; it is created on first startup. For another language, supply the matching JSON yourself.
5. Restart and check task names, menus and messages.

Preserve YAML structure, color codes and placeholders such as `%player%`. Compare translations against the bundled message file when adding missing keys. Use [src/main/resources/config.yml](../../src/main/resources/config.yml) for current game defaults; the configuration examples in `en-us/` and `zh-cn/` are historical and should not replace your server settings wholesale.

## Additional languages

Find `minecraft/lang/<language>.json` in the asset index for your current Minecraft client. Use its hash to locate the corresponding asset object, then copy that object to the plugin directory as `<language>.json`. Asset index filenames vary between game versions.

Translate CSV `display_name` for custom goals in non-Chinese mode, or `中文显示` for Chinese mode. Keep task IDs, Material names and requirement identifiers unchanged. This fork does not bundle `zh_cn.json`; Chinese target names are maintained in the CSV.

See [target maintenance](../targets.md) for CSV details.

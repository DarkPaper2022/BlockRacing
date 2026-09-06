package top.lqsnow.blockracing.menus;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import top.lqsnow.blockracing.managers.Block;
import top.lqsnow.blockracing.managers.Game;
import top.lqsnow.blockracing.managers.Goal;
import top.lqsnow.blockracing.managers.LanguageManager;
import top.lqsnow.blockracing.managers.Message;
import top.lqsnow.blockracing.managers.Team;
import top.lqsnow.blockracing.toolkit.item.ItemBuilder;
import top.lqsnow.blockracing.toolkit.menu.MenuButton;
import top.lqsnow.blockracing.toolkit.menu.MenuView;

import java.util.ArrayList;
import java.util.List;

/** The shared task view, including goals and Bonus, on the native menu toolkit. */
public final class TargetListMenu extends MenuView {
    static final int PAGE_SIZE = 45;
    private final String team;
    private final boolean preview;
    private final Filter filter;
    private int page;
    private int taskCount;
    private List<Entry> entries = List.of();

    public TargetListMenu(Player player, int requestedPage) {
        this(player, requestedPage, Filter.ALL, false);
    }

    public static TargetListMenu preview(Player player) {
        return new TargetListMenu(player, 0, Filter.GOALS, true);
    }

    private TargetListMenu(Player player, int requestedPage, Filter filter, boolean preview) {
        super(54, viewer -> (preview ? "[UI] " : "") + (LanguageManager.usesChinese(viewer)
                ? "任务 · " + filter.label(true) : "Tasks · " + filter.label(false)));
        this.preview = preview;
        this.filter = filter;
        team = Team.redTeamPlayers.contains(player.getName()) ? "red"
                : Team.blueTeamPlayers.contains(player.getName()) ? "blue" : "";
        page = Math.max(0, requestedPage);
        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int offset = slot;
            setButton(slot, MenuButton.of(viewer -> {
                int index = page * PAGE_SIZE + offset;
                return index < entries.size() ? item(entries.get(index), viewer) : null;
            }, (viewer, click) -> { }));
        }
        setButton(45, MenuButton.of(
                viewer -> ItemBuilder.of(Material.ARROW).name(Message.MENU_TARGET_LIST_PREVIOUS.getString(viewer)).build(),
                (viewer, click) -> new TargetListMenu(viewer, Math.max(0, page - 1), filter, preview).open(viewer)));
        setButton(53, MenuButton.of(
                viewer -> ItemBuilder.of(Material.ARROW).name(Message.MENU_TARGET_LIST_NEXT.getString(viewer)).build(),
                (viewer, click) -> new TargetListMenu(viewer, Math.min(maxPage(), page + 1), filter, preview).open(viewer)));
        setButton(48, MenuButton.of(
                viewer -> ItemBuilder.of(Material.BARRIER).name(Message.MENU_ALL_RETURN_BACK.getString(viewer)).build(),
                (viewer, click) -> top.lqsnow.blockracing.managers.Gui.openMenu(viewer)));
        setButton(46, MenuButton.of(viewer -> ItemBuilder.of(Material.HOPPER)
                .name("§b" + (LanguageManager.usesChinese(viewer) ? "筛选：" : "Filter: ")
                        + filter.label(LanguageManager.usesChinese(viewer)))
                .lore(List.of(LanguageManager.usesChinese(viewer)
                        ? "§7全部 → 物品 → 行为 → Bonus" : "§7All → Items → Goals → Bonus")).build(),
                (viewer, click) -> new TargetListMenu(viewer, 0, filter.next(), preview).open(viewer)));
        setButton(50, MenuButton.of(viewer -> ItemBuilder.of(Material.KNOWLEDGE_BOOK)
                .name(LanguageManager.usesChinese(viewer) ? "§b图标说明" : "§bIcon legend")
                .lore(LanguageManager.usesChinese(viewer) ? List.of(
                        "§f对象图标 = 具体物品 / 生物 / 工作站", "§f叠放数量 = 需求数量，不是任务分数",
                        "§6Bonus 发光；标题 ★ 标记，筛选可单独查看", "§7可选资源包：左上动作符号，右上精确数量",
                        "§7ANY = 任一；ALL = 全部；5M = 5 分钟", "§7数字 100/200/400 不截断成 64",
                        "§7悬停直接显示完整目标与个人进度，无需点击", "§7点击底部页码刷新任务与进度") : List.of(
                        "§fObject = required item / mob / workstation", "§fStack count = requirement, NOT score",
                        "§6Bonus glows and is marked ★; use the Bonus filter", "§7Optional pack: action top-left, exact count top-right",
                        "§7ANY = any one; ALL = all; 5M = 5 minutes", "§7Counts over 64 are never truncated",
                        "§7Hover for full goal and personal progress; no click needed", "§7Click the page counter to refresh")).build(),
                (viewer, click) -> { }));
        setButton(49, MenuButton.of(viewer -> ItemBuilder.of(Material.PAPER)
                .name(Message.MENU_TARGET_LIST_PAGE.getString(viewer)
                        .replace("%page%", String.valueOf(page + 1))
                        .replace("%total_page%", String.valueOf(maxPage() + 1))
                        .replace("%amount%", String.valueOf(taskCount))).build(),
                (viewer, click) -> refresh(viewer)));
    }

    @Override
    protected void beforeRender(Player viewer) {
        List<String> targets = preview ? Block.targetScores.keySet().stream().sorted().toList()
                : team.isEmpty() ? List.of() : Game.getCurrentBlocks(team);
        entries = buildEntries(targets, filter);
        taskCount = (int) entries.stream().filter(entry -> entry.target() != null).count();
        page = Math.min(page, maxPage());
    }

    private int maxPage() {
        return Math.max(0, (entries.size() - 1) / PAGE_SIZE);
    }

    static List<Entry> buildEntries(List<String> targets) {
        return buildEntries(targets, Filter.ALL);
    }

    static List<Entry> buildEntries(List<String> targets, Filter filter) {
        List<Entry> result = new ArrayList<>();
        for (int category = 0; category < 3; category++) {
            if (!filter.includes(category)) continue;
            boolean headerAdded = false;
            for (int index = 0; index < targets.size(); index++) {
                String target = targets.get(index);
                int kind = Block.isBonusTarget(target) ? 2 : Goal.isGoal(target) ? 1 : 0;
                if (kind != category) continue;
                if (!headerAdded) {
                    result.add(new Entry(null, 0, category));
                    headerAdded = true;
                }
                result.add(new Entry(target, index + 1, category));
            }
        }
        return result;
    }

    private ItemStack item(Entry entry, Player viewer) {
        boolean chinese = LanguageManager.usesChinese(viewer);
        if (entry.target() == null) {
            String[] labels = chinese ? new String[]{"物品任务", "特殊任务", "奖励任务"}
                    : new String[]{"Items", "Goals", "Bonus"};
            return ItemBuilder.of(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
                    .name("§b" + labels[entry.category()]).build();
        }
        String target = entry.target();
        int score = Block.getTargetScore(target);
        String color = Block.isBonusTarget(target) ? "§4" : score >= 5 ? "§6" : score >= 3 ? "§c" : score == 2 ? "§e" : "§f";
        TargetIconCatalog.Icon visual = TargetIconCatalog.matching(target, Goal.getRawRequirement(target));
        Material icon = visual != null ? visual.material()
                : Goal.isGoal(target) ? Goal.getGoalIcon(target) : Material.getMaterial(target);
        if (icon == null || !icon.isItem()) icon = Material.PAPER;
        List<String> lore = new ArrayList<>();
        String type = Goal.isGoal(target) ? Goal.getGoalTypeLabel(target, viewer) : chinese ? "物品" : "Item";
        if (visual != null) type = visual.actionLabel(chinese);
        lore.add("§7#" + entry.index() + " | " + type);
        lore.add(color + score + (chinese ? " 分" : " points"));
        if (Block.isBonusTarget(target)) lore.add(chinese ? "§6奖励可用积分，不计胜利进度" : "§6Currency reward; no victory progress");
        if (!preview) lore.addAll(Goal.getProgressLore(target, viewer));
        else lore.add(chinese ? "§8仅预览图标，不抽样或修改本局目标" : "§8UI preview only; no task sampling or game changes");
        for (String line : Message.MENU_TARGET_LIST_ITEM_LORE.getStringList(viewer)) {
            lore.add(line.replace("%index%", String.valueOf(entry.index()))
                    .replace("%target%", Game.getTargetDisplayName(target, viewer))
                    .replace("%score%", String.valueOf(score)));
        }
        String marker = Block.isBonusTarget(target) ? "★ " : "";
        ItemStack item = ItemBuilder.of(icon).name(color + marker + "[" + type + "] "
                + Game.getTargetDisplayName(target, viewer)).lore(lore).build();
        ItemMeta meta = item.getItemMeta();
        meta.setEnchantmentGlintOverride(Block.isBonusTarget(target) || (visual != null && visual.glint()));
        if (visual != null) {
            var model = meta.getCustomModelDataComponent();
            model.setStrings(List.of(visual.modelKey()));
            model.setFlags(List.of(Block.isBonusTarget(target)));
            meta.setCustomModelDataComponent(model);
            // Ordinary clients ignore this custom model data and still render the native object.
            // Never change durability/stack rules on weapons and armor just to draw a number.
            if (icon.getMaxDurability() == 0 && visual.stackAmount() > 1) {
                meta.setMaxStackSize(64);
                item.setAmount(visual.stackAmount());
            }
            if (meta instanceof PotionMeta potion && visual.requirement().equals("consume-potion:WATER")) {
                potion.setBasePotionType(PotionType.WATER);
            }
        }
        item.setItemMeta(meta);
        return item;
    }

    enum Filter {
        ALL, ITEMS, GOALS, BONUS;
        Filter next() { return values()[(ordinal() + 1) % values().length]; }
        boolean includes(int category) { return this == ALL || ordinal() - 1 == category; }
        String label(boolean chinese) {
            return (chinese ? new String[]{"全部", "物品", "行为", "Bonus"}
                    : new String[]{"All", "Items", "Goals", "Bonus"})[ordinal()];
        }
    }

    record Entry(String target, int index, int category) { }
}

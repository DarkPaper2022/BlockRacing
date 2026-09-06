package top.lqsnow.blockracing.menus;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
    private int page;
    private int taskCount;
    private List<Entry> entries = List.of();

    public TargetListMenu(Player player, int requestedPage) {
        super(54, viewer -> LanguageManager.usesChinese(viewer) ? "任务列表" : "Tasks");
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
                (viewer, click) -> new TargetListMenu(viewer, Math.max(0, page - 1)).open(viewer)));
        setButton(53, MenuButton.of(
                viewer -> ItemBuilder.of(Material.ARROW).name(Message.MENU_TARGET_LIST_NEXT.getString(viewer)).build(),
                (viewer, click) -> new TargetListMenu(viewer, Math.min(maxPage(), page + 1)).open(viewer)));
        setButton(48, MenuButton.of(
                viewer -> ItemBuilder.of(Material.BARRIER).name(Message.MENU_ALL_RETURN_BACK.getString(viewer)).build(),
                (viewer, click) -> new GameMenu().open(viewer)));
        setButton(49, MenuButton.of(viewer -> ItemBuilder.of(Material.PAPER)
                .name(Message.MENU_TARGET_LIST_PAGE.getString(viewer)
                        .replace("%page%", String.valueOf(page + 1))
                        .replace("%total_page%", String.valueOf(maxPage() + 1))
                        .replace("%amount%", String.valueOf(taskCount))).build(),
                (viewer, click) -> refresh(viewer)));
    }

    @Override
    protected void beforeRender(Player viewer) {
        List<String> targets = team.isEmpty() ? List.of() : Game.getCurrentBlocks(team);
        taskCount = targets.size();
        entries = buildEntries(targets);
        page = Math.min(page, maxPage());
    }

    private int maxPage() {
        return Math.max(0, (entries.size() - 1) / PAGE_SIZE);
    }

    static List<Entry> buildEntries(List<String> targets) {
        List<Entry> result = new ArrayList<>();
        for (int category = 0; category < 3; category++) {
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
        Material icon = Goal.isGoal(target) ? Goal.getGoalIcon(target) : Material.getMaterial(target);
        if (icon == null || !icon.isItem()) icon = Material.PAPER;
        List<String> lore = new ArrayList<>();
        String type = Goal.isGoal(target) ? Goal.getGoalTypeLabel(target, viewer) : chinese ? "物品" : "Item";
        lore.add("§7#" + entry.index() + " | " + type);
        lore.add(color + score + (chinese ? " 分" : " points"));
        if (Block.isBonusTarget(target)) lore.add(chinese ? "§6奖励可用积分，不计胜利进度" : "§6Currency reward; no victory progress");
        lore.addAll(Goal.getProgressLore(target, viewer));
        for (String line : Message.MENU_TARGET_LIST_ITEM_LORE.getStringList(viewer)) {
            lore.add(line.replace("%index%", String.valueOf(entry.index()))
                    .replace("%target%", Game.getTargetDisplayName(target, viewer))
                    .replace("%score%", String.valueOf(score)));
        }
        return ItemBuilder.of(icon).name(color + Game.getTargetDisplayName(target, viewer)).lore(lore).build();
    }

    record Entry(String target, int index, int category) { }
}

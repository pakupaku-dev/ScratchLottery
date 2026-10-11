package jp.pakupaku.scratchlottery.command;

import jp.pakupaku.scratchlottery.service.LotteryService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ScratchCommand implements CommandExecutor, TabCompleter {
    private static final String RELOAD_PERMISSION = "scratchlottery.reload";
    private static final String USE_PERMISSION = "scratchlottery.use";

    private final LotteryService lotteryService;

    public ScratchCommand(LotteryService lotteryService) {
        this.lotteryService = lotteryService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission(RELOAD_PERMISSION)) {
                lotteryService.sendNoPermission(sender);
                return true;
            }

            lotteryService.reload();
            lotteryService.sendReloadComplete(sender);
            return true;
        }

        if (!(sender instanceof Player player)) {
            lotteryService.sendPlayersOnly(sender);
            return true;
        }

        if (!player.hasPermission(USE_PERMISSION)) {
            lotteryService.sendNoPermission(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("stats")) {
            lotteryService.sendPlayerStats(player);
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("prizes")) {
            lotteryService.sendPrizeList(player);
            return true;
        }

        lotteryService.openPurchaseMenu(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            if (sender.hasPermission(RELOAD_PERMISSION)) {
                suggestions.add("reload");
            }
            suggestions.add("stats");
            suggestions.add("prizes");
            return suggestions;
        }
        return Collections.emptyList();
    }
}

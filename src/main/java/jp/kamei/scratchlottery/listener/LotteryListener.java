package jp.kamei.scratchlottery.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import jp.kamei.scratchlottery.gui.PurchaseMenuHolder;
import jp.kamei.scratchlottery.service.LotteryService;
import jp.kamei.scratchlottery.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class LotteryListener implements Listener {
    private final LotteryService lotteryService;

    public LotteryListener(LotteryService lotteryService) {
        this.lotteryService = lotteryService;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (!(event.getInventory().getHolder() instanceof PurchaseMenuHolder holder)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getInventory()) {
            return;
        }

        lotteryService.handleMenuClick(player, holder, event.getRawSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof PurchaseMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!lotteryService.isWaitingForCustomInput(player)) {
            return;
        }

        event.setCancelled(true);
        String plain = TextUtil.plain(event.message());
        Bukkit.getScheduler().runTask(lotteryService.getPlugin(), () -> lotteryService.handleCustomInput(player, plain));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lotteryService.clearPendingInput(event.getPlayer());
    }
}

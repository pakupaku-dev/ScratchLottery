package jp.pakupaku.scratchlottery.service;

import jp.pakupaku.scratchlottery.ScratchLotteryPlugin;
import jp.pakupaku.scratchlottery.gui.PurchaseMenuHolder;
import jp.pakupaku.scratchlottery.model.PlayerLotteryStats;
import jp.pakupaku.scratchlottery.model.PrizeTier;
import jp.pakupaku.scratchlottery.model.PurchaseSummary;
import jp.pakupaku.scratchlottery.util.TextUtil;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class LotteryService {
    private static final int MENU_SIZE = 27;
    private static final int INFO_SLOT = 4;
    private static final int PRIZE_LIST_SLOT = 8;
    private static final int CUSTOM_SLOT = 22;
    private static final int CLOSE_SLOT = 26;
    private static final int[] OPTION_SLOTS = new int[] { 10, 12, 14, 16, 20, 24 };
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.##");

    private final ScratchLotteryPlugin plugin;
    private final Economy economy;
    private final Random random;
    private final File stateFile;
    private final File playerStatsFile;
    private final Map<UUID, Boolean> pendingCustomInput;
    private final Map<UUID, PlayerLotteryStats> playerStats;

    private double ticketPrice;
    private int poolSize;
    private int batchResetDays;
    private int customInputMax;
    private boolean broadcastFirstPrize;
    private List<Integer> purchaseOptions;
    private List<PrizeTier> prizeTiers;
    private Map<String, Integer> remainingPrizeCounts;
    private int remainingTickets;
    private long lastResetAt;

    public LotteryService(ScratchLotteryPlugin plugin, Economy economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.random = new Random();
        this.stateFile = new File(plugin.getDataFolder(), "lottery-state.yml");
        this.playerStatsFile = new File(plugin.getDataFolder(), "player-stats.yml");
        this.pendingCustomInput = new ConcurrentHashMap<>();
        this.playerStats = new ConcurrentHashMap<>();
        this.purchaseOptions = new ArrayList<>();
        this.prizeTiers = new ArrayList<>();
        this.remainingPrizeCounts = new LinkedHashMap<>();
    }

    public void reload() {
        plugin.reloadConfig();
        loadValues();
        loadState();
        loadPlayerStats();
        pendingCustomInput.clear();
    }

    public void openPurchaseMenu(Player player) {
        ensureBatchFresh();
        pendingCustomInput.remove(player.getUniqueId());

        PurchaseMenuHolder holder = new PurchaseMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, MENU_SIZE, text("gui.title", Collections.emptyMap()));
        holder.setInventory(inventory);

        fillInventory(inventory);
        holder.setInfoSlot(INFO_SLOT);
        inventory.setItem(INFO_SLOT, createInfoItem());

        holder.setPrizeListSlot(PRIZE_LIST_SLOT);
        inventory.setItem(PRIZE_LIST_SLOT, createPrizeListItem());

        int optionCount = Math.min(OPTION_SLOTS.length, purchaseOptions.size());
        for (int i = 0; i < optionCount; i++) {
            int amount = purchaseOptions.get(i);
            int slot = OPTION_SLOTS[i];
            holder.addPurchaseSlot(slot, amount);
            inventory.setItem(slot, createPurchaseItem(amount));
        }

        holder.setCustomSlot(CUSTOM_SLOT);
        inventory.setItem(CUSTOM_SLOT, createCustomItem());

        holder.setCloseSlot(CLOSE_SLOT);
        inventory.setItem(CLOSE_SLOT, createSimpleItem(Material.BARRIER, "gui.close-name", Collections.emptyMap()));

        player.openInventory(inventory);
    }

    public void handleMenuClick(Player player, PurchaseMenuHolder holder, int rawSlot) {
        if (rawSlot == holder.getInfoSlot()) {
            sendPlayerStats(player);
            return;
        }

        if (rawSlot == holder.getPrizeListSlot()) {
            sendPrizeList(player);
            return;
        }

        Integer purchaseAmount = holder.getPurchaseAmount(rawSlot);
        if (purchaseAmount != null) {
            player.closeInventory();
            purchaseTickets(player, purchaseAmount);
            return;
        }

        if (rawSlot == holder.getCustomSlot()) {
            player.closeInventory();
            pendingCustomInput.put(player.getUniqueId(), Boolean.TRUE);
            send(player, "custom-input-prompt", Collections.emptyMap());
            return;
        }

        if (rawSlot == holder.getCloseSlot()) {
            player.closeInventory();
        }
    }

    public boolean handleCustomInput(Player player, String input) {
        if (!pendingCustomInput.containsKey(player.getUniqueId())) {
            return false;
        }

        String trimmed = input == null ? "" : input.trim();
        if (trimmed.equalsIgnoreCase("cancel")) {
            pendingCustomInput.remove(player.getUniqueId());
            send(player, "custom-input-cancelled", Collections.emptyMap());
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(trimmed);
        } catch (NumberFormatException ex) {
            send(player, "invalid-number", Collections.emptyMap());
            return true;
        }

        if (amount < 1) {
            send(player, "invalid-number", Collections.emptyMap());
            return true;
        }

        if (amount > customInputMax) {
            send(player, "quantity-too-high", mapOf("max", customInputMax));
            return true;
        }

        pendingCustomInput.remove(player.getUniqueId());
        purchaseTickets(player, amount);
        return true;
    }

    public void clearPendingInput(Player player) {
        pendingCustomInput.remove(player.getUniqueId());
    }

    public boolean isWaitingForCustomInput(Player player) {
        return pendingCustomInput.containsKey(player.getUniqueId());
    }

    public ScratchLotteryPlugin getPlugin() {
        return plugin;
    }

    public void sendPlayerStats(Player player) {
        PlayerLotteryStats stats = getPlayerStats(player);
        send(player, "stats-header", mapOf("player", player.getName()));
        send(player, "stats-purchased", mapOf("amount", stats.getPurchasedTickets()));
        send(player, "stats-winning-tickets", mapOf("amount", stats.getWinningTicketCount()));
        send(player, "stats-spent", mapOf("amount", formatMoney(stats.getTotalSpent())));
        send(player, "stats-payout", mapOf("amount", formatMoney(stats.getTotalPayout())));
        send(player, "stats-net", mapOf("amount", formatSignedMoney(stats.getNet())));

        boolean hasPrizeData = false;
        for (PrizeTier tier : prizeTiers) {
            int count = stats.getPrizeWins().getOrDefault(tier.getKey(), 0);
            if (count <= 0) {
                continue;
            }

            hasPrizeData = true;
            send(player, "stats-prize-line", mapOf(
                    "prize", tier.getDisplayName(),
                    "count", count
            ));
        }

        if (!hasPrizeData) {
            send(player, "stats-no-win-data", Collections.emptyMap());
        }
    }

    public void sendPrizeList(CommandSender sender) {
        ensureBatchFresh();
        send(sender, "prize-list-header", Collections.emptyMap());
        send(sender, "prize-list-reset", mapOf("reset_in", formatResetRemaining()));

        for (PrizeTier tier : prizeTiers) {
            String itemsSuffix = tier.hasItemRewards()
                    ? " / アイテム: " + formatItemList(tier.getItemRewards())
                    : "";
            send(sender, "prize-list-line", mapOf(
                    "prize", tier.getDisplayName(),
                    "reward", formatMoney(tier.getReward()),
                    "remaining", remainingPrizeCounts.getOrDefault(tier.getKey(), 0),
                    "items_suffix", itemsSuffix
            ));
        }
    }

    public void purchaseTickets(Player player, int amount) {
        ensureBatchFresh();
        if (amount < 1) {
            send(player, "invalid-number", Collections.emptyMap());
            return;
        }

        if (amount > customInputMax) {
            send(player, "quantity-too-high", mapOf("max", customInputMax));
            return;
        }

        double totalCost = ticketPrice * amount;
        double balance = economy.getBalance(player);
        if (balance + 0.0001D < totalCost) {
            send(player, "insufficient-money", mapOf(
                    "cost", formatMoney(totalCost),
                    "balance", formatMoney(balance)
            ));
            return;
        }

        EconomyResponse withdraw = economy.withdrawPlayer(player, totalCost);
        if (!withdraw.transactionSuccess()) {
            send(player, "insufficient-money", mapOf(
                    "cost", formatMoney(totalCost),
                    "balance", formatMoney(balance)
            ));
            return;
        }

        PurchaseSummary summary = new PurchaseSummary(amount, totalCost);
        for (int i = 0; i < amount; i++) {
            ensureBatchAvailable();
            PrizeTier tier = drawTicket();
            if (tier == null) {
                summary.recordLoss();
            } else {
                summary.recordWin(tier);
            }
        }

        if (summary.getTotalPayout() > 0) {
            EconomyResponse deposit = economy.depositPlayer(player, summary.getTotalPayout());
            if (!deposit.transactionSuccess()) {
                plugin.getLogger().warning("払戻金の入金に失敗しました: " + player.getName());
            }
        }

        List<ItemStack> itemRewards = createSummaryItemRewards(summary);
        if (!itemRewards.isEmpty()) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(itemRewards.toArray(new ItemStack[0]));
            if (!leftovers.isEmpty()) {
                List<ItemStack> droppedItems = new ArrayList<>(leftovers.values());
                for (ItemStack item : droppedItems) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                send(player, "purchase-items-dropped", mapOf("items", formatItemList(droppedItems)));
            }
        }

        saveState();
        recordPlayerStats(player, summary);
        sendPurchaseSummary(player, summary);
        broadcastFirstPrize(player, summary);
    }

    public void sendReloadComplete(CommandSender sender) {
        send(sender, "reload-complete", Collections.emptyMap());
    }

    public void sendNoPermission(CommandSender sender) {
        send(sender, "no-permission", Collections.emptyMap());
    }

    public void sendPlayersOnly(CommandSender sender) {
        send(sender, "players-only", Collections.emptyMap());
    }

    private void loadValues() {
        ticketPrice = Math.max(0.01D, plugin.getConfig().getDouble("ticket-price", 300.0D));
        poolSize = Math.max(1, plugin.getConfig().getInt("pool-size", 100000));
        batchResetDays = plugin.getConfig().getInt("batch-reset-days", 7);
        customInputMax = Math.max(1, plugin.getConfig().getInt("custom-input-max", 1000));
        broadcastFirstPrize = plugin.getConfig().getBoolean("broadcast-first-prize", true);

        purchaseOptions = new ArrayList<>();
        for (int option : plugin.getConfig().getIntegerList("purchase-options")) {
            if (option > 0) {
                purchaseOptions.add(option);
            }
        }
        if (purchaseOptions.isEmpty()) {
            purchaseOptions.add(1);
            purchaseOptions.add(10);
            purchaseOptions.add(30);
        }

        prizeTiers = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("prizes");
        if (section == null) {
            throw new IllegalStateException("prizes section is missing in config.yml");
        }

        int totalWinningTickets = 0;
        for (String key : section.getKeys(false)) {
            String path = "prizes." + key + ".";
            String displayName = plugin.getConfig().getString(path + "display-name", key);
            double reward = plugin.getConfig().getDouble(path + "reward", 0);
            int winningTickets = plugin.getConfig().getInt(path + "winning-tickets", 0);
            if (reward < 0 || winningTickets < 0) {
                throw new IllegalStateException("Invalid prize configuration for " + key);
            }

            List<ItemStack> itemRewards = parseItemRewards(plugin.getConfig().getStringList(path + "item-rewards"), path + "item-rewards");
            prizeTiers.add(new PrizeTier(key, displayName, reward, winningTickets, itemRewards));
            totalWinningTickets += winningTickets;
        }

        if (prizeTiers.isEmpty()) {
            throw new IllegalStateException("At least one prize must be defined.");
        }

        if (totalWinningTickets > poolSize) {
            throw new IllegalStateException("Total winning ticket count cannot exceed pool-size.");
        }
    }

    private void loadState() {
        remainingPrizeCounts = new LinkedHashMap<>();
        if (!stateFile.exists()) {
            resetBatch();
            saveState();
            return;
        }

        YamlConfiguration state = YamlConfiguration.loadConfiguration(stateFile);
        lastResetAt = state.getLong("last-reset-at", System.currentTimeMillis());
        remainingTickets = state.getInt("remaining-tickets", poolSize);
        if (remainingTickets < 0 || remainingTickets > poolSize) {
            resetBatch();
            saveState();
            return;
        }

        int totalRemainingPrizes = 0;
        for (PrizeTier tier : prizeTiers) {
            int remaining = state.getInt("prizes." + tier.getKey(), tier.getWinningTickets());
            remaining = Math.max(0, Math.min(remaining, tier.getWinningTickets()));
            remainingPrizeCounts.put(tier.getKey(), remaining);
            totalRemainingPrizes += remaining;
        }

        if (totalRemainingPrizes > remainingTickets) {
            resetBatch();
            saveState();
            return;
        }

        ensureBatchFresh();
    }

    private void loadPlayerStats() {
        playerStats.clear();
        if (!playerStatsFile.exists()) {
            return;
        }

        YamlConfiguration statsConfig = YamlConfiguration.loadConfiguration(playerStatsFile);
        ConfigurationSection playersSection = statsConfig.getConfigurationSection("players");
        if (playersSection == null) {
            return;
        }

        for (String key : playersSection.getKeys(false)) {
            UUID uniqueId;
            try {
                uniqueId = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            String basePath = "players." + key + ".";
            Map<String, Integer> loadedPrizeWins = new LinkedHashMap<>();
            for (PrizeTier tier : prizeTiers) {
                int count = statsConfig.getInt(basePath + "prizes." + tier.getKey(), 0);
                if (count > 0) {
                    loadedPrizeWins.put(tier.getKey(), count);
                }
            }

            PlayerLotteryStats stats = new PlayerLotteryStats(uniqueId, statsConfig.getString(basePath + "name", ""));
            stats.restore(statsConfig.getString(basePath + "name", ""),
                    statsConfig.getInt(basePath + "purchased-tickets", 0),
                    statsConfig.getDouble(basePath + "total-spent", 0),
                    statsConfig.getDouble(basePath + "total-payout", 0),
                    loadedPrizeWins);
            playerStats.put(uniqueId, stats);
        }
    }

    private void saveState() {
        YamlConfiguration state = new YamlConfiguration();
        state.set("last-reset-at", lastResetAt);
        state.set("remaining-tickets", remainingTickets);
        for (PrizeTier tier : prizeTiers) {
            state.set("prizes." + tier.getKey(), remainingPrizeCounts.getOrDefault(tier.getKey(), 0));
        }

        try {
            state.save(stateFile);
        } catch (IOException e) {
            plugin.getLogger().warning("lottery-state.yml の保存に失敗しました: " + e.getMessage());
        }
    }

    private void savePlayerStats() {
        YamlConfiguration statsConfig = new YamlConfiguration();
        for (PlayerLotteryStats stats : playerStats.values()) {
            String basePath = "players." + stats.getUniqueId() + ".";
            statsConfig.set(basePath + "name", stats.getLastKnownName());
            statsConfig.set(basePath + "purchased-tickets", stats.getPurchasedTickets());
            statsConfig.set(basePath + "total-spent", stats.getTotalSpent());
            statsConfig.set(basePath + "total-payout", stats.getTotalPayout());
            for (PrizeTier tier : prizeTiers) {
                statsConfig.set(basePath + "prizes." + tier.getKey(), stats.getPrizeWins().getOrDefault(tier.getKey(), 0));
            }
        }

        try {
            statsConfig.save(playerStatsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("player-stats.yml の保存に失敗しました: " + e.getMessage());
        }
    }

    private void recordPlayerStats(Player player, PurchaseSummary summary) {
        PlayerLotteryStats stats = getPlayerStats(player);
        stats.recordPurchase(player.getName(), summary);
        savePlayerStats();
    }

    private PlayerLotteryStats getPlayerStats(Player player) {
        return playerStats.computeIfAbsent(player.getUniqueId(), id -> new PlayerLotteryStats(id, player.getName()));
    }

    private void resetBatch() {
        lastResetAt = System.currentTimeMillis();
        remainingTickets = poolSize;
        remainingPrizeCounts = new LinkedHashMap<>();
        for (PrizeTier tier : prizeTiers) {
            remainingPrizeCounts.put(tier.getKey(), tier.getWinningTickets());
        }
    }

    private void ensureBatchFresh() {
        if (batchResetDays <= 0) {
            return;
        }

        long resetIntervalMillis = Duration.ofDays(batchResetDays).toMillis();
        long now = System.currentTimeMillis();
        if (lastResetAt <= 0) {
            lastResetAt = now;
            saveState();
            return;
        }

        if (now - lastResetAt < resetIntervalMillis) {
            return;
        }

        resetBatch();
        saveState();
    }

    private void ensureBatchAvailable() {
        if (remainingTickets <= 0) {
            resetBatch();
            saveState();
        }
    }

    private PrizeTier drawTicket() {
        int draw = random.nextInt(remainingTickets) + 1;
        int cumulative = 0;
        PrizeTier winner = null;

        for (PrizeTier tier : prizeTiers) {
            int remaining = remainingPrizeCounts.getOrDefault(tier.getKey(), 0);
            cumulative += remaining;
            if (draw <= cumulative) {
                winner = tier;
                remainingPrizeCounts.put(tier.getKey(), Math.max(0, remaining - 1));
                break;
            }
        }

        remainingTickets--;
        return winner;
    }

    private void sendPurchaseSummary(Player player, PurchaseSummary summary) {
        send(player, "purchase-header", mapOf(
                "amount", summary.getAmount(),
                "cost", formatMoney(summary.getTotalCost())
        ));

        if (!summary.hasAnyWin()) {
            send(player, "purchase-no-win", Collections.emptyMap());
        } else {
            for (Map.Entry<PrizeTier, Integer> entry : summary.getWins().entrySet()) {
                PrizeTier tier = entry.getKey();
                int count = entry.getValue();
                String itemsSuffix = tier.hasItemRewards()
                        ? " / アイテム: " + formatItemList(createScaledItems(tier.getItemRewards(), count))
                        : "";
                send(player, "purchase-prize-line", mapOf(
                        "prize", tier.getDisplayName(),
                        "count", count,
                        "reward", formatMoney(tier.getReward() * count),
                        "items_suffix", itemsSuffix
                ));
            }
        }

        send(player, "purchase-total", mapOf(
                "payout", formatMoney(summary.getTotalPayout()),
                "net", formatSignedMoney(summary.getNet())
        ));
        send(player, "remaining-tickets", mapOf("remaining", remainingTickets));
    }

    private void broadcastFirstPrize(Player player, PurchaseSummary summary) {
        if (!broadcastFirstPrize || prizeTiers.isEmpty()) {
            return;
        }

        PrizeTier firstPrize = prizeTiers.get(0);
        Integer count = summary.getWins().get(firstPrize);
        if (count == null || count < 1) {
            return;
        }

        Component message = prefixedMessage("first-prize-broadcast", mapOf(
                "player", player.getName(),
                "prize", firstPrize.getDisplayName(),
                "reward", formatMoney(firstPrize.getReward() * count)
        ));
        Bukkit.broadcast(message);
    }

    private ItemStack createInfoItem() {
        return createItem(
                Material.BOOK,
                text("gui.info-name", Collections.emptyMap()),
                lore("gui.info-lore", mapOf(
                        "ticket_price", formatMoney(ticketPrice),
                        "remaining_tickets", remainingTickets,
                        "reset_in", formatResetRemaining()
                ))
        );
    }

    private ItemStack createPrizeListItem() {
        return createItem(
                Material.CHEST,
                text("gui.prize-list-name", Collections.emptyMap()),
                lore("gui.prize-list-lore", Collections.emptyMap())
        );
    }

    private ItemStack createPurchaseItem(int amount) {
        double totalCost = ticketPrice * amount;
        return createItem(
                Material.PAPER,
                text("gui.option-name", mapOf("amount", amount)),
                lore("gui.option-lore", mapOf(
                        "amount", amount,
                        "total_cost", formatMoney(totalCost)
                ))
        );
    }

    private ItemStack createCustomItem() {
        return createItem(
                Material.NAME_TAG,
                text("gui.custom-name", Collections.emptyMap()),
                lore("gui.custom-lore", mapOf("custom_max", customInputMax))
        );
    }

    private ItemStack createSimpleItem(Material material, String path, Map<String, Object> placeholders) {
        return createItem(material, text(path, placeholders), Collections.emptyList());
    }

    private ItemStack createItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private List<ItemStack> parseItemRewards(List<String> configuredItems, String path) {
        List<ItemStack> items = new ArrayList<>();
        for (String configuredItem : configuredItems) {
            if (configuredItem == null || configuredItem.isBlank()) {
                continue;
            }

            String[] split = configuredItem.split(":");
            Material material = Material.matchMaterial(split[0].trim());
            if (material == null || material.isAir()) {
                throw new IllegalStateException("Invalid item reward material at " + path + ": " + configuredItem);
            }

            int amount = 1;
            if (split.length >= 2) {
                try {
                    amount = Integer.parseInt(split[1].trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalStateException("Invalid item reward amount at " + path + ": " + configuredItem, ex);
                }
            }

            if (amount < 1) {
                throw new IllegalStateException("Item reward amount must be greater than 0 at " + path + ": " + configuredItem);
            }

            items.add(new ItemStack(material, amount));
        }
        return items;
    }

    private List<ItemStack> createSummaryItemRewards(PurchaseSummary summary) {
        List<ItemStack> items = new ArrayList<>();
        for (Map.Entry<PrizeTier, Integer> entry : summary.getWins().entrySet()) {
            items.addAll(createScaledItems(entry.getKey().getItemRewards(), entry.getValue()));
        }
        return items;
    }

    private List<ItemStack> createScaledItems(List<ItemStack> baseItems, int multiplier) {
        List<ItemStack> items = new ArrayList<>();
        if (multiplier <= 0 || baseItems.isEmpty()) {
            return items;
        }

        for (ItemStack baseItem : baseItems) {
            int totalAmount = baseItem.getAmount() * multiplier;
            int maxStack = Math.max(1, baseItem.getMaxStackSize());
            while (totalAmount > 0) {
                ItemStack stack = baseItem.clone();
                int stackAmount = Math.min(totalAmount, maxStack);
                stack.setAmount(stackAmount);
                items.add(stack);
                totalAmount -= stackAmount;
            }
        }
        return items;
    }

    private String formatItemList(List<ItemStack> items) {
        if (items.isEmpty()) {
            return "-";
        }

        List<String> parts = new ArrayList<>();
        for (ItemStack item : items) {
            String materialName = item.getType().getKey().getKey().replace('_', ' ');
            parts.add(materialName + " x" + item.getAmount());
        }
        return String.join(", ", parts);
    }

    private void fillInventory(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);

        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private Component text(String path, Map<String, Object> placeholders) {
        return TextUtil.miniMessage(getRequiredString(path), placeholders);
    }

    private List<Component> lore(String path, Map<String, Object> placeholders) {
        return TextUtil.miniMessageList(plugin.getConfig().getStringList(path), placeholders);
    }

    private Component prefixedMessage(String messageKey, Map<String, Object> placeholders) {
        String prefix = getRequiredString("messages.prefix");
        String body = getRequiredString("messages." + messageKey);
        return TextUtil.miniMessage(prefix + body, placeholders);
    }

    private void send(CommandSender sender, String messageKey, Map<String, Object> placeholders) {
        sender.sendMessage(prefixedMessage(messageKey, placeholders));
    }

    private String getRequiredString(String path) {
        return Objects.requireNonNull(plugin.getConfig().getString(path), "Missing config path: " + path);
    }

    private String formatMoney(double amount) {
        return MONEY_FORMAT.format(amount) + currencySuffix(amount);
    }

    private String formatSignedMoney(double amount) {
        String prefix = amount > 0 ? "+" : amount < 0 ? "-" : "";
        return prefix + formatMoney(Math.abs(amount));
    }

    private String formatResetRemaining() {
        if (batchResetDays <= 0) {
            return getRequiredString("messages.reset-disabled-label");
        }

        long resetAt = lastResetAt + Duration.ofDays(batchResetDays).toMillis();
        long remainingMillis = Math.max(0L, resetAt - System.currentTimeMillis());
        long totalMinutes = remainingMillis / 60000L;
        long days = totalMinutes / (24L * 60L);
        long hours = (totalMinutes % (24L * 60L)) / 60L;
        long minutes = totalMinutes % 60L;

        List<String> parts = new ArrayList<>();
        if (days > 0) {
            parts.add(days + "日");
        }
        if (hours > 0) {
            parts.add(hours + "時間");
        }
        if (minutes > 0) {
            parts.add(minutes + "分");
        }

        if (parts.isEmpty()) {
            return getRequiredString("messages.reset-less-than-minute-label");
        }

        return String.join("", parts);
    }

    private String currencySuffix(double amount) {
        String currency = economy.currencyNamePlural();
        String singular = economy.currencyNameSingular();
        String name = Math.abs(amount - 1.0D) < 0.0001D ? singular : currency;
        return name == null || name.isEmpty() ? "" : " " + name;
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }
}

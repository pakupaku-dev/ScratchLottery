package jp.pakupaku.scratchlottery.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PrizeTier {
    private final String key;
    private final String displayName;
    private final double reward;
    private final int winningTickets;
    private final List<ItemStack> itemRewards;

    public PrizeTier(String key, String displayName, double reward, int winningTickets, List<ItemStack> itemRewards) {
        this.key = key;
        this.displayName = displayName;
        this.reward = reward;
        this.winningTickets = winningTickets;
        this.itemRewards = cloneItems(itemRewards);
    }

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getReward() {
        return reward;
    }

    public int getWinningTickets() {
        return winningTickets;
    }

    public List<ItemStack> getItemRewards() {
        return cloneItems(itemRewards);
    }

    public boolean hasItemRewards() {
        return !itemRewards.isEmpty();
    }

    private static List<ItemStack> cloneItems(List<ItemStack> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItemStack> clones = new ArrayList<>();
        for (ItemStack item : items) {
            if (item != null) {
                clones.add(item.clone());
            }
        }
        return clones;
    }
}

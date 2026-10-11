package jp.pakupaku.scratchlottery.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerLotteryStats {
    private final UUID uniqueId;
    private String lastKnownName;
    private int purchasedTickets;
    private double totalSpent;
    private double totalPayout;
    private Map<String, Integer> prizeWins;

    public PlayerLotteryStats(UUID uniqueId, String lastKnownName) {
        this.uniqueId = uniqueId;
        this.lastKnownName = lastKnownName;
        this.prizeWins = new LinkedHashMap<>();
    }

    public void normalize() {
        if (prizeWins == null) {
            prizeWins = new LinkedHashMap<>();
        }
    }

    public void recordPurchase(String playerName, PurchaseSummary summary) {
        normalize();
        lastKnownName = playerName;
        purchasedTickets += summary.getAmount();
        totalSpent += summary.getTotalCost();
        totalPayout += summary.getTotalPayout();
        for (Map.Entry<PrizeTier, Integer> entry : summary.getWins().entrySet()) {
            prizeWins.merge(entry.getKey().getKey(), entry.getValue(), Integer::sum);
        }
    }

    public void restore(String playerName, int purchasedTickets, double totalSpent, double totalPayout, Map<String, Integer> prizeWins) {
        normalize();
        this.lastKnownName = playerName;
        this.purchasedTickets = Math.max(0, purchasedTickets);
        this.totalSpent = Math.max(0, totalSpent);
        this.totalPayout = Math.max(0, totalPayout);
        this.prizeWins.clear();
        if (prizeWins != null) {
            this.prizeWins.putAll(prizeWins);
        }
    }

    public UUID getUniqueId() {
        return uniqueId;
    }

    public String getLastKnownName() {
        return lastKnownName;
    }

    public int getPurchasedTickets() {
        return purchasedTickets;
    }

    public double getTotalSpent() {
        return totalSpent;
    }

    public double getTotalPayout() {
        return totalPayout;
    }

    public double getNet() {
        return totalPayout - totalSpent;
    }

    public int getWinningTicketCount() {
        normalize();
        return prizeWins.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, Integer> getPrizeWins() {
        normalize();
        return prizeWins;
    }
}

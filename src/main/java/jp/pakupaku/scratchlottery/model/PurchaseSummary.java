package jp.pakupaku.scratchlottery.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class PurchaseSummary {
    private final int amount;
    private final double totalCost;
    private final Map<PrizeTier, Integer> wins;
    private int losingTickets;
    private double totalPayout;

    public PurchaseSummary(int amount, double totalCost) {
        this.amount = amount;
        this.totalCost = totalCost;
        this.wins = new LinkedHashMap<>();
    }

    public void recordWin(PrizeTier tier) {
        wins.merge(tier, 1, Integer::sum);
        totalPayout += tier.getReward();
    }

    public void recordLoss() {
        losingTickets++;
    }

    public int getAmount() {
        return amount;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public int getLosingTickets() {
        return losingTickets;
    }

    public double getTotalPayout() {
        return totalPayout;
    }

    public double getNet() {
        return totalPayout - totalCost;
    }

    public boolean hasAnyWin() {
        return !wins.isEmpty();
    }

    public Map<PrizeTier, Integer> getWins() {
        return Collections.unmodifiableMap(wins);
    }
}

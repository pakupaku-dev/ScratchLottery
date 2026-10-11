package jp.kamei.scratchlottery.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;

public final class PurchaseMenuHolder implements InventoryHolder {
    private final Map<Integer, Integer> purchaseAmounts;
    private Inventory inventory;
    private int infoSlot;
    private int prizeListSlot;
    private int customSlot;
    private int closeSlot;

    public PurchaseMenuHolder() {
        this.purchaseAmounts = new HashMap<>();
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void addPurchaseSlot(int slot, int amount) {
        purchaseAmounts.put(slot, amount);
    }

    public Integer getPurchaseAmount(int slot) {
        return purchaseAmounts.get(slot);
    }

    public int getInfoSlot() {
        return infoSlot;
    }

    public void setInfoSlot(int infoSlot) {
        this.infoSlot = infoSlot;
    }

    public int getPrizeListSlot() {
        return prizeListSlot;
    }

    public void setPrizeListSlot(int prizeListSlot) {
        this.prizeListSlot = prizeListSlot;
    }

    public int getCustomSlot() {
        return customSlot;
    }

    public void setCustomSlot(int customSlot) {
        this.customSlot = customSlot;
    }

    public int getCloseSlot() {
        return closeSlot;
    }

    public void setCloseSlot(int closeSlot) {
        this.closeSlot = closeSlot;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}

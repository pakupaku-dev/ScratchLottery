package jp.kamei.scratchlottery;

import jp.kamei.scratchlottery.command.ScratchCommand;
import jp.kamei.scratchlottery.listener.LotteryListener;
import jp.kamei.scratchlottery.service.LotteryService;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class ScratchLotteryPlugin extends JavaPlugin {
    private Economy economy;
    private LotteryService lotteryService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!setupEconomy()) {
            getLogger().severe("Vault または経済プラグインが見つからないため、ScratchLottery を無効化します。");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        lotteryService = new LotteryService(this, economy);
        lotteryService.reload();

        ScratchCommand scratchCommand = new ScratchCommand(lotteryService);
        PluginCommand command = getCommand("scratch");
        if (command == null) {
            throw new IllegalStateException("scratch command is not defined in plugin.yml");
        }

        command.setExecutor(scratchCommand);
        command.setTabCompleter(scratchCommand);
        getServer().getPluginManager().registerEvents(new LotteryListener(lotteryService), this);
    }

    public LotteryService getLotteryService() {
        return lotteryService;
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> provider = getServer().getServicesManager().getRegistration(Economy.class);
        if (provider == null) {
            return false;
        }

        economy = provider.getProvider();
        return economy != null;
    }
}

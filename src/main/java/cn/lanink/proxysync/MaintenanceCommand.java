package cn.lanink.proxysync;

import dev.waterdog.waterdogpe.command.Command;
import dev.waterdog.waterdogpe.command.CommandSender;
import dev.waterdog.waterdogpe.command.CommandSettings;

/**
 * ProxySync 维护模式命令。
 * 用法：/ps mt <on [shutdown]|off|status>
 */
public class MaintenanceCommand extends Command {

    private final MaintenanceManager maintenanceManager;

    public MaintenanceCommand(MaintenanceManager maintenanceManager) {
        super("proxysync", CommandSettings.builder()
                .setDescription("ProxySync maintenance management")
                .setUsageMessage("/ps mt <on [shutdown]|off|status>")
                .setPermission("proxysync.maintenance")
                .setAliases(new String[]{"ps"})
                .build());
        this.maintenanceManager = maintenanceManager;
    }

    @Override
    public boolean onExecute(CommandSender sender, String alias, String[] args) {
        if (args.length < 2 || !"mt".equalsIgnoreCase(args[0])) {
            sender.sendMessage("§eUsage: /ps mt <on [shutdown]|off|status>");
            return false;
        }

        switch (args[1].toLowerCase()) {
            case "on" -> {
                boolean shutdown = args.length >= 3 && "shutdown".equalsIgnoreCase(args[2]);
                maintenanceManager.setMaintenanceMode(true);
                maintenanceManager.setShutdownWhenEmpty(shutdown);
                if (shutdown) {
                    sender.sendMessage("§aMaintenance mode enabled (shutdown when empty)");
                } else {
                    sender.sendMessage("§aMaintenance mode enabled");
                }
            }
            case "off" -> {
                maintenanceManager.setMaintenanceMode(false);
                maintenanceManager.setShutdownWhenEmpty(false);
                sender.sendMessage("§aMaintenance mode disabled");
            }
            case "status" -> {
                sender.sendMessage("§eMaintenance mode: " +
                        (maintenanceManager.isMaintenanceMode() ? "§aON" : "§cOFF"));
                sender.sendMessage("§eShutdown when empty: " +
                        (maintenanceManager.isShutdownWhenEmpty() ? "§aON" : "§cOFF"));
            }
            default -> {
                sender.sendMessage("§eUsage: /ps mt <on [shutdown]|off|status>");
                return false;
            }
        }
        return true;
    }
}

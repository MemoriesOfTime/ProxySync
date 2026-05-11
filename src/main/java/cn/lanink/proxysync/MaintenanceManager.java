package cn.lanink.proxysync;

import dev.waterdog.waterdogpe.event.defaults.InitialServerConnectedEvent;
import dev.waterdog.waterdogpe.event.defaults.PlayerLoginEvent;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import dev.waterdog.waterdogpe.scheduler.WaterdogScheduler;
import org.cloudburstmc.protocol.bedrock.packet.SetTitlePacket;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 维护模式管理器，封装维护模式的状态和逻辑。
 */
public class MaintenanceManager {

    private final ProxySync plugin;
    private volatile boolean maintenanceMode = false;
    private boolean shutdownWhenEmpty = false;
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);

    public MaintenanceManager(ProxySync plugin) {
        this.plugin = plugin;
    }

    public boolean isMaintenanceMode() {
        return maintenanceMode;
    }

    public void setMaintenanceMode(boolean enabled) {
        this.maintenanceMode = enabled;
    }

    public boolean isShutdownWhenEmpty() {
        return shutdownWhenEmpty;
    }

    public void setShutdownWhenEmpty(boolean enabled) {
        this.shutdownWhenEmpty = enabled;
    }

    /**
     * 处理新玩家登录事件。
     * 维护模式下无在线代理时，取消登录。
     */
    public void handlePlayerLogin(PlayerLoginEvent event) {
        if (!maintenanceMode) {
            return;
        }

        List<InetSocketAddress> onlineProxies = plugin.getRemoteProxyCounter().getOnlineProxies();
        if (onlineProxies.isEmpty()) {
            event.setCancelled(true);
            event.setCancelReason(plugin.getConfig().getString("maintenance-message",
                    "Server is under maintenance, please try again later."));
        }
    }

    /**
     * 处理玩家首次连接后端服务器完成事件。
     * 维护模式下有在线代理时，转移玩家到其他代理。
     */
    public void handleInitialServerConnected(InitialServerConnectedEvent event) {
        if (!maintenanceMode) {
            return;
        }

        List<InetSocketAddress> onlineProxies = plugin.getRemoteProxyCounter().getOnlineProxies();
        if (onlineProxies.isEmpty()) {
            return;
        }

        // 轮询选择目标代理
        int index = roundRobinIndex.getAndIncrement();
        InetSocketAddress target = onlineProxies.get(index % onlineProxies.size());

        String publicAddress = plugin.getConfig().getString("public-address", "");
        String transferHost = ProxySync.isLocalAddress(target.getHostString()) && !publicAddress.isEmpty()
                ? publicAddress : target.getHostString();
        int transferPort = target.getPort();

        int delayTicks = plugin.getConfig().getInt("maintenance-transfer-delay", 100);
        String titleText = plugin.getConfig().getString("maintenance-title", "§cServer Maintenance");
        String subtitleText = plugin.getConfig().getString("maintenance-subtitle", "§eTransferring to another server...");

        ProxiedPlayer player = event.getPlayer();

        // 延迟发送 title 提示，等待客户端世界加载
        WaterdogScheduler.getInstance().scheduleDelayed(() -> {
            if (player.isConnected()) {
                sendTitle(player, titleText, subtitleText);
            }
        }, 20);

        // 延迟发送 TransferPacket
        WaterdogScheduler.getInstance().scheduleDelayed(() -> {
            if (player.isConnected()) {
                TransferPacket packet = new TransferPacket();
                packet.setAddress(transferHost);
                packet.setPort(transferPort);
                player.sendPacket(packet);
                plugin.getLogger().info("ProxySync: maintenance mode, transferring {} to {}:{}",
                        player.getName(), transferHost, transferPort);
            }
        }, delayTicks);
    }

    /**
     * 处理玩家断开事件。
     * 维护模式+自动关机开启时，无玩家则关闭代理。
     */
    public void handlePlayerDisconnected() {
        if (!maintenanceMode || !shutdownWhenEmpty) {
            return;
        }

        // 延迟 1 tick 确保玩家已从列表移除
        WaterdogScheduler.getInstance().scheduleDelayed(() -> {
            if (plugin.getProxy().getPlayers().isEmpty()) {
                plugin.getLogger().info("ProxySync: maintenance mode, no players remaining, shutting down proxy");
                plugin.getProxy().shutdown();
            }
        }, 1);
    }

    private static void sendTitle(ProxiedPlayer player, String title, String subtitle) {
        SetTitlePacket titlePacket = new SetTitlePacket();
        titlePacket.setType(SetTitlePacket.Type.TITLE);
        titlePacket.setText(title);
        titlePacket.setFadeInTime(10);
        titlePacket.setStayTime(60);
        titlePacket.setFadeOutTime(10);
        titlePacket.setXuid("");
        titlePacket.setPlatformOnlineId("");
        player.sendPacket(titlePacket);

        SetTitlePacket subtitlePacket = new SetTitlePacket();
        subtitlePacket.setType(SetTitlePacket.Type.SUBTITLE);
        subtitlePacket.setText(subtitle);
        subtitlePacket.setFadeInTime(10);
        subtitlePacket.setStayTime(60);
        subtitlePacket.setFadeOutTime(10);
        subtitlePacket.setXuid("");
        subtitlePacket.setPlatformOnlineId("");
        player.sendPacket(subtitlePacket);
    }
}

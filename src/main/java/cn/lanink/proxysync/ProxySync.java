package cn.lanink.proxysync;

import dev.waterdog.waterdogpe.event.defaults.InitialServerConnectedEvent;
import dev.waterdog.waterdogpe.event.defaults.PlayerDisconnectedEvent;
import dev.waterdog.waterdogpe.event.defaults.PlayerLoginEvent;
import dev.waterdog.waterdogpe.event.defaults.ProxyPingEvent;
import dev.waterdog.waterdogpe.event.defaults.ProxyQueryEvent;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import dev.waterdog.waterdogpe.plugin.Plugin;
import org.cloudburstmc.protocol.bedrock.packet.TransferPacket;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProxySync extends Plugin {

    private RemoteProxyCounter remoteProxyCounter;
    private MaintenanceManager maintenanceManager;

    @Override
    public void onEnable() {
        createDefaultConfig();

        List<InetSocketAddress> addresses = parseRemoteAddresses();
        if (addresses.isEmpty()) {
            getLogger().warn("ProxySync: no remote proxies configured, plugin will not function");
            return;
        }

        int interval = getConfig().getInt("query-interval", 30);
        boolean aggregateCount = getConfig().getBoolean("aggregate-player-count", false);
        boolean aggregateMax = getConfig().getBoolean("aggregate-max-players", false);

        remoteProxyCounter = new RemoteProxyCounter(addresses, getLogger());
        remoteProxyCounter.start(interval);

        getProxy().getEventManager().subscribe(ProxyPingEvent.class, event -> handleEvent(event, aggregateCount, aggregateMax));
        getProxy().getEventManager().subscribe(ProxyQueryEvent.class, event -> handleEvent(event, aggregateCount, aggregateMax));

        maintenanceManager = new MaintenanceManager(this);
        getProxy().getEventManager().subscribe(PlayerLoginEvent.class, maintenanceManager::handlePlayerLogin);
        getProxy().getEventManager().subscribe(InitialServerConnectedEvent.class, maintenanceManager::handleInitialServerConnected);
        getProxy().getEventManager().subscribe(PlayerDisconnectedEvent.class, event -> maintenanceManager.handlePlayerDisconnected());
        getProxy().getCommandMap().registerCommand(new MaintenanceCommand(maintenanceManager));

        getLogger().info("ProxySync enabled, monitoring {} remote proxies", addresses.size());
    }

    @Override
    public void onDisable() {
        if (remoteProxyCounter != null) {
            if (getConfig().getBoolean("transfer-on-shutdown", true)) {
                transferPlayersOnShutdown();
            }
            remoteProxyCounter.shutdown();
        }
    }

    private void transferPlayersOnShutdown() {
        List<InetSocketAddress> onlineProxies = remoteProxyCounter.getOnlineProxies();
        if (onlineProxies.isEmpty()) {
            getLogger().warn("ProxySync: no online remote proxies available, cannot transfer players");
            return;
        }

        Collection<ProxiedPlayer> players = getProxy().getPlayers().values();
        if (players.isEmpty()) {
            return;
        }

        String publicAddress = getConfig().getString("public-address", "");

        getLogger().info("ProxySync: transferring {} players to {} remote proxies", players.size(), onlineProxies.size());

        int index = 0;
        for (ProxiedPlayer player : players) {
            if (!player.isConnected()) {
                continue;
            }
            InetSocketAddress target = onlineProxies.get(index % onlineProxies.size());
            String transferHost = isLocalAddress(target.getHostString()) && !publicAddress.isEmpty()
                    ? publicAddress : target.getHostString();
            TransferPacket packet = new TransferPacket();
            packet.setAddress(transferHost);
            packet.setPort(target.getPort());
            player.sendPacket(packet);
            getLogger().info("ProxySync: transferring {} to {}:{}", player.getName(), transferHost, target.getPort());
            index++;
        }
    }

    private void handleEvent(ProxyPingEvent event, boolean aggregateCount, boolean aggregateMax) {
        if (aggregateCount) {
            int remotePlayerCount = remoteProxyCounter.getTotalPlayerCount();
            event.setPlayerCount(event.getPlayerCount() + remotePlayerCount);
        }

        if (aggregateMax) {
            int remoteMaxCount = remoteProxyCounter.getTotalMaxPlayerCount();
            event.setMaximumPlayerCount(event.getMaximumPlayerCount() + remoteMaxCount);
        }
    }

    private List<InetSocketAddress> parseRemoteAddresses() {
        List<InetSocketAddress> addresses = new ArrayList<>();
        List<String> proxies = getConfig().getStringList("remote-proxies");
        if (proxies == null) {
            return addresses;
        }

        int localPort = getProxy().getConfiguration().getBindAddress().getPort();

        for (String entry : proxies) {
            String[] parts = entry.split(":");
            if (parts.length == 2) {
                try {
                    String host = parts[0].trim();
                    int port = Integer.parseInt(parts[1].trim());
                    if (isLocalAddress(host) && port == localPort) {
                        getLogger().info("ProxySync: skipping local proxy address: {}", entry);
                        continue;
                    }
                    addresses.add(new InetSocketAddress(host, port));
                } catch (NumberFormatException e) {
                    getLogger().warn("ProxySync: invalid address format: {}", entry);
                }
            } else {
                getLogger().warn("ProxySync: invalid address format: {}", entry);
            }
        }
        return addresses;
    }

    public RemoteProxyCounter getRemoteProxyCounter() {
        return remoteProxyCounter;
    }

    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    static boolean isLocalAddress(String host) {
        return "127.0.0.1".equals(host) || "localhost".equals(host) || "::1".equals(host);
    }

    private void createDefaultConfig() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdir();
        }
        File file = new File(getDataFolder(), "config.yml");
        if (!file.exists()) {
            try (InputStream in = getResourceFile("config.yml")) {
                Files.copy(in, file.toPath());
            } catch (IOException e) {
                getLogger().error("ProxySync: failed to create default config", e);
            }
        }
    }
}

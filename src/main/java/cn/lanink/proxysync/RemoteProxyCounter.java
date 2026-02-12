package cn.lanink.proxysync;

import dev.waterdog.waterdogpe.scheduler.TaskHandler;
import dev.waterdog.waterdogpe.scheduler.WaterdogScheduler;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时查询所有远程 WDPE 代理的在线人数，缓存并提供聚合结果。
 */
public class RemoteProxyCounter {

    private static final int TICKS_PER_SECOND = 20;

    private final List<InetSocketAddress> remoteAddresses;
    private final ConcurrentHashMap<String, int[]> cache = new ConcurrentHashMap<>();
    private final Logger logger;
    private TaskHandler<?> taskHandler;

    public RemoteProxyCounter(List<InetSocketAddress> remoteAddresses, Logger logger) {
        this.remoteAddresses = remoteAddresses;
        this.logger = logger;
    }

    public void start(int intervalSeconds) {
        int periodTicks = intervalSeconds * TICKS_PER_SECOND;
        taskHandler = WaterdogScheduler.getInstance().scheduleRepeating(this::queryAll, periodTicks, true);
        logger.info("ProxySync: remote proxy query started, interval: {}s, targets: {}", intervalSeconds, remoteAddresses.size());
    }

    private void queryAll() {
        for (InetSocketAddress address : remoteAddresses) {
            try {
                int[] result = RemoteProxyQuery.query(address);
                String key = address.getHostString() + ":" + address.getPort();
                cache.put(key, result);
                logger.debug("ProxySync: query {} -> players={}, max={}", key, result[0], result[1]);
            } catch (Exception e) {
                logger.error("ProxySync: error querying {}", address, e);
            }
        }
    }

    /**
     * 获取所有远程代理的在线人数总和（忽略查询失败的代理）。
     */
    public int getTotalPlayerCount() {
        int total = 0;
        for (int[] counts : cache.values()) {
            if (counts[0] > 0) {
                total += counts[0];
            }
        }
        return total;
    }

    /**
     * 获取所有远程代理的最大人数总和（忽略查询失败的代理）。
     */
    public int getTotalMaxPlayerCount() {
        int total = 0;
        for (int[] counts : cache.values()) {
            if (counts[1] > 0) {
                total += counts[1];
            }
        }
        return total;
    }

    /**
     * 获取当前在线的远程代理地址列表（查询成功且人数>=0的代理）。
     */
    public List<InetSocketAddress> getOnlineProxies() {
        List<InetSocketAddress> online = new ArrayList<>();
        for (InetSocketAddress address : remoteAddresses) {
            String key = address.getHostString() + ":" + address.getPort();
            int[] counts = cache.get(key);
            if (counts != null && counts[0] >= 0) {
                online.add(address);
            }
        }
        return online;
    }

    public void shutdown() {
        if (taskHandler != null) {
            taskHandler.cancel();
        }
        logger.info("ProxySync: remote proxy query stopped");
    }
}

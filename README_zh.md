# ProxySync

[English](README.md) | **中文**

一个 WaterdogPE 插件，用于在多个代理实例之间同步玩家数量并在关服时自动转移玩家。

## 功能特性

- **聚合玩家人数** - 汇总多个 WaterdogPE 代理的玩家数量，在 MOTD/Query 响应中显示总人数
- **关服玩家转移** - 当前代理关闭时，自动将在线玩家轮询分配到其他在线代理
- **远程代理监控** - 通过 Minecraft 基岩版 UDP MOTD 协议定时查询远程代理的状态和玩家数量
- **公网地址转换** - 支持在 NAT 环境下将 localhost 地址替换为公网 IP，确保玩家转移正常

## 环境要求

- Java 17+
- WaterdogPE 2.0.4-SNAPSHOT 或兼容版本

## 构建

```bash
mvn clean package
```

编译产物位于 `target/ProxySync-1.0.0-SNAPSHOT.jar`。

## 安装

1. 构建插件或下载 JAR 文件
2. 将 JAR 放入 WaterdogPE 代理的 `plugins/` 目录
3. 启动代理以生成默认配置文件
4. 编辑 `plugins/ProxySync/config.yml` 配置远程代理地址
5. 重启代理

## 配置说明

`plugins/ProxySync/config.yml`：

```yaml
# 远程 WDPE 代理地址 (IP:端口)
remote-proxies:
  - "192.168.1.10:19132"
  - "192.168.1.11:19132"

# 查询间隔（秒）
query-interval: 30

# 聚合在线玩家数量（true=所有代理总和，false=仅本地）
aggregate-player-count: false

# 聚合最大玩家数量（true=所有代理总和，false=仅本地配置）
aggregate-max-players: false

# 关服时将玩家转移到其他在线代理
transfer-on-shutdown: true

# 用于玩家转移的公网 IP（替换 127.0.0.1）
# 留空则使用原始地址
public-address: ""
```

| 配置项 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `remote-proxies` | 列表 | - | 远程 WaterdogPE 代理地址列表，格式为 `IP:端口` |
| `query-interval` | 整数 | 30 | 查询远程代理的间隔时间（秒） |
| `aggregate-player-count` | 布尔值 | false | 将远程代理的玩家数量累加到 MOTD/Query 响应中 |
| `aggregate-max-players` | 布尔值 | false | 将远程代理的最大玩家数累加到 MOTD/Query 响应中 |
| `transfer-on-shutdown` | 布尔值 | true | 关服时将玩家转移到在线的远程代理 |
| `public-address` | 字符串 | "" | 转移数据包中替换 localhost 的公网 IP |

## 工作原理

1. 启动时解析配置的远程代理地址（自动跳过本地代理自身地址）
2. 定时任务周期性地向每个远程代理发送 UDP MOTD 查询包，缓存查询结果（在线人数 + 最大人数）
3. 开启 `aggregate-player-count` 或 `aggregate-max-players` 后，插件拦截 `ProxyPingEvent` 和 `ProxyQueryEvent` 事件，将远程代理的人数累加到响应中
4. 代理关闭时（开启 `transfer-on-shutdown`），通过 `TransferPacket` 将在线玩家轮询分配到各个在线的远程代理

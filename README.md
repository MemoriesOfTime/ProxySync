# ProxySync

**English** | [中文](README_zh.md)

A WaterdogPE plugin that synchronizes player counts and transfers players across multiple proxy instances.

## Features

- **Aggregated Player Count** - Combines player counts from multiple WaterdogPE proxies and displays the total in MOTD/Query responses
- **Player Transfer on Shutdown** - Automatically transfers connected players to other online proxies when the current proxy shuts down, using round-robin distribution
- **Remote Proxy Monitoring** - Periodically queries remote proxies via the Minecraft Bedrock UDP MOTD protocol to track their status and player counts
- **Public Address Translation** - Supports replacing localhost addresses with a public IP for player transfer packets in NAT environments

## Requirements

- Java 17+
- WaterdogPE 2.0.4-SNAPSHOT or compatible

## Build

```bash
mvn clean package
```

The compiled JAR will be at `target/ProxySync-1.0.0-SNAPSHOT.jar`.

## Installation

1. Build the plugin or download the JAR
2. Place the JAR in the `plugins/` directory of your WaterdogPE proxy
3. Start the proxy to generate the default configuration
4. Edit `plugins/ProxySync/config.yml` to configure remote proxy addresses
5. Restart the proxy

## Configuration

`plugins/ProxySync/config.yml`:

```yaml
# Remote WDPE proxy addresses (IP:port)
remote-proxies:
  - "192.168.1.10:19132"
  - "192.168.1.11:19132"

# Query interval (seconds)
query-interval: 30

# Aggregate online player count (true=sum of all proxies, false=local only)
aggregate-player-count: false

# Aggregate max player count (true=sum of all proxies, false=local config only)
aggregate-max-players: false

# Transfer players to other online proxies on shutdown
transfer-on-shutdown: true

# Public IP for client transfer (replaces 127.0.0.1 in transfer packets)
# Leave empty to use the address as-is
public-address: ""
```

| Option | Type | Default | Description |
|---|---|---|---|
| `remote-proxies` | List | - | List of remote WaterdogPE proxy addresses in `IP:port` format |
| `query-interval` | Integer | 30 | Interval in seconds between remote proxy queries |
| `aggregate-player-count` | Boolean | false | Add remote player counts to MOTD/Query responses |
| `aggregate-max-players` | Boolean | false | Add remote max player slots to MOTD/Query responses |
| `transfer-on-shutdown` | Boolean | true | Transfer players to online remote proxies on shutdown |
| `public-address` | String | "" | Public IP to replace localhost in transfer packets |

## How It Works

1. On startup, the plugin parses configured remote proxy addresses (automatically skipping the local proxy's own address)
2. A scheduled task periodically sends UDP MOTD query packets to each remote proxy and caches the results (online count + max count)
3. When `aggregate-player-count` or `aggregate-max-players` is enabled, the plugin intercepts `ProxyPingEvent` and `ProxyQueryEvent` to add remote counts to the response
4. On proxy shutdown (if `transfer-on-shutdown` is enabled), connected players are distributed to online remote proxies via `TransferPacket` using round-robin

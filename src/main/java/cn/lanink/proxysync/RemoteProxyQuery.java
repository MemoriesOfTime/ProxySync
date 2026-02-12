package cn.lanink.proxysync;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

/**
 * UDP MOTD 查询工具类，向远程 WDPE 代理发送查询包并解析在线人数和最大人数。
 */
public class RemoteProxyQuery {

    private static final int TIMEOUT_MS = 3000;

    private static final byte[] QUERY_PACKET = new byte[]{
            0x01,
            0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x00, (byte) 0xFF, (byte) 0xFF, 0x00, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE, (byte) 0xFE,
            (byte) 0xFD, (byte) 0xFD, (byte) 0xFD, (byte) 0xFD,
            0x12, 0x34, 0x56, 0x78,
            0x00, 0x00, 0x00, 0x00, 0x00
    };

    /**
     * 查询远程代理的在线人数和最大人数。
     *
     * @param address 远程代理地址
     * @return int[2]：[0]=在线人数, [1]=最大人数；查询失败返回 {-1, -1}
     */
    public static int[] query(InetSocketAddress address) {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(TIMEOUT_MS);

            DatagramPacket sendPacket = new DatagramPacket(
                    QUERY_PACKET, QUERY_PACKET.length, address
            );
            socket.send(sendPacket);

            byte[] buffer = new byte[1024];
            DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
            socket.receive(receivePacket);

            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
            String[] data = response.split(";");
            // data[4] = 在线人数, data[5] = 最大人数
            if (data.length > 5) {
                return new int[]{Integer.parseInt(data[4]), Integer.parseInt(data[5])};
            }
        } catch (Exception ignored) {
        }
        return new int[]{-1, -1};
    }
}

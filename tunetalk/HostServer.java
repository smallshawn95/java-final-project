package tunetalk;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

public class HostServer {
    // 記錄所有連線進來的 Client (Key = IP:Port, Value = 暱稱)
    private static Map<String, String> clientNames = new HashMap<>();

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket(5000);
            byte[] buffer = new byte[4096];

            System.out.println("【房主伺服器】已啟動 (Port: 5000)，等待連線...");

            while (true) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String senderIP = packet.getAddress().getHostAddress();
                int senderPort = packet.getPort();
                String clientKey = senderIP + ":" + senderPort;
                
                String message = new String(packet.getData(), 0, packet.getLength());
                boolean listChanged = false;

                // 1. 處理新朋友加入
                if (message.startsWith("[JOIN]")) {
                    String nickname = message.substring(6).trim();
                    if (nickname.isEmpty()) nickname = "無名氏";
                    
                    clientNames.put(clientKey, nickname);
                    System.out.println("➡️ 新朋友加入: " + nickname + " (" + clientKey + ")");
                    listChanged = true;
                } 
                // 🌟 2. 處理朋友主動退出房間
                else if (message.startsWith("[LEAVE]")) {
                    String nickname = clientNames.remove(clientKey);
                    System.out.println("⬅️ 朋友離開房間: " + (nickname != null ? nickname : "未知") + " (" + clientKey + ")");
                    listChanged = true;
                }
                // 3. 預防機制
                else if (!clientNames.containsKey(clientKey)) {
                    clientNames.put(clientKey, "匿名_" + senderPort);
                    listChanged = true;
                }

                // 如果名單有變動，廣播最新的「暱稱名單」給目前在線的所有人
                if (listChanged) {
                    broadcastUserList(socket);
                }

                // 轉發語音邏輯 (過濾掉控制文字封包，只轉發純聲音位元組)
                if (!message.startsWith("[JOIN]") && !message.startsWith("[LEAVE]") && !message.startsWith("[USER_LIST]")) {
                    for (String client : clientNames.keySet()) {
                        if (!client.equals(clientKey)) {
                            String[] parts = client.split(":");
                            InetAddress targetIp = InetAddress.getByName(parts[0]);
                            int targetPort = Integer.parseInt(parts[1]);

                            DatagramPacket relayPacket = new DatagramPacket(
                                    packet.getData(), packet.getLength(), targetIp, targetPort);
                            socket.send(relayPacket);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void broadcastUserList(DatagramSocket socket) throws Exception {
        // 如果房間空了，傳送提示字串
        String listMsg = "[USER_LIST]" + (clientNames.isEmpty() ? "暫無使用者" : String.join("\n", clientNames.values()));
        byte[] listData = listMsg.getBytes();

        for (String client : clientNames.keySet()) {
            String[] parts = client.split(":");
            InetAddress targetIp = InetAddress.getByName(parts[0]);
            int targetPort = Integer.parseInt(parts[1]);
            socket.send(new DatagramPacket(listData, listData.length, targetIp, targetPort));
        }
    }
}
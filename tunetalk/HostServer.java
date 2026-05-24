package tunetalk;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

public class HostServer {
    // 通訊錄：用來記錄所有連線進來的 Client (格式為 IP:Port)
    private static Set<String> clientList = new HashSet<>();

    public static void main(String[] args) {
        try {
            // 房主固定在 Port 5000 接收訊息
            DatagramSocket socket = new DatagramSocket(5000);
            byte[] buffer = new byte[4096];

            System.out.println("【房主伺服器（純語音轉發版）】已啟動 (Port: 5000)，等待朋友連線...");

            while (true) {
                // 1. 接收任何人的語音封包
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String senderIP = packet.getAddress().getHostAddress();
                int senderPort = packet.getPort();
                String clientKey = senderIP + ":" + senderPort; // 組合出獨一無二的 ID

                // 2. 如果是新朋友，加進通訊錄
                if (clientList.add(clientKey)) {
                    System.out.println("➡️ 新朋友加入房間: " + clientKey);
                }

                // 💡 為了不破壞音訊，我們不把 packet 轉成 String 印出了
                // System.out.println("轉發語音中，來自: " + clientKey + "，大小: " + packet.getLength() + " bytes");

                // 3. 核心邏輯：原封不動地轉發純位元組給「除了發送者以外」的所有人
                for (String client : clientList) {
                    if (!client.equals(clientKey)) {
                        String[] parts = client.split(":");
                        InetAddress targetIp = InetAddress.getByName(parts[0]);
                        int targetPort = Integer.parseInt(parts[1]);

                        // 🌟 關鍵修正：直接轉發 packet.getData()，不加任何打字訊息，確保音訊格式完美對齊
                        DatagramPacket relayPacket = new DatagramPacket(
                                packet.getData(), packet.getLength(), targetIp, targetPort);
                        socket.send(relayPacket);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

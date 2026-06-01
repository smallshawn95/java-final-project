package tunetalk;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HostServer {
    private static Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
    private static volatile boolean isRunning = false;
    private static DatagramSocket socket;

    static class ClientInfo {
        String nickname;
        long lastActiveTime;
        ClientInfo(String name) { 
            this.nickname = name; 
            this.lastActiveTime = System.currentTimeMillis(); 
        }
    }

    public static void startServer() {
        if (isRunning) return; 
        isRunning = true;
        clients.clear();

        // 啟動清理斷線使用者的執行緒
        Thread cleanerThread = new Thread(() -> {
            while (isRunning) {
                try {
                    Thread.sleep(5000); 
                    long now = System.currentTimeMillis();
                    boolean listChanged = false;

                    for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
                        if (now - entry.getValue().lastActiveTime > 10000) {
                            System.out.println("⚠️ 偵測到斷線，移除: " + entry.getValue().nickname);
                            clients.remove(entry.getKey());
                            listChanged = true;
                        }
                    }
                    if (listChanged && socket != null && !socket.isClosed()) {
                        broadcastUserList(socket);
                    }
                } catch (Exception e) {}
            }
        });
        cleanerThread.start();

        new Thread(() -> {
            try {
                socket = new DatagramSocket(5000);
                byte[] buffer = new byte[8192];
                System.out.println("【房主伺服器】已啟動 (Port: 5000)...");

                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet); 

                    String senderKey = packet.getAddress().getHostAddress() + ":" + packet.getPort();
                    String message = new String(packet.getData(), 0, Math.min(packet.getLength(), 100), "UTF-8");

                    // 1. 處理控制指令
                    if (message.startsWith("[JOIN]")) {
                        String nickname = message.substring(6).trim();
                        clients.put(senderKey, new ClientInfo(nickname));
                        System.out.println("✅ " + nickname + " 加入房間");
                        broadcastUserList(socket);
                        continue; 
                    } else if (message.startsWith("[LEAVE]")) {
                        if (clients.containsKey(senderKey)) {
                            System.out.println("👋 " + clients.get(senderKey).nickname + " 離開房間");
                            clients.remove(senderKey);
                            broadcastUserList(socket);
                        }
                        continue;
                    } else if (message.startsWith("[PING]")) {
                        if (clients.containsKey(senderKey)) {
                            clients.get(senderKey).lastActiveTime = System.currentTimeMillis();
                        }
                        continue;
                    }

                    // 2. 處理語音資料：封裝發言者資訊並廣播
                    if (clients.containsKey(senderKey)) {
                        ClientInfo senderInfo = clients.get(senderKey);
                        senderInfo.lastActiveTime = System.currentTimeMillis();

                        // 建立帶有身分標頭的特殊語音封包
                        byte[] prefix = "[AUDIO]".getBytes("UTF-8");
                        byte[] nameBytes = senderInfo.nickname.getBytes("UTF-8");
                        int audioLen = packet.getLength();
                        
                        // 新結構: [AUDIO](7 bytes) + 名字長度(1 byte) + 名字字串(N bytes) + 聲音PCM數據
                        byte[] forwardBuffer = new byte[prefix.length + 1 + nameBytes.length + audioLen];
                        System.arraycopy(prefix, 0, forwardBuffer, 0, prefix.length);
                        forwardBuffer[prefix.length] = (byte) nameBytes.length;
                        System.arraycopy(nameBytes, 0, forwardBuffer, prefix.length + 1, nameBytes.length);
                        System.arraycopy(packet.getData(), 0, forwardBuffer, prefix.length + 1 + nameBytes.length, audioLen);

                        // 廣播給房間內的其他所有人
                        for (String targetKey : clients.keySet()) {
                            if (!targetKey.equals(senderKey)) {
                                String[] parts = targetKey.split(":");
                                InetAddress targetIp = InetAddress.getByName(parts[0]);
                                int targetPort = Integer.parseInt(parts[1]);
                                socket.send(new DatagramPacket(forwardBuffer, forwardBuffer.length, targetIp, targetPort));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (isRunning) e.printStackTrace();
            } finally {
                System.out.println("【房主伺服器】已完全關閉。");
            }
        }).start();
    }

    public static void stopServer() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close(); 
        }
    }

    private static void broadcastUserList(DatagramSocket socket) throws Exception {
        StringBuilder sb = new StringBuilder();
        if (clients.isEmpty()) {
            sb.append("暫無使用者");
        } else {
            for (ClientInfo info : clients.values()) {
                sb.append(info.nickname).append("\n");
            }
        }
        
        String listMsg = "[USER_LIST]" + sb.toString().trim();
        byte[] listData = listMsg.getBytes("UTF-8");

        for (String clientKey : clients.keySet()) {
            String[] parts = clientKey.split(":");
            InetAddress targetIp = InetAddress.getByName(parts[0]);
            int targetPort = Integer.parseInt(parts[1]);
            socket.send(new DatagramPacket(listData, listData.length, targetIp, targetPort));
        }
    }
}
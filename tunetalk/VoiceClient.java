package tunetalk;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.function.Consumer;

public class VoiceClient {
    private DatagramSocket socket;
    private TargetDataLine microphone;
    private SourceDataLine speaker;
    private volatile boolean isRunning = false; // 控制發送與接收迴圈的開關
    private String targetIp;
    private final int hostPort = 5000;

    private static AudioFormat getAudioFormat() {
        return new AudioFormat(16000.0f, 16, 1, true, false);
    }

    public void startClient(String targetIp, String nickname, Consumer<String> onUserListUpdate) {
        this.targetIp = targetIp;
        this.isRunning = true;
        
        try {
            socket = new DatagramSocket();
            InetAddress hostIP = InetAddress.getByName(targetIp);
            AudioFormat format = getAudioFormat();
            int frameSize = format.getFrameSize();

            // 【接收區 (喇叭)】
            Thread receiveThread = new Thread(() -> {
                try {
                    DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
                    speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
                    speaker.open(format);
                    speaker.start();

                    byte[] buffer = new byte[4096];
                    while (isRunning) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        int length = packet.getLength();

                        String possibleText = new String(packet.getData(), 0, length);
                        if (possibleText.startsWith("[USER_LIST]")) {
                            String users = possibleText.replace("[USER_LIST]", "");
                            if (onUserListUpdate != null) {
                                onUserListUpdate.accept(users);
                            }
                            continue; 
                        }

                        int validLength = (length / frameSize) * frameSize;
                        if (validLength > 0 && speaker != null && speaker.isOpen()) {
                            speaker.write(packet.getData(), 0, validLength);
                        }
                    }
                } catch (Exception e) {
                    // 當 socket 被外部關閉時會觸發異常，這裡不做處理，讓執行緒優雅結束
                }
            });
            receiveThread.start();

            // 剛進房間，先發送加入訊息與暱稱
            String joinMsg = "[JOIN]" + nickname;
            byte[] initData = joinMsg.getBytes();
            socket.send(new DatagramPacket(initData, initData.length, hostIP, hostPort));

            // 【發送區 (麥克風)】
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphone.open(format);
            microphone.start();

            byte[] micBuffer = new byte[4096];
            while (isRunning) {
                int bytesRead = microphone.read(micBuffer, 0, micBuffer.length);
                if (bytesRead > 0 && isRunning) {
                    int validRead = (bytesRead / frameSize) * frameSize;
                    if (validRead > 0) {
                        DatagramPacket packet = new DatagramPacket(micBuffer, validRead, hostIP, hostPort);
                        socket.send(packet);
                    }
                }
            }
        } catch (Exception e) {
            if (isRunning) e.printStackTrace();
        }
    }

    // 🌟 關鍵新增：退出房間並釋放所有硬體裝置與網路 Socket
    public void stopClient() {
        this.isRunning = false;
        
        try {
            // 1. 發送離開訊號給伺服器
            if (socket != null && !socket.isClosed() && targetIp != null) {
                byte[] leaveData = "[LEAVE]".getBytes();
                InetAddress hostIP = InetAddress.getByName(targetIp);
                socket.send(new DatagramPacket(leaveData, leaveData.length, hostIP, hostPort));
            }
        } catch (Exception e) {
            // 忽略發送失敗的狀況
        }

        // 2. 關閉音訊實體裝置（非常重要，不關閉的話下次開房會卡死）
        if (microphone != null) {
            microphone.stop();
            microphone.close();
        }
        if (speaker != null) {
            speaker.stop();
            speaker.close();
        }

        // 3. 關閉網路通訊埠
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        System.out.println("❌ 語音客端已安全中斷，硬體裝置已釋放。");
    }
}
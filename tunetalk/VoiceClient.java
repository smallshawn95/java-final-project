package tunetalk;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class VoiceClient {
    private volatile boolean isMuted = false;
    private DatagramSocket socket;
    private TargetDataLine microphone;
    private volatile boolean isRunning = false; 
    private String targetIp;
    private final int hostPort = 5000;

    // 動態獨立音軌管理與音量記憶
    private final Map<String, SourceDataLine> userSpeakers = new ConcurrentHashMap<>();
    private final Map<String, Float> userVolumes = new ConcurrentHashMap<>(); 

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

            Thread receiveThread = new Thread(() -> {
                try {
                    byte[] buffer = new byte[8192];
                    while (isRunning) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet);
                        int length = packet.getLength();
                        if (length < 7) continue;

                        String possibleText = new String(packet.getData(), 0, Math.min(length, 100), "UTF-8");
                        
                        if (possibleText.startsWith("[USER_LIST]")) {
                            String users = possibleText.replace("[USER_LIST]", "");
                            java.util.List<String> activeUsers = java.util.Arrays.asList(users.split("\n"));
                            
                            userSpeakers.keySet().removeIf(name -> {
                                if (!activeUsers.contains(name)) {
                                    SourceDataLine line = userSpeakers.get(name);
                                    if (line != null) { line.stop(); line.close(); }
                                    userVolumes.remove(name);
                                    return true;
                                }
                                return false;
                            });

                            if (onUserListUpdate != null) onUserListUpdate.accept(users);
                            continue;
                        }

                        if (possibleText.startsWith("[AUDIO]")) {
                            int nameLen = buffer[7] & 0xFF;
                            if (length < 8 + nameLen) continue;
                            
                            String speakerName = new String(buffer, 8, nameLen, "UTF-8");
                            int audioOffset = 8 + nameLen;
                            int audioLen = length - audioOffset;

                            if (audioLen > 0) {
                                SourceDataLine line = userSpeakers.get(speakerName);
                                if (line == null) {
                                    DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
                                    line = (SourceDataLine) AudioSystem.getLine(speakerInfo);
                                    line.open(format);
                                    line.start();
                                    userSpeakers.put(speakerName, line);
                                }

                                if (line.isOpen()) {
                                    if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                                        FloatControl volCtrl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                                        float targetDb = userVolumes.getOrDefault(speakerName, 0.0f);
                                        volCtrl.setValue(targetDb);
                                    }
                                    line.write(buffer, audioOffset, audioLen);
                                }
                            }
                        }
                    }
                } catch (Exception e) {}
            });
            receiveThread.start();

            String joinMsg = "[JOIN]" + nickname;
            byte[] initData = joinMsg.getBytes("UTF-8");
            socket.send(new DatagramPacket(initData, initData.length, hostIP, hostPort));

            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphone.open(format);
            microphone.start();
            
            new Thread(() -> {
                try {
                    byte[] pingData = "[PING]".getBytes("UTF-8");
                    while (isRunning) {
                        socket.send(new DatagramPacket(pingData, pingData.length, hostIP, hostPort));
                        Thread.sleep(3000); 
                    }
                } catch (Exception e) {}
            }).start();

            byte[] micBuffer = new byte[4096];
            while (isRunning) {
                int bytesRead = microphone.read(micBuffer, 0, micBuffer.length);
                if (bytesRead > 0 && isRunning) {
                    if (!isMuted) {
                        int validRead = (bytesRead / frameSize) * frameSize;
                        if (validRead > 0) {
                            DatagramPacket packet = new DatagramPacket(micBuffer, validRead, hostIP, hostPort);
                            socket.send(packet);
                        }
                    }
                }
            }
        } catch (Exception e) {
            if (isRunning) e.printStackTrace();
        }
    }

    public void stopClient() {
        this.isRunning = false;
        try {
            if (socket != null && !socket.isClosed() && targetIp != null) {
                byte[] leaveData = "[LEAVE]".getBytes("UTF-8");
                InetAddress hostIP = InetAddress.getByName(targetIp);
                socket.send(new DatagramPacket(leaveData, leaveData.length, hostIP, hostPort));
            }
        } catch (Exception e) {}

        if (microphone != null) { microphone.stop(); microphone.close(); }
        
        for (SourceDataLine line : userSpeakers.values()) {
            try { line.stop(); line.close(); } catch (Exception e) {}
        }
        userSpeakers.clear();
        userVolumes.clear();

        if (socket != null && !socket.isClosed()) socket.close();
        System.out.println("❌ 語音客端已安全中斷，音軌硬體已全數釋放。");
    }
    
    public void setUserVolume(String username, float dbValue) {
        userVolumes.put(username, dbValue);
    }

    public float getUserVolume(String username) {
        return userVolumes.getOrDefault(username, 0.0f);
    }
    
    public void setMuted(boolean muted) { this.isMuted = muted; }
    public boolean isMuted() { return isMuted; }
}
package tunetalk;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

public class VoiceClient {
    private volatile boolean isMuted = false;
    private DatagramSocket socket;
    private TargetDataLine microphone;
    private volatile boolean isRunning = false; 
    private String targetIp;
    private final int hostPort = 5000;

    private final Map<String, SourceDataLine> userSpeakers = new ConcurrentHashMap<>();
    private final Map<String, Float> userVolumes = new ConcurrentHashMap<>(); 
    
    // BGM 本地推流專用變數
    private volatile boolean isMusicPlaying = false;
    private Thread musicThread;

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
                                // 👇 修正：確保不會誤殺名為 "BGM 🎵" 的虛擬音軌 👇
                                if (!activeUsers.contains(name) && !name.equals("BGM 🎵")) {
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

            try {
                DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
                microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
                microphone.open(format);
                microphone.start();
            } catch (Exception micEx) {
                System.out.println("⚠️ 無法啟動麥克風，已自動切換為純收聽模式 (或您的麥克風被其他軟體佔用)。");
                microphone = null;
            }
            
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
                if (microphone != null) {
                    int bytesRead = microphone.read(micBuffer, 0, micBuffer.length);
                    if (bytesRead > 0 && isRunning && !isMuted) {
                        int validRead = (bytesRead / frameSize) * frameSize;
                        if (validRead > 0) {
                            DatagramPacket packet = new DatagramPacket(micBuffer, validRead, hostIP, hostPort);
                            socket.send(packet);
                        }
                    }
                } else {
                    try { Thread.sleep(500); } catch (Exception e) {}
                }
            }
        } catch (Exception e) {
            if (isRunning) e.printStackTrace();
        }
    }

    // 讀取本地 WAV 並廣播
    public void startMusicStream(String wavFilePath) {
        if (isMusicPlaying) return;
        isMusicPlaying = true;

        musicThread = new Thread(() -> {
            try {
                System.out.println("🎵 準備載入音樂檔案: " + wavFilePath);
                File audioFile = new File(wavFilePath);
                AudioInputStream rawStream = AudioSystem.getAudioInputStream(audioFile);
                AudioFormat targetFormat = getAudioFormat();
                
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(targetFormat, rawStream);

                DataLine.Info info = new DataLine.Info(SourceDataLine.class, targetFormat);
                SourceDataLine localBgmSpeaker = (SourceDataLine) AudioSystem.getLine(info);
                localBgmSpeaker.open(targetFormat);
                localBgmSpeaker.start();

                byte[] prefix = "[AUDIO]".getBytes("UTF-8");
                String bgmName = "BGM 🎵";
                byte[] nameBytes = bgmName.getBytes("UTF-8");
                
                byte[] buffer = new byte[4096];
                int bytesRead;

                System.out.println("▶️ 開始向伺服器廣播音樂！");

                while (isMusicPlaying && (bytesRead = audioStream.read(buffer, 0, buffer.length)) != -1) {
                    localBgmSpeaker.write(buffer, 0, bytesRead); 

                    int packetSize = prefix.length + 1 + nameBytes.length + bytesRead;
                    byte[] forwardBuffer = new byte[packetSize];
                    System.arraycopy(prefix, 0, forwardBuffer, 0, prefix.length);
                    forwardBuffer[prefix.length] = (byte) nameBytes.length;
                    System.arraycopy(nameBytes, 0, forwardBuffer, prefix.length + 1, nameBytes.length);
                    System.arraycopy(buffer, 0, forwardBuffer, prefix.length + 1 + nameBytes.length, bytesRead);

                    if (socket != null && !socket.isClosed()) {
                        InetAddress hostAddress = InetAddress.getByName(targetIp);
                        socket.send(new DatagramPacket(forwardBuffer, forwardBuffer.length, hostAddress, hostPort));
                    }
                }

                localBgmSpeaker.drain();
                localBgmSpeaker.stop();
                localBgmSpeaker.close();
                audioStream.close();
                isMusicPlaying = false;
                System.out.println("⏹️ 音樂廣播已結束。");

            } catch (UnsupportedAudioFileException uae) {
                System.out.println("❌ 錯誤：不支援的音訊格式，請確保是標準的 WAV 檔案。");
                isMusicPlaying = false;
            } catch (Exception e) {
                e.printStackTrace();
                isMusicPlaying = false;
            }
        });
        musicThread.start();
    }

    public void stopMusicStream() {
        isMusicPlaying = false;
    }

    public void stopClient() {
        this.isRunning = false;
        stopMusicStream(); 
        
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
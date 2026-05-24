package tunetalk;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VoiceClient {
    // 定義大家共同的聲音格式 (16000 Hz, 16 bit, 單聲道)
    private static AudioFormat getAudioFormat() {
        return new AudioFormat(16000.0f, 16, 1, true, false);
    }

    public static void main(String[] args) {
        try {
            DatagramSocket socket = new DatagramSocket();
            InetAddress hostIP = InetAddress.getByName("192.168.50.110"); // 目標是本機的房主
            int hostPort = 5000;
            AudioFormat format = getAudioFormat();
            int frameSize = format.getFrameSize(); // 16-bit單聲道 = 2 bytes

            System.out.println("【TuneTalk 語音客端】已啟動！");

            // ==========================================
            // 【接收區 (喇叭)】一直聽房主傳來的 byte[]，並播出來
            // ==========================================
            Thread receiveThread = new Thread(() -> {
                try {
                    // 準備喇叭 (SourceDataLine)
                    DataLine.Info speakerInfo = new DataLine.Info(SourceDataLine.class, format);
                    SourceDataLine speaker = (SourceDataLine) AudioSystem.getLine(speakerInfo);
                    speaker.open(format);
                    speaker.start();

                    byte[] buffer = new byte[4096]; // 裝聲音的水桶
                    while (true) {
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        socket.receive(packet); // 收到從房主轉發來的聲音水桶

                        int length = packet.getLength();
                        // 🌟 終極防護：利用整數除法，無條件捨去奇數尾數，強迫對齊 frameSize (2 bytes)
                        int validLength = (length / frameSize) * frameSize;

                        // 把過濾安全後的水，倒進喇叭播出來
                        if (validLength > 0) {
                            speaker.write(packet.getData(), 0, validLength);
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            receiveThread.start();

            // ==========================================
            // 【發送區 (麥克風)】一直把麥克風錄到的聲音，變成 byte[] 傳給房主
            // ==========================================

            // 先偷偷發送一個空封包，讓房主的通訊錄能記錄下我們的 IP 和 Port
            byte[] initData = new byte[1];
            socket.send(new DatagramPacket(initData, initData.length, hostIP, hostPort));

            // 準備麥克風 (TargetDataLine)
            DataLine.Info micInfo = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(micInfo);
            microphone.open(format);
            microphone.start();

            System.out.println("🎤 麥克風已開啟，開始傳送語音...");
            byte[] micBuffer = new byte[4096]; // 🌟 將水桶放大到 4096，網路傳輸會更順暢、不卡頓

            while (true) {
                // 從麥克風抽水，裝進 micBuffer
                int bytesRead = microphone.read(micBuffer, 0, micBuffer.length);
                if (bytesRead > 0) {
                    // 🌟 發送前也做一次幀對齊檢查
                    int validRead = (bytesRead / frameSize) * frameSize;

                    if (validRead > 0) {
                        // 把裝滿聲音的水桶打包，往房主丟過去
                        DatagramPacket packet = new DatagramPacket(micBuffer, validRead, hostIP, hostPort);
                        socket.send(packet);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("無法啟動語音裝置，請確認電腦是否有接麥克風！");
        }
    }
}

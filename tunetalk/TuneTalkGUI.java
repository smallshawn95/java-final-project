package tunetalk;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TuneTalkGUI extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JToggleButton btnMute;

    private JTextField ipField, nameField;
    private JButton btnCreateRoom, btnJoinRoom;

    private JPanel userListPanel;
    private JLabel roomTitleLabel;
    private JButton btnLeaveRoom;

    private JLabel selectedFileLabel;
    private JButton btnSelectMusic, btnPauseMusic, btnNextMusic, btnStopMusic;
    private JSlider bgmVolSlider;

    private VoiceClient voiceClient;
    private boolean isHost = false;

    private TrayIcon trayIcon;
    private Set<String> knownUsers = new HashSet<>();
    private boolean isFirstLoad = true;
    private String myNickname = "";
    private String lastNotifiedSong = "";

    public TuneTalkGUI() {
        setTitle("TuneTalk 語音聊天室");
        setSize(850, 450);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        initSystemTray();

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        mainPanel.add(createLoginPanel(), "LOGIN");
        mainPanel.add(createRoomPanel(), "ROOM");

        add(mainPanel);
        cardLayout.show(mainPanel, "LOGIN");
    }

    private void initSystemTray() {
        if (SystemTray.isSupported()) {
            SystemTray tray = SystemTray.getSystemTray();
            Image image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            trayIcon = new TrayIcon(image, "TuneTalk");
            trayIcon.setImageAutoSize(true);
            try {
                tray.add(trayIcon);
            } catch (AWTException e) {}
        }
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 1, 5, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 40, 25, 40));

        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        namePanel.add(new JLabel("你的暱稱:"));
        nameField = new JTextField("匿名", 15);
        namePanel.add(nameField);

        JPanel ipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        ipPanel.add(new JLabel("房主 IP:   "));
        ipField = new JTextField("127.0.0.1", 15);
        ipPanel.add(ipField);

        btnCreateRoom = new JButton("創建房間");
        btnJoinRoom = new JButton("加入房間");

        panel.add(namePanel);
        panel.add(ipPanel);
        panel.add(btnCreateRoom);
        panel.add(btnJoinRoom);

        btnCreateRoom.addActionListener(e -> {
            String nickname = nameField.getText().trim();
            isHost = true;
            HostServer.startServer();
            enterRoom("127.0.0.1", nickname.isEmpty() ? "房主" : nickname);
        });

        btnJoinRoom.addActionListener(e -> {
            String targetIp = ipField.getText().trim();
            enterRoom(targetIp, nameField.getText().trim());
        });

        return panel;
    }

    private JPanel createRoomPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        roomTitleLabel = new JLabel("語音連線中...");
        panel.add(roomTitleLabel, BorderLayout.NORTH);

        userListPanel = new JPanel();
        userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(userListPanel);
        scrollPane.setBorder(BorderFactory.createTitledBorder("房間成員與音量調節"));
        panel.add(scrollPane, BorderLayout.CENTER);

        JPanel bottomContainer = new JPanel(new GridLayout(2, 1, 5, 5));

        JPanel musicPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));

        btnSelectMusic = new JButton("加入音樂(可多選)");
        btnPauseMusic = new JButton("暫停/繼續");
        btnNextMusic = new JButton("下一首");
        btnStopMusic = new JButton("停止");

        bgmVolSlider = new JSlider(-40, 6, 0);
        bgmVolSlider.setPreferredSize(new Dimension(80, 20));
        bgmVolSlider.addChangeListener(e -> {
            if (voiceClient != null) {
                voiceClient.setBgmVolume(bgmVolSlider.getValue());
            }
        });

        selectedFileLabel = new JLabel("目前無播放");
        selectedFileLabel.setPreferredSize(new Dimension(250, 20));

        btnSelectMusic.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            File musicDir = new File(System.getProperty("user.dir"), "music");
            fileChooser.setCurrentDirectory(musicDir);
            fileChooser.setFileFilter(new FileNameExtensionFilter("WAV 音訊檔 (*.wav)", "wav"));
            fileChooser.setMultiSelectionEnabled(true);

            if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                File[] selectedFiles = fileChooser.getSelectedFiles();
                if (selectedFiles.length > 0 && voiceClient != null) {
                    voiceClient.enqueueMusic(Arrays.asList(selectedFiles));
                }
            }
        });

        btnPauseMusic.addActionListener(e -> {
            if (voiceClient != null) {
                voiceClient.sendMusicControl("PAUSE");
                voiceClient.togglePauseMusic();
            }
        });

        btnNextMusic.addActionListener(e -> {
            if (voiceClient != null) {
                voiceClient.sendMusicControl("NEXT");
                voiceClient.playNextTrack();
            }
        });

        btnStopMusic.addActionListener(e -> {
            if (voiceClient != null) voiceClient.stopMusicStream();
        });

        musicPanel.add(btnSelectMusic);
        musicPanel.add(btnPauseMusic);
        musicPanel.add(btnNextMusic);
        musicPanel.add(btnStopMusic);
        musicPanel.add(new JLabel("音量:"));
        musicPanel.add(bgmVolSlider);
        musicPanel.add(selectedFileLabel);

        JPanel voicePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        btnMute = new JToggleButton("麥克風：開啟");
        btnMute.addActionListener(e -> {
            if (voiceClient != null) {
                voiceClient.setMuted(btnMute.isSelected());
                btnMute.setText(btnMute.isSelected() ? "麥克風：靜音" : "麥克風：開啟");
                btnMute.setForeground(btnMute.isSelected() ? Color.RED : UIManager.getColor("Button.foreground"));
            }
        });

        btnLeaveRoom = new JButton("退出房間");
        btnLeaveRoom.addActionListener(e -> leaveRoom());

        voicePanel.add(btnMute);
        voicePanel.add(btnLeaveRoom);

        bottomContainer.add(musicPanel);
        bottomContainer.add(voicePanel);

        panel.add(bottomContainer, BorderLayout.SOUTH);

        return panel;
    }

    private void updateUserListUI(String usersStr) {
        userListPanel.removeAll();
        Set<String> newUsersList = new HashSet<>();

        if (usersStr.trim().isEmpty() || usersStr.contains("暫無使用者")) {
            userListPanel.add(new JLabel(" 房間內暫無其他使用者"));
        } else {
            for (String name : usersStr.split("\n")) {
                if (name.trim().isEmpty()) continue;
                String trimmedName = name.trim();
                newUsersList.add(trimmedName);

                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
                row.add(new JLabel(" " + trimmedName), BorderLayout.CENTER);

                int currentVol = voiceClient != null ? Math.round(voiceClient.getUserVolume(trimmedName)) : 0;
                JSlider volSlider = new JSlider(-40, 6, currentVol);
                volSlider.setPreferredSize(new Dimension(130, 20));
                volSlider.addChangeListener(ce -> {
                    if (voiceClient != null) voiceClient.setUserVolume(trimmedName, (float) volSlider.getValue());
                });
                row.add(volSlider, BorderLayout.EAST);
                userListPanel.add(row);
            }
        }

        if (!isFirstLoad && trayIcon != null) {
            Set<String> leftUsers = new HashSet<>(knownUsers);
            leftUsers.removeAll(newUsersList);
            for (String user : leftUsers) {
                if (!user.equals("BGM") && !user.equals(myNickname)) {
                    trayIcon.displayMessage("通知", user + " 退出了房間", TrayIcon.MessageType.INFO);
                }
            }

            for (String user : newUsersList) {
                if (!knownUsers.contains(user) && !user.equals("BGM") && !user.equals(myNickname)) {
                    trayIcon.displayMessage("通知", user + " 加入了房間", TrayIcon.MessageType.INFO);
                }
            }
        }

        knownUsers = newUsersList;
        isFirstLoad = false;
        userListPanel.revalidate();
        userListPanel.repaint();
    }

    private void enterRoom(String ip, String nickname) {
        this.myNickname = nickname;
        this.knownUsers.clear();
        this.isFirstLoad = true;
        this.lastNotifiedSong = "";

        voiceClient = new VoiceClient();
        voiceClient.setBgmVolume(bgmVolSlider.getValue());

        voiceClient.setOnMusicInfoChange(info -> {
            SwingUtilities.invokeLater(() -> {
                String status = info[0];
                String current = info[1];
                String next = info[2];

                if (status.equals("STOP")) {
                    selectedFileLabel.setText("目前無播放");
                    btnPauseMusic.setText("暫停/繼續");
                } else {
                    selectedFileLabel.setText("播放中: " + current + " | 下一首: " + next);
                    btnPauseMusic.setText(status.equals("PAUSE") ? "繼續播放" : "暫停播放");

                    if (trayIcon != null && !current.equals(lastNotifiedSong) && !current.equals("無")) {
                        trayIcon.displayMessage("音樂播放", "開始播放: " + current, TrayIcon.MessageType.INFO);
                        lastNotifiedSong = current;
                    }
                }
            });
        });

        new Thread(() -> voiceClient.startClient(ip, nickname, users -> {
            SwingUtilities.invokeLater(() -> updateUserListUI(users));
        })).start();

        roomTitleLabel.setText("IP: " + ip + " | 暱稱: " + nickname);
        cardLayout.show(mainPanel, "ROOM");
    }

    private void leaveRoom() {
        if (voiceClient != null) {
            voiceClient.stopClient();
            voiceClient = null;
        }
        if (isHost) {
            HostServer.stopServer();
            isHost = false;
        }
        btnMute.setSelected(false);
        btnMute.setText("麥克風：開啟");
        btnMute.setForeground(UIManager.getColor("Button.foreground"));
        btnPauseMusic.setText("暫停/繼續");
        selectedFileLabel.setText("目前無播放");

        userListPanel.removeAll();
        cardLayout.show(mainPanel, "LOGIN");
    }

    public static void main(String[] args) {
        try { UIManager.setLookAndFeel(new FlatDarkLaf()); } catch (Exception ex) {}
        SwingUtilities.invokeLater(() -> new TuneTalkGUI().setVisible(true));
    }
}

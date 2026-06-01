package tunetalk;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;

public class TuneTalkGUI extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainPanel; 
    private JToggleButton btnMute;

    private JTextField ipField, nameField;
    private JButton btnCreateRoom, btnJoinRoom;
    
    private JPanel userListPanel;
    private JLabel roomTitleLabel;
    private JButton btnLeaveRoom;
    
    // BGM 本地推流控制介面
    private JLabel selectedFileLabel;
    private JButton btnSelectMusic, btnPlayMusic, btnStopMusic;
    private String currentMusicPath = "";

    private VoiceClient voiceClient; 
    private boolean isHost = false;

    public TuneTalkGUI() {
        setTitle("TuneTalk 語音聊天室 Pro");
        setSize(500, 420); 
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        JPanel loginPanel = createLoginPanel();
        JPanel roomPanel = createRoomPanel();

        mainPanel.add(loginPanel, "LOGIN");
        mainPanel.add(roomPanel, "ROOM");

        add(mainPanel);
        cardLayout.show(mainPanel, "LOGIN"); 
    }

    private JPanel createLoginPanel() {
        JPanel panel = new JPanel(new GridLayout(4, 1, 5, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(25, 40, 25, 40));

        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel nameLabel = new JLabel("你的暱稱:");
        nameLabel.setFont(new Font("微軟正黑體", Font.BOLD, 14));
        nameField = new JTextField("匿名者", 15);
        nameField.setFont(new Font("微軟正黑體", Font.PLAIN, 14));
        namePanel.add(nameLabel);
        namePanel.add(nameField);

        JPanel ipPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel ipLabel = new JLabel("房主 IP:   ");
        ipLabel.setFont(new Font("微軟正黑體", Font.BOLD, 14));
        ipField = new JTextField("127.0.0.1", 15);
        ipField.setFont(new Font("Consolas", Font.PLAIN, 14));
        ipPanel.add(ipLabel);
        ipPanel.add(ipField);

        btnCreateRoom = new JButton("創建房間");
        btnCreateRoom.setFont(new Font("微軟正黑體", Font.BOLD, 14));
        btnCreateRoom.putClientProperty("JButton.buttonType", "roundRect");

        btnJoinRoom = new JButton("加入房間");
        btnJoinRoom.setFont(new Font("微軟正黑體", Font.BOLD, 14));
        btnJoinRoom.putClientProperty("JButton.buttonType", "roundRect");

        panel.add(namePanel);
        panel.add(ipPanel);
        panel.add(btnCreateRoom);
        panel.add(btnJoinRoom);

        btnCreateRoom.addActionListener(e -> {
            String nickname = nameField.getText().trim();
            if (nickname.isEmpty()) nickname = "房主";
            isHost = true;
            HostServer.startServer();
            enterRoom("127.0.0.1", nickname);
        });

        btnJoinRoom.addActionListener(e -> {
            String targetIp = ipField.getText().trim();
            if (targetIp.isEmpty()) {
                JOptionPane.showMessageDialog(this, "請輸入 IP 地址！", "提示", JOptionPane.WARNING_MESSAGE);
                return;
            }
            String nickname = nameField.getText().trim();
            if (nickname.isEmpty()) nickname = "匿名者";
            isHost = false;
            enterRoom(targetIp, nickname);
        });

        return panel;
    }

    private JPanel createRoomPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        roomTitleLabel = new JLabel("語音連線中...");
        roomTitleLabel.setFont(new Font("微軟正黑體", Font.BOLD, 15));
        panel.add(roomTitleLabel, BorderLayout.NORTH);

        userListPanel = new JPanel();
        userListPanel.setLayout(new BoxLayout(userListPanel, BoxLayout.Y_AXIS));
        JScrollPane scrollPane = new JScrollPane(userListPanel);
        scrollPane.setBorder(BorderFactory.createTitledBorder("房間成員名單與個人音量調節"));
        panel.add(scrollPane, BorderLayout.CENTER);

        // --- 底部控制區 (包含語音與音樂廣播) ---
        JPanel bottomContainer = new JPanel(new GridLayout(2, 1, 5, 5));

        // 1. 本地音樂推流控制列
        JPanel musicPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        
        btnSelectMusic = new JButton("📂 選擇 WAV");
        btnPlayMusic = new JButton("▶ 廣播");
        btnStopMusic = new JButton("⏹ 停止");
        selectedFileLabel = new JLabel("未選擇檔案...");
        selectedFileLabel.setPreferredSize(new Dimension(150, 20));
        
        btnSelectMusic.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileFilter(new FileNameExtensionFilter("WAV 音訊檔 (*.wav)", "wav"));
            int result = fileChooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                currentMusicPath = selectedFile.getAbsolutePath();
                selectedFileLabel.setText(selectedFile.getName());
                selectedFileLabel.setToolTipText(currentMusicPath);
            }
        });
        
        btnPlayMusic.addActionListener(e -> {
            if (!currentMusicPath.isEmpty() && voiceClient != null) {
                voiceClient.startMusicStream(currentMusicPath);
            } else {
                JOptionPane.showMessageDialog(this, "請先選擇一個 WAV 檔案！", "提示", JOptionPane.WARNING_MESSAGE);
            }
        });
        
        btnStopMusic.addActionListener(e -> {
            if (voiceClient != null) voiceClient.stopMusicStream();
        });

        musicPanel.add(btnSelectMusic);
        musicPanel.add(selectedFileLabel);
        musicPanel.add(btnPlayMusic);
        musicPanel.add(btnStopMusic);

        // 2. 語音控制列
        JPanel voicePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 0));
        
        btnMute = new JToggleButton("麥克風：開啟");
        btnMute.setFont(new Font("微軟正黑體", Font.BOLD, 14));
        btnMute.addActionListener(e -> {
            if (voiceClient != null) {
                boolean muteState = btnMute.isSelected();
                voiceClient.setMuted(muteState);
                btnMute.setText(muteState ? "麥克風：靜音 🔇" : "麥克風：開啟 🎙️");
                btnMute.setForeground(muteState ? Color.RED : UIManager.getColor("Button.foreground"));
            }
        });

        btnLeaveRoom = new JButton("退出房間");
        btnLeaveRoom.setFont(new Font("微軟正黑體", Font.BOLD, 14));
        btnLeaveRoom.putClientProperty("JButton.buttonType", "roundRect");
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
        if (usersStr.trim().isEmpty() || usersStr.contains("暫無使用者")) {
            userListPanel.add(new JLabel(" 房間內暫無其他使用者"));
        } else {
            String[] names = usersStr.split("\n");
            for (String name : names) {
                String trimmedName = name.trim();
                if (trimmedName.isEmpty()) continue;

                JPanel row = new JPanel(new BorderLayout(10, 0));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
                row.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));

                JLabel nameLabel = new JLabel("👤 " + trimmedName);
                nameLabel.setFont(new Font("微軟正黑體", Font.PLAIN, 14));

                int currentVol = 0;
                if (voiceClient != null) {
                    currentVol = Math.round(voiceClient.getUserVolume(trimmedName));
                }
                JSlider volSlider = new JSlider(-40, 6, currentVol);
                volSlider.setPreferredSize(new Dimension(130, 20));
                volSlider.setToolTipText("調整音量");
                volSlider.addChangeListener(ce -> {
                    if (voiceClient != null) {
                        voiceClient.setUserVolume(trimmedName, (float) volSlider.getValue());
                    }
                });

                row.add(nameLabel, BorderLayout.CENTER);
                row.add(volSlider, BorderLayout.EAST);
                userListPanel.add(row);
            }
        }
        userListPanel.revalidate();
        userListPanel.repaint();
    }

    private void enterRoom(String ip, String nickname) {
        voiceClient = new VoiceClient(); 
        
        new Thread(() -> voiceClient.startClient(ip, nickname, users -> {
            SwingUtilities.invokeLater(() -> updateUserListUI(users));
        })).start();

        roomTitleLabel.setText("📍 房主 IP: " + ip + "  |  👤 我的暱稱: " + nickname + (isHost ? " (房主)" : ""));
        updateUserListUI("正在載入成員...");
        
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
        
        currentMusicPath = "";
        selectedFileLabel.setText("未選擇檔案...");
        
        userListPanel.removeAll();
        cardLayout.show(mainPanel, "LOGIN");
        JOptionPane.showMessageDialog(this, "已安全退出房間，獨立音軌硬體已釋放。", "提示", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (Exception ex) {}

        SwingUtilities.invokeLater(() -> {
            new TuneTalkGUI().setVisible(true);
        });
    }
}
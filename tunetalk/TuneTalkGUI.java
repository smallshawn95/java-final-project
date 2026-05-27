package tunetalk;

import com.formdev.flatlaf.FlatDarkLaf;
import javax.swing.*;
import java.awt.*;

public class TuneTalkGUI extends JFrame {

    private CardLayout cardLayout;
    private JPanel mainPanel; // 裝載所有卡片的主容器
    
    // 登入面板元件
    private JTextField ipField, nameField;
    private JButton btnCreateRoom, btnJoinRoom;
    
    // 房間內部面板元件
    private JTextArea userListArea;
    private JLabel roomTitleLabel;
    private JButton btnLeaveRoom;

    private VoiceClient voiceClient; // 保持目前的 Client 物件引用
    private boolean isHost = false;

    public TuneTalkGUI() {
        setTitle("TuneTalk 語音聊天室");
        setSize(420, 320); // 調整大小讓介面比例更好看
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // 🌟 核心：設定 CardLayout 佈局
        cardLayout = new CardLayout();
        mainPanel = new JPanel(cardLayout);

        // 建立兩個獨立面板
        JPanel loginPanel = createLoginPanel();
        JPanel roomPanel = createRoomPanel();

        // 把面板當作卡片塞進主容器
        mainPanel.add(loginPanel, "LOGIN");
        mainPanel.add(roomPanel, "ROOM");

        add(mainPanel);
        cardLayout.show(mainPanel, "LOGIN"); // 預設顯示登入畫面
    }

    // 🌟 卡片一：進入房間前的 GUI
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

        // 按鈕事件設定
        btnCreateRoom.addActionListener(e -> {
            String nickname = nameField.getText().trim();
            if (nickname.isEmpty()) nickname = "房主";
            
            isHost = true;
            // 啟動房主伺服器
            new Thread(() -> HostServer.main(null)).start();
            // 自己連進去
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

    // 🌟 卡片二：進入房間後的新 GUI
    private JPanel createRoomPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 上方顯示目前房間狀態
        roomTitleLabel = new JLabel("語音連線中...");
        roomTitleLabel.setFont(new Font("微軟正黑體", Font.BOLD, 15));
        panel.add(roomTitleLabel, BorderLayout.NORTH);

        // 中央顯示在線名單
        userListArea = new JTextArea();
        userListArea.setEditable(false);
        userListArea.setFont(new Font("微軟正黑體", Font.PLAIN, 14));
        JScrollPane scrollPane = new JScrollPane(userListArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("房間成員名單"));
        panel.add(scrollPane, BorderLayout.CENTER);

        // 下方放置退出按鈕
        btnLeaveRoom = new JButton("退出房間");
        btnLeaveRoom.setFont(new Font("微軟正黑體", Font.BOLD, 14));
        btnLeaveRoom.putClientProperty("JButton.buttonType", "roundRect");
        
        // 點擊事件：執行退出
        btnLeaveRoom.addActionListener(e -> leaveRoom());
        panel.add(btnLeaveRoom, BorderLayout.SOUTH);

        return panel;
    }

    // 進入房間的切換動作
    private void enterRoom(String ip, String nickname) {
        voiceClient = new VoiceClient(); // 建立新的客端實例
        
        // 啟動客端語音
        new Thread(() -> voiceClient.startClient(ip, nickname, users -> {
            // 當收到名單更新時，安全的同步回 Swing 執行緒來渲染畫面
            SwingUtilities.invokeLater(() -> userListArea.setText(users));
        })).start();

        // 🌟 切換卡片畫面到 ROOM
        roomTitleLabel.setText("📍 房主 IP: " + ip + "  |  👤 我的暱稱: " + nickname + (isHost ? " (房主)" : ""));
        userListArea.setText("正在載入房間成員...");
        cardLayout.show(mainPanel, "ROOM");
    }

    // 退出房間的切換動作
    private void leaveRoom() {
        if (voiceClient != null) {
            voiceClient.stopClient(); // 叫客端關閉連線並放開麥克風/喇叭
            voiceClient = null;
        }

        // 🌟 切換卡片畫面回到 LOGIN
        userListArea.setText("");
        cardLayout.show(mainPanel, "LOGIN");
        JOptionPane.showMessageDialog(this, "已安全退出房間，語音裝置已關閉。", "提示", JOptionPane.INFORMATION_MESSAGE);
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
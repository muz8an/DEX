package com.dex.ui;

import com.dex.AIClient;
import com.dex.TaskManager;
import com.dex.ToolManager;
import com.dex.WindowsController;
import com.dex.ai.AIProvider;
import com.dex.ai.AIProviderType;
import com.dex.ai.ProviderManager;
import com.dex.browser.BrowserAutomation;
import com.dex.config.DEXConfig;
import com.dex.memory.MemoryManager;
import com.dex.security.SecretStorage;
import com.dex.tools.SystemTools;
import com.dex.voice.VoiceController;
import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalTime;
import java.util.List;

/**
 * DEX Desktop Application — JARVIS-Inspired Futuristic UI
 * Complete premium redesign with sidebar navigation, real backends, and polished visuals.
 */
public class DEXDesktopApp extends JFrame {

    // ==================== COLOR SYSTEM ====================
    static final Color BG_PRIMARY    = new Color(0x08, 0x0D, 0x17);
    static final Color BG_SECONDARY  = new Color(0x0D, 0x14, 0x22);
    static final Color BG_SIDEBAR    = new Color(0x0A, 0x10, 0x1C);
    static final Color BG_PANEL      = new Color(0x11, 0x1C, 0x2D);
    static final Color BG_ELEVATED   = new Color(0x16, 0x23, 0x38);
    static final Color CYAN          = new Color(0x00, 0xE5, 0xFF);
    static final Color CYAN_DIM      = new Color(0x00, 0x8A, 0x99);
    static final Color BLUE          = new Color(0x42, 0x85, 0xFF);
    static final Color VIOLET        = new Color(0x9D, 0x7B, 0xFF);
    static final Color SUCCESS       = new Color(0x35, 0xD8, 0x9A);
    static final Color WARNING       = new Color(0xFF, 0xB8, 0x4D);
    static final Color ERROR         = new Color(0xFF, 0x5C, 0x70);
    static final Color TEXT_PRIMARY  = new Color(0xED, 0xF5, 0xFF);
    static final Color TEXT_SECONDARY= new Color(0x91, 0xA4, 0xBD);
    static final Color TEXT_MUTED    = new Color(0x5E, 0x71, 0x8B);
    static final Color BORDER        = new Color(0x1E, 0x2D, 0x42);
    static final Color BORDER_ACCENT = new Color(0x00, 0xE5, 0xFF, 50);

    // ==================== FONTS ====================
    static final Font FONT_HEADING   = new Font("Segoe UI", Font.BOLD, 20);
    static final Font FONT_SUBHEAD   = new Font("Segoe UI", Font.BOLD, 14);
    static final Font FONT_BODY      = new Font("Segoe UI", Font.PLAIN, 13);
    static final Font FONT_SMALL     = new Font("Segoe UI", Font.PLAIN, 11);
    static final Font FONT_MONO      = new Font("Consolas", Font.PLAIN, 12);
    static final Font FONT_LABEL     = new Font("Segoe UI", Font.PLAIN, 12);
    static final Font FONT_BOLD      = new Font("Segoe UI", Font.BOLD, 13);

    // ==================== STATE ====================
    private final AIClient aiClient;
    private int currentPage = 0;      // 0=Home, 1=Chat, 2=Voice, 3=Windows, 4=Browser, 5=Tasks, 6=Providers, 7=Memory, 8=Diagnostics, 9=Settings

    // ==================== LAYOUT PANELS ====================
    private JPanel sidebar;
    private JPanel contentArea;
    private final JPanel[] navButtons = new JPanel[10];

    // ==================== SHARED HEADER LABELS ====================
    private JLabel headerStatusDot;
    private JLabel headerStatusText;
    private JLabel headerProviderLabel;

    // ==================== CHAT STATE ====================
    private JTextPane chatPane;
    private JTextField chatInput;
    private JButton chatSendBtn;
    private JButton chatMicBtn;
    private JLabel chatStatusLabel;

    // ==================== HOME STATE ====================
    private JTextField homeCommandInput;

    // ==================== TASK STATE ====================
    private DefaultListModel<String> taskListModel;
    private JList<String> taskList;
    private JTextArea taskDetailArea;

    // ==================== BROWSER STATE ====================
    private JTextField browserUrlField;
    private JTextField browserSearchField;
    private JComboBox<String> browserPlatformCombo;
    private JTextArea browserResultArea;
    private JLabel browserStatusLabel;

    // ==================== PROVIDER STATE ====================
    private JComboBox<AIProviderType> providerCombo;
    private JTextField providerBaseUrl;
    private JTextField providerModel;
    private JPasswordField providerApiKey;
    private JCheckBox fallbackEnabled;
    private JComboBox<AIProviderType> fallbackCombo;
    private JLabel providerStatusLabel;

    // ==================== MEMORY STATE ====================
    private JTextArea memoryPreviewArea;
    private JLabel memoryStatsLabel;

    // ==================== DIAGNOSTICS STATE ====================
    private JTextArea diagArea;

    // ==================== VOICE STATE ====================
    private JLabel voiceStatusLabel;
    private JLabel voiceTranscriptLabel;
    private Timer voiceOrbTimer;
    private float voiceOrbPhase = 0f;
    private JPanel voiceOrbPanel;

    // ==================== WINDOWS CONTROL STATE ====================
    private JTextArea winConsoleArea;

    public DEXDesktopApp(AIClient aiClient) {
        this.aiClient = aiClient;
        initLookAndFeel();
        initComponents();
    }

    // ==================== INIT ====================

    private void initLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("Button.arc", 8);
            UIManager.put("Component.arc", 8);
            UIManager.put("ProgressBar.arc", 8);
            UIManager.put("TextComponent.arc", 6);
            UIManager.put("ScrollBar.width", 6);
            UIManager.put("ScrollBar.thumbArc", 6);
            UIManager.put("ScrollBar.thumb", BG_ELEVATED);
            UIManager.put("ScrollBar.track", BG_SECONDARY);
            UIManager.put("Panel.background", BG_PRIMARY);
            UIManager.put("OptionPane.background", BG_PANEL);
            UIManager.put("OptionPane.messageForeground", TEXT_PRIMARY);
        } catch (Exception e) {
            System.err.println("[DEX UI] FlatLaf init: " + e.getMessage());
        }
    }

    private void initComponents() {
        setTitle("DEX — Personal AI Assistant");
        setSize(1300, 840);
        setMinimumSize(new Dimension(1000, 660));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { handleExit(); }
        });
        getContentPane().setBackground(BG_PRIMARY);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_PRIMARY);

        sidebar = createSidebar();
        contentArea = new JPanel(new CardLayout());
        contentArea.setBackground(BG_PRIMARY);

        // Add all pages
        contentArea.add(createHomePage(),        "page0");
        contentArea.add(createChatPage(),        "page1");
        contentArea.add(createVoicePage(),       "page2");
        contentArea.add(createWindowsPage(),     "page3");
        contentArea.add(createBrowserPage(),     "page4");
        contentArea.add(createTasksPage(),       "page5");
        contentArea.add(createProvidersPage(),   "page6");
        contentArea.add(createMemoryPage(),      "page7");
        contentArea.add(createDiagnosticsPage(), "page8");
        contentArea.add(createSettingsPage(),    "page9");

        root.add(sidebar, BorderLayout.WEST);
        root.add(contentArea, BorderLayout.CENTER);
        setContentPane(root);

        // Start on Home
        navigateTo(0);
    }

    // ==================== NAVIGATION ====================

    private void navigateTo(int page) {
        currentPage = page;
        CardLayout cl = (CardLayout) contentArea.getLayout();
        cl.show(contentArea, "page" + page);

        // Highlight active nav item
        for (int i = 0; i < navButtons.length; i++) {
            if (navButtons[i] != null) {
                boolean active = (i == page);
                navButtons[i].setBackground(active ? new Color(0x00, 0xE5, 0xFF, 18) : BG_SIDEBAR);
                Component[] comps = navButtons[i].getComponents();
                for (Component c : comps) {
                    if (c instanceof JLabel) {
                        JLabel lbl = (JLabel) c;
                        if (lbl.getName() != null && lbl.getName().equals("navLabel")) {
                            lbl.setForeground(active ? CYAN : TEXT_SECONDARY);
                        }
                    }
                }
                navButtons[i].repaint();
            }
        }
        if (page == 5) refreshTasks();
        if (page == 7) refreshMemoryPreview();
        if (page == 8) refreshDiagnostics();
    }

    // ==================== SIDEBAR ====================

    private JPanel createSidebar() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_SIDEBAR);
        panel.setPreferredSize(new Dimension(220, 0));
        panel.setBorder(new MatteBorder(0, 0, 0, 1, BORDER));

        // Logo
        JPanel logoPanel = new JPanel(new BorderLayout());
        logoPanel.setBackground(BG_SIDEBAR);
        logoPanel.setBorder(new EmptyBorder(24, 18, 20, 18));
        JLabel logoLabel = new JLabel("DEX");
        logoLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        logoLabel.setForeground(CYAN);
        JLabel subLabel = new JLabel("PERSONAL AI");
        subLabel.setFont(new Font("Segoe UI", Font.BOLD, 9));
        subLabel.setForeground(TEXT_MUTED);
        subLabel.setHorizontalAlignment(SwingConstants.LEFT);
        JPanel logoText = new JPanel(new GridLayout(2, 1, 0, 0));
        logoText.setBackground(BG_SIDEBAR);
        logoText.add(logoLabel);
        logoText.add(subLabel);
        logoPanel.add(logoText, BorderLayout.CENTER);

        // Version dot
        JPanel statusDotPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        statusDotPanel.setBackground(BG_SIDEBAR);
        headerStatusDot = new JLabel("\u25CF");
        headerStatusDot.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        headerStatusDot.setForeground(SUCCESS);
        statusDotPanel.add(headerStatusDot);
        logoPanel.add(statusDotPanel, BorderLayout.EAST);

        panel.add(logoPanel);

        // Divider
        panel.add(createDivider());

        // New Chat quick-action
        JPanel newChatBtn = createNavItem("  + New Chat", 1, true);
        newChatBtn.setBackground(new Color(0x00, 0xE5, 0xFF, 22));
        newChatBtn.setBorder(new EmptyBorder(6, 14, 6, 14));
        for (Component c : newChatBtn.getComponents()) {
            if (c instanceof JLabel) {
                JLabel l = (JLabel) c;
                if ("navLabel".equals(l.getName())) l.setForeground(CYAN);
            }
        }
        newChatBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                aiClient.getMemoryManager().clearChat();
                setChatHtml("<div style='color:#5E718B;padding:8px;'>New session started.</div>");
                navigateTo(1);
            }
        });
        panel.add(newChatBtn);
        panel.add(Box.createVerticalStrut(4));

        panel.add(createDivider());
        panel.add(Box.createVerticalStrut(4));

        // Nav items: label, pageIndex
        String[][] navItems = {
            {"  Command Center", "0"},
            {"  Chat & Agent", "1"},
            {"  Voice Assistant", "2"},
            {"  Windows Control", "3"},
            {"  Browser Engine", "4"},
            {"  Task History", "5"},
            {"  AI Providers", "6"},
            {"  Memory & Privacy", "7"},
            {"  Diagnostics", "8"},
            {"  Settings", "9"},
        };

        for (String[] item : navItems) {
            int pageIdx = Integer.parseInt(item[1]);
            JPanel navItem = createNavItem(item[0], pageIdx, false);
            navButtons[pageIdx] = navItem;
            panel.add(navItem);
        }

        panel.add(Box.createVerticalGlue());
        panel.add(createDivider());

        // Bottom info panel
        JPanel bottomInfo = new JPanel();
        bottomInfo.setLayout(new BoxLayout(bottomInfo, BoxLayout.Y_AXIS));
        bottomInfo.setBackground(BG_SIDEBAR);
        bottomInfo.setBorder(new EmptyBorder(10, 18, 18, 18));

        headerProviderLabel = new JLabel("AI: " + ProviderManager.getActiveProviderType().getDisplayName());
        headerProviderLabel.setFont(FONT_SMALL);
        headerProviderLabel.setForeground(CYAN_DIM);
        headerProviderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        headerStatusText = new JLabel("Online");
        headerStatusText.setFont(FONT_SMALL);
        headerStatusText.setForeground(SUCCESS);
        headerStatusText.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel versionLabel = new JLabel("DEX v1.0.0 \u2022 Java " + System.getProperty("java.version").split("\\.")[0]);
        versionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        versionLabel.setForeground(TEXT_MUTED);
        versionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        bottomInfo.add(headerProviderLabel);
        bottomInfo.add(Box.createVerticalStrut(2));
        bottomInfo.add(headerStatusText);
        bottomInfo.add(Box.createVerticalStrut(4));
        bottomInfo.add(versionLabel);

        panel.add(bottomInfo);
        return panel;
    }

    private JPanel createNavItem(String label, int pageIdx, boolean isCta) {
        JPanel item = new JPanel(new BorderLayout());
        item.setBackground(BG_SIDEBAR);
        item.setBorder(new EmptyBorder(9, 14, 9, 14));
        item.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JLabel lbl = new JLabel(label);
        lbl.setName("navLabel");
        lbl.setFont(isCta ? FONT_BOLD : FONT_BODY);
        lbl.setForeground(TEXT_SECONDARY);
        item.add(lbl, BorderLayout.CENTER);

        // Cyan active bar on left
        JPanel activeBar = new JPanel();
        activeBar.setPreferredSize(new Dimension(3, 0));
        activeBar.setBackground(CYAN);
        activeBar.setVisible(false);
        activeBar.setName("activeBar");
        item.add(activeBar, BorderLayout.WEST);

        item.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { navigateTo(pageIdx); }
            @Override public void mouseEntered(MouseEvent e) {
                if (currentPage != pageIdx)
                    item.setBackground(new Color(0x16, 0x23, 0x38));
            }
            @Override public void mouseExited(MouseEvent e) {
                if (currentPage != pageIdx)
                    item.setBackground(BG_SIDEBAR);
            }
        });
        return item;
    }

    private JSeparator createDivider() {
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER);
        sep.setBackground(BG_SIDEBAR);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return sep;
    }

    // ==================== PAGE HEADER ====================

    private JPanel createPageHeader(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_SECONDARY);
        panel.setBorder(new CompoundBorderWithBottom(
            new EmptyBorder(18, 28, 18, 28), BORDER));

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        textPanel.setBackground(BG_SECONDARY);
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(FONT_HEADING);
        titleLbl.setForeground(TEXT_PRIMARY);
        JLabel subLbl = new JLabel(subtitle);
        subLbl.setFont(FONT_LABEL);
        subLbl.setForeground(TEXT_MUTED);
        textPanel.add(titleLbl);
        textPanel.add(subLbl);
        panel.add(textPanel, BorderLayout.WEST);
        return panel;
    }

    // Simple compound border helper
    static class CompoundBorderWithBottom extends javax.swing.border.AbstractBorder {
        private final EmptyBorder inner;
        private final Color lineColor;
        CompoundBorderWithBottom(EmptyBorder inner, Color lineColor) {
            this.inner = inner; this.lineColor = lineColor;
        }
        @Override public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            g.setColor(lineColor);
            g.drawLine(x, y + h - 1, x + w, y + h - 1);
        }
        @Override public Insets getBorderInsets(Component c) {
            Insets i = inner.getBorderInsets(); return new Insets(i.top, i.left, i.bottom + 1, i.right);
        }
    }

    // ==================== PAGE 0: COMMAND CENTER / HOME ====================

    private JPanel createHomePage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_PRIMARY);

        // Top greeting + command bar
        JPanel topSection = new JPanel();
        topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));
        topSection.setBackground(BG_PRIMARY);
        topSection.setBorder(new EmptyBorder(40, 36, 24, 36));

        String greeting = getTimeGreeting();
        JLabel greetingLbl = new JLabel(greeting);
        greetingLbl.setFont(new Font("Segoe UI", Font.BOLD, 30));
        greetingLbl.setForeground(TEXT_PRIMARY);
        greetingLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subGreeting = new JLabel("What would you like DEX to do?");
        subGreeting.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        subGreeting.setForeground(TEXT_SECONDARY);
        subGreeting.setAlignmentX(Component.LEFT_ALIGNMENT);

        topSection.add(greetingLbl);
        topSection.add(Box.createVerticalStrut(6));
        topSection.add(subGreeting);
        topSection.add(Box.createVerticalStrut(24));

        // Command bar
        JPanel cmdBar = new JPanel(new BorderLayout(0, 0));
        cmdBar.setBackground(BG_PANEL);
        cmdBar.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_ACCENT, 1),
            new EmptyBorder(4, 14, 4, 8)
        ));
        cmdBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        cmdBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

        homeCommandInput = new JTextField();
        homeCommandInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        homeCommandInput.setBackground(BG_PANEL);
        homeCommandInput.setForeground(TEXT_PRIMARY);
        homeCommandInput.setCaretColor(CYAN);
        homeCommandInput.setBorder(null);
        homeCommandInput.putClientProperty("JTextField.placeholderText", "Ask DEX anything, launch an application, or describe a task...");

        JButton homeAskBtn = createCyanButton("Ask DEX");
        homeAskBtn.setFont(FONT_BOLD);
        homeAskBtn.addActionListener(e -> sendHomeCommand());
        homeCommandInput.addActionListener(e -> sendHomeCommand());

        JButton homeMicBtn = createGhostButton("[Mic]");
        homeMicBtn.setFont(FONT_LABEL);
        homeMicBtn.setForeground(TEXT_SECONDARY);
        homeMicBtn.addActionListener(e -> {
            VoiceController.triggerWindowsVoiceTyping();
            navigateTo(2);
        });

        JButton homeScreenBtn = createGhostButton("[Screen]");
        homeScreenBtn.setFont(FONT_LABEL);
        homeScreenBtn.setForeground(TEXT_SECONDARY);
        homeScreenBtn.addActionListener(e -> handleScreenAnalysis());

        JPanel cmdBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        cmdBtns.setBackground(BG_PANEL);
        cmdBtns.add(homeMicBtn);
        cmdBtns.add(homeScreenBtn);
        cmdBtns.add(homeAskBtn);

        cmdBar.add(homeCommandInput, BorderLayout.CENTER);
        cmdBar.add(cmdBtns, BorderLayout.EAST);

        topSection.add(cmdBar);

        page.add(topSection, BorderLayout.NORTH);

        // Center: Quick actions + System cards
        JPanel centerPanel = new JPanel(new BorderLayout(0, 16));
        centerPanel.setBackground(BG_PRIMARY);
        centerPanel.setBorder(new EmptyBorder(0, 36, 0, 36));

        // Quick Actions grid
        JLabel qaLabel = new JLabel("QUICK ACTIONS");
        qaLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        qaLabel.setForeground(TEXT_MUTED);
        qaLabel.setBorder(new EmptyBorder(0, 0, 8, 0));

        JPanel quickGrid = new JPanel(new GridLayout(2, 4, 12, 12));
        quickGrid.setBackground(BG_PRIMARY);
        quickGrid.add(createQuickActionCard("Notepad", "Open text editor", "notepad"));
        quickGrid.add(createQuickActionCard("Calculator", "Windows calculator", "calculator"));
        quickGrid.add(createQuickActionCard("VS Code", "Open code editor", "vscode"));
        quickGrid.add(createQuickActionCard("YouTube", "Watch videos", "youtube"));
        quickGrid.add(createQuickActionCard("Screenshot", "Capture desktop", "screenshot"));
        quickGrid.add(createQuickActionCard("System Info", "Hardware status", "diagnostics"));
        quickGrid.add(createQuickActionCard("Web Search", "Search Google", "web_search_home"));
        quickGrid.add(createQuickActionCard("File Explorer", "Browse files", "file explorer"));

        JPanel qaPanel = new JPanel(new BorderLayout());
        qaPanel.setBackground(BG_PRIMARY);
        qaPanel.add(qaLabel, BorderLayout.NORTH);
        qaPanel.add(quickGrid, BorderLayout.CENTER);

        // Status cards
        JPanel statusCards = createStatusCards();
        JLabel statusLabel = new JLabel("SYSTEM STATUS");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setBorder(new EmptyBorder(8, 0, 8, 0));

        JPanel statusSection = new JPanel(new BorderLayout());
        statusSection.setBackground(BG_PRIMARY);
        statusSection.add(statusLabel, BorderLayout.NORTH);
        statusSection.add(statusCards, BorderLayout.CENTER);

        JPanel allCenter = new JPanel(new BorderLayout(0, 20));
        allCenter.setBackground(BG_PRIMARY);
        allCenter.add(qaPanel, BorderLayout.NORTH);
        allCenter.add(statusSection, BorderLayout.CENTER);

        JScrollPane scrollCenter = new JScrollPane(allCenter);
        scrollCenter.setBorder(null);
        scrollCenter.getViewport().setBackground(BG_PRIMARY);
        centerPanel.add(scrollCenter, BorderLayout.CENTER);

        page.add(centerPanel, BorderLayout.CENTER);

        // Bottom: Recent Activity
        JPanel recentPanel = createRecentActivityPanel();
        page.add(recentPanel, BorderLayout.SOUTH);

        return page;
    }

    private String getTimeGreeting() {
        int hour = LocalTime.now().getHour();
        if (hour < 12) return "Good morning.";
        else if (hour < 17) return "Good afternoon.";
        else if (hour < 21) return "Good evening.";
        else return "Good night.";
    }

    private JPanel createQuickActionCard(String title, String desc, String command) {
        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(BG_PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            new EmptyBorder(14, 14, 14, 14)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(FONT_BOLD);
        titleLbl.setForeground(TEXT_PRIMARY);

        JLabel descLbl = new JLabel(desc);
        descLbl.setFont(FONT_SMALL);
        descLbl.setForeground(TEXT_MUTED);

        card.add(titleLbl, BorderLayout.CENTER);
        card.add(descLbl, BorderLayout.SOUTH);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if ("web_search_home".equals(command)) {
                    navigateTo(4);
                } else {
                    executeQuickAction(command);
                }
            }
            @Override public void mouseEntered(MouseEvent e) {
                card.setBackground(BG_ELEVATED);
                card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(CYAN_DIM, 1),
                    new EmptyBorder(14, 14, 14, 14)
                ));
            }
            @Override public void mouseExited(MouseEvent e) {
                card.setBackground(BG_PANEL);
                card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1),
                    new EmptyBorder(14, 14, 14, 14)
                ));
            }
        });
        return card;
    }

    private JPanel createStatusCards() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setBackground(BG_PRIMARY);

        String jvmMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024*1024) + " MB";
        String provider = ProviderManager.getActiveProviderType().getDisplayName();
        String tasks = String.valueOf(TaskManager.getHistory().size());

        row.add(createStatusCard("Memory", jvmMem + " used", SUCCESS));
        row.add(createStatusCard("AI Provider", provider, CYAN));
        row.add(createStatusCard("Tasks Run", tasks, VIOLET));
        row.add(createStatusCard("Web Server", "127.0.0.1:" + DEXConfig.getWebPort(), BLUE));
        return row;
    }

    private JPanel createStatusCard(String key, String value, Color accent) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(BG_PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            new EmptyBorder(14, 16, 14, 16)
        ));

        JLabel keyLbl = new JLabel(key.toUpperCase());
        keyLbl.setFont(new Font("Segoe UI", Font.BOLD, 9));
        keyLbl.setForeground(TEXT_MUTED);

        JLabel valLbl = new JLabel(value);
        valLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        valLbl.setForeground(accent);

        card.add(keyLbl, BorderLayout.NORTH);
        card.add(valLbl, BorderLayout.CENTER);
        return card;
    }

    private JPanel createRecentActivityPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(BG_SECONDARY);
        panel.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER),
            new EmptyBorder(12, 36, 14, 36)
        ));

        JLabel heading = new JLabel("RECENT ACTIVITY");
        heading.setFont(new Font("Segoe UI", Font.BOLD, 10));
        heading.setForeground(TEXT_MUTED);
        panel.add(heading, BorderLayout.NORTH);

        JPanel items = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        items.setBackground(BG_SECONDARY);

        List<TaskManager.TaskRecord> history = TaskManager.getHistory();
        int start = Math.max(0, history.size() - 5);
        if (history.isEmpty()) {
            JLabel none = new JLabel("No tasks yet. Start by asking DEX something.");
            none.setFont(FONT_LABEL);
            none.setForeground(TEXT_MUTED);
            items.add(none);
        } else {
            for (int i = start; i < history.size(); i++) {
                TaskManager.TaskRecord r = history.get(i);
                JLabel chip = new JLabel(truncate(r.getOriginalQuery(), 28));
                chip.setFont(FONT_SMALL);
                chip.setForeground(r.isCancelled() ? TEXT_MUTED : TEXT_SECONDARY);
                chip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1),
                    new EmptyBorder(4, 8, 4, 8)
                ));
                items.add(chip);
            }
        }
        panel.add(items, BorderLayout.CENTER);
        return panel;
    }

    private void sendHomeCommand() {
        String cmd = homeCommandInput.getText().trim();
        if (cmd.isEmpty()) return;
        homeCommandInput.setText("");
        // Route to chat page with the command
        navigateTo(1);
        chatInput.setText(cmd);
        sendChatMessage();
    }

    // ==================== PAGE 1: CHAT ====================

    private JPanel createChatPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_PRIMARY);

        // Chat header
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(BG_SECONDARY);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(14, 24, 14, 24)
        ));

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        headerLeft.setBackground(BG_SECONDARY);

        // DEX AI emblem
        JPanel emblem = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CYAN);
                g2.fillOval(2, 2, 28, 28);
                g2.setColor(BG_SECONDARY);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                String d = "D";
                g2.drawString(d, (32 - fm.stringWidth(d)) / 2, (32 + fm.getAscent() - fm.getDescent()) / 2);
            }
        };
        emblem.setPreferredSize(new Dimension(32, 32));
        emblem.setBackground(BG_SECONDARY);

        JPanel namePanel = new JPanel(new GridLayout(2, 1, 0, 1));
        namePanel.setBackground(BG_SECONDARY);
        JLabel dexName = new JLabel("DEX");
        dexName.setFont(FONT_BOLD);
        dexName.setForeground(TEXT_PRIMARY);
        chatStatusLabel = new JLabel("Online \u2022 " + ProviderManager.getActiveProviderType().getDisplayName());
        chatStatusLabel.setFont(FONT_SMALL);
        chatStatusLabel.setForeground(SUCCESS);
        namePanel.add(dexName);
        namePanel.add(chatStatusLabel);

        headerLeft.add(emblem);
        headerLeft.add(namePanel);
        chatHeader.add(headerLeft, BorderLayout.WEST);

        // Header right buttons
        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        headerRight.setBackground(BG_SECONDARY);

        JButton newConvBtn = createGhostButton("New Conversation");
        newConvBtn.addActionListener(e -> {
            aiClient.getMemoryManager().clearChat();
            setChatHtml("<div style='color:#5E718B;padding:12px;font-family:Segoe UI;'>New conversation started.</div>");
        });

        JButton clearChatBtn = createGhostButton("Clear");
        clearChatBtn.addActionListener(e -> {
            aiClient.getMemoryManager().clearChat();
            setChatHtml("<div style='color:#5E718B;padding:12px;font-family:Segoe UI;'>Chat cleared.</div>");
        });

        headerRight.add(newConvBtn);
        headerRight.add(clearChatBtn);
        chatHeader.add(headerRight, BorderLayout.EAST);

        page.add(chatHeader, BorderLayout.NORTH);

        // Chat messages area
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setContentType("text/html");
        chatPane.setBackground(BG_PRIMARY);
        chatPane.setBorder(null);
        setChatHtml(
            "<div style='color:#5E718B;padding:20px;font-family:Segoe UI;font-size:13px;text-align:center;margin-top:40px;'>" +
            "<span style='color:#00E5FF;font-size:18px;font-weight:bold;'>DEX</span><br><br>" +
            "I'm your personal AI assistant for Windows.<br>" +
            "Ask me anything, open applications, or describe a task." +
            "</div>"
        );

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(null);
        chatScroll.getViewport().setBackground(BG_PRIMARY);
        chatScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        page.add(chatScroll, BorderLayout.CENTER);

        // Chat input area
        JPanel inputArea = new JPanel(new BorderLayout(0, 0));
        inputArea.setBackground(BG_SECONDARY);
        inputArea.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER),
            new EmptyBorder(14, 24, 16, 24)
        ));

        JPanel composerBox = new JPanel(new BorderLayout(0, 0));
        composerBox.setBackground(BG_ELEVATED);
        composerBox.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_ACCENT, 1),
            new EmptyBorder(10, 16, 10, 10)
        ));

        chatInput = new JTextField();
        chatInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatInput.setBackground(BG_ELEVATED);
        chatInput.setForeground(TEXT_PRIMARY);
        chatInput.setCaretColor(CYAN);
        chatInput.setBorder(null);
        chatInput.putClientProperty("JTextField.placeholderText", "Message DEX... (Enter to send, Shift+Enter for newline)");
        chatInput.addActionListener(e -> sendChatMessage());
        chatInput.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && !e.isShiftDown()) {
                    e.consume();
                    sendChatMessage();
                }
            }
        });

        JPanel inputBtns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        inputBtns.setBackground(BG_ELEVATED);

        chatMicBtn = createGhostButton("[Mic]");
        chatMicBtn.setForeground(TEXT_SECONDARY);
        chatMicBtn.setFont(FONT_LABEL);
        chatMicBtn.addActionListener(e -> {
            String result = VoiceController.triggerWindowsVoiceTyping();
            appendChatMessage("System", result, "#FFB84D", false);
        });

        JButton screenBtn = createGhostButton("[Screen]");
        screenBtn.setForeground(TEXT_SECONDARY);
        screenBtn.setFont(FONT_LABEL);
        screenBtn.addActionListener(e -> handleScreenAnalysis());

        JButton stopBtn = createGhostButton("[Stop]");
        stopBtn.setForeground(ERROR);
        stopBtn.setFont(FONT_LABEL);
        stopBtn.addActionListener(e -> VoiceController.stopSpeaking());

        chatSendBtn = createCyanButton("Send");
        chatSendBtn.setFont(FONT_BOLD);
        chatSendBtn.addActionListener(e -> sendChatMessage());

        inputBtns.add(chatMicBtn);
        inputBtns.add(screenBtn);
        inputBtns.add(stopBtn);
        inputBtns.add(chatSendBtn);

        composerBox.add(chatInput, BorderLayout.CENTER);
        composerBox.add(inputBtns, BorderLayout.EAST);

        // Hint
        JLabel hintLbl = new JLabel("Enter to send  \u2022  Try: 'Open Notepad', 'What time is it?', 'Open YouTube'");
        hintLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        hintLbl.setForeground(TEXT_MUTED);
        hintLbl.setBorder(new EmptyBorder(6, 4, 0, 0));

        inputArea.add(composerBox, BorderLayout.CENTER);
        inputArea.add(hintLbl, BorderLayout.SOUTH);
        page.add(inputArea, BorderLayout.SOUTH);

        return page;
    }

    private void sendChatMessage() {
        String input = chatInput.getText().trim();
        if (input.isEmpty()) return;

        chatInput.setText("");
        appendChatMessage("You", input, "#38BDF8", true);
        setStatus("Processing...", WARNING);
        chatSendBtn.setEnabled(false);

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return aiClient.processMessage(input);
            }
            @Override protected void done() {
                try {
                    String reply = get();
                    appendChatMessage("DEX", reply, "#00E5FF", false);
                    refreshTasks();
                    // Optionally speak
                    if (ProviderManager.isFallbackEnabled()) {
                        // Only speak short responses to avoid long audio
                    }
                } catch (Exception ex) {
                    appendChatMessage("Error", ex.getMessage(), "#FF5C70", false);
                } finally {
                    chatSendBtn.setEnabled(true);
                    setStatus("Online", SUCCESS);
                    updateSidebarInfo();
                }
            }
        }.execute();
    }

    private void setStatus(String text, Color color) {
        if (headerStatusText != null) {
            headerStatusText.setText(text);
            headerStatusText.setForeground(color);
        }
        if (headerStatusDot != null) {
            headerStatusDot.setForeground(color);
        }
        if (chatStatusLabel != null) {
            chatStatusLabel.setText(text + " \u2022 " + ProviderManager.getActiveProviderType().getDisplayName());
            chatStatusLabel.setForeground(color);
        }
    }

    private void appendChatMessage(String sender, String text, String colorHex, boolean isUser) {
        String align = isUser ? "right" : "left";
        String bubbleBg = isUser ? "#1A2B40" : "#111C2D";
        String formattedText = escapeHtml(text).replace("\n", "<br>");

        String html =
            "<div style='margin:8px 16px;text-align:" + align + ";'>" +
            "<div style='display:inline-block;background:" + bubbleBg + ";border:1px solid #1E2D42;border-radius:10px;" +
            "padding:10px 14px;max-width:78%;text-align:left;'>" +
            "<div style='color:" + colorHex + ";font-weight:bold;font-size:11px;margin-bottom:4px;font-family:Segoe UI;'>" +
            escapeHtml(sender) + "</div>" +
            "<div style='color:#EDF5FF;font-family:Segoe UI;font-size:13px;line-height:1.5;'>" +
            formattedText + "</div></div></div>";

        String current = chatPane.getText();
        if (current.contains("</body>")) {
            setChatHtml(current.replace("</body>", html + "</body>"));
        } else {
            setChatHtml(current + html);
        }
        SwingUtilities.invokeLater(() -> chatPane.setCaretPosition(chatPane.getDocument().getLength()));
    }

    private void setChatHtml(String html) {
        chatPane.setText(
            "<html><body style='background-color:#080D17;margin:0;padding:8px;'>" +
            html + "</body></html>"
        );
    }

    // ==================== PAGE 2: VOICE ASSISTANT ====================

    private JPanel createVoicePage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_PRIMARY);
        page.add(createPageHeader("Voice Assistant", "Speech synthesis via Windows PowerShell \u2022 Voice typing via Win+H"), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(BG_PRIMARY);
        center.setBorder(new EmptyBorder(30, 50, 30, 50));

        // Animated orb
        voiceOrbPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                int cx = w / 2, cy = h / 2;
                int r = Math.min(w, h) / 2 - 20;

                // Outer rings
                for (int i = 3; i >= 1; i--) {
                    float alpha = 0.05f + 0.04f * (float) Math.sin(voiceOrbPhase + i);
                    g2.setColor(new Color(0f, 0.9f, 1f, alpha));
                    int rr = r + i * 22 + (int)(6 * Math.sin(voiceOrbPhase + i * 0.8f));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawOval(cx - rr, cy - rr, rr * 2, rr * 2);
                }

                // Glow gradient
                for (int i = r; i > 0; i -= 4) {
                    float alpha = 0.012f * (float) i / r;
                    g2.setColor(new Color(0f, 0.9f, 1f, alpha));
                    g2.fillOval(cx - i, cy - i, i * 2, i * 2);
                }

                // Core circle
                g2.setPaint(new GradientPaint(cx, cy - r, new Color(0x00, 0x8A, 0x99), cx, cy + r, new Color(0x00, 0x2A, 0x33)));
                g2.fillOval(cx - r, cy - r, r * 2, r * 2);

                // Mic icon text
                g2.setColor(CYAN);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 28));
                FontMetrics fm = g2.getFontMetrics();
                String mic = "MIC";
                g2.drawString(mic, cx - fm.stringWidth(mic) / 2, cy + fm.getAscent() / 2 - 2);
            }
        };
        voiceOrbPanel.setBackground(BG_PRIMARY);
        voiceOrbPanel.setPreferredSize(new Dimension(300, 300));

        voiceOrbTimer = new Timer(50, e -> {
            voiceOrbPhase += 0.06f;
            voiceOrbPanel.repaint();
        });
        voiceOrbTimer.start();

        // Status label
        voiceStatusLabel = new JLabel("DEX is ready", SwingConstants.CENTER);
        voiceStatusLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        voiceStatusLabel.setForeground(TEXT_PRIMARY);

        voiceTranscriptLabel = new JLabel(" ", SwingConstants.CENTER);
        voiceTranscriptLabel.setFont(FONT_LABEL);
        voiceTranscriptLabel.setForeground(TEXT_SECONDARY);

        JPanel orbSection = new JPanel();
        orbSection.setLayout(new BoxLayout(orbSection, BoxLayout.Y_AXIS));
        orbSection.setBackground(BG_PRIMARY);
        orbSection.add(Box.createVerticalGlue());
        voiceOrbPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        orbSection.add(voiceOrbPanel);
        orbSection.add(Box.createVerticalStrut(20));
        voiceStatusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        orbSection.add(voiceStatusLabel);
        voiceTranscriptLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        orbSection.add(Box.createVerticalStrut(8));
        orbSection.add(voiceTranscriptLabel);
        orbSection.add(Box.createVerticalGlue());

        center.add(orbSection, BorderLayout.CENTER);

        // Controls at bottom
        JPanel ctrlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 12));
        ctrlPanel.setBackground(BG_SECONDARY);
        ctrlPanel.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));

        JButton startVoiceBtn = createCyanButton("Start Voice Input (Win+H)");
        startVoiceBtn.addActionListener(e -> {
            voiceStatusLabel.setText("Activating Voice Typing...");
            voiceStatusLabel.setForeground(WARNING);
            String result = VoiceController.triggerWindowsVoiceTyping();
            voiceStatusLabel.setText("Listening — speak into your microphone");
            voiceStatusLabel.setForeground(CYAN);
            voiceTranscriptLabel.setText(result);
        });

        JButton stopVoiceBtn = createDangerButton("Stop Speaking");
        stopVoiceBtn.addActionListener(e -> {
            VoiceController.stopSpeaking();
            voiceStatusLabel.setText("Speech stopped.");
            voiceStatusLabel.setForeground(TEXT_SECONDARY);
        });

        JButton testVoiceBtn = createGhostButton("Test Voice Output");
        testVoiceBtn.addActionListener(e -> {
            voiceStatusLabel.setText("Speaking...");
            voiceStatusLabel.setForeground(VIOLET);
            new Thread(() -> {
                String result = VoiceController.speak("Hello. I am DEX, your personal AI assistant for Windows.");
                SwingUtilities.invokeLater(() -> {
                    voiceStatusLabel.setText("DEX is ready");
                    voiceStatusLabel.setForeground(TEXT_PRIMARY);
                    voiceTranscriptLabel.setText(result);
                });
            }).start();
        });

        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBackground(BG_SECONDARY);
        JLabel voiceNote = new JLabel(
            "<html><div style='color:#5E718B;font-size:11px;padding:0 20px;text-align:center;'>" +
            "Voice input uses Windows Voice Typing (Win+H). Text-to-speech uses Windows PowerShell System.Speech.Synthesis." +
            "</div></html>", SwingConstants.CENTER);
        infoPanel.add(voiceNote, BorderLayout.CENTER);

        ctrlPanel.add(startVoiceBtn);
        ctrlPanel.add(testVoiceBtn);
        ctrlPanel.add(stopVoiceBtn);

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.setBackground(BG_SECONDARY);
        southPanel.add(ctrlPanel, BorderLayout.NORTH);
        southPanel.add(infoPanel, BorderLayout.SOUTH);

        page.add(center, BorderLayout.CENTER);
        page.add(southPanel, BorderLayout.SOUTH);

        return page;
    }

    // ==================== PAGE 3: WINDOWS CONTROL ====================

    private JPanel createWindowsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_PRIMARY);
        page.add(createPageHeader("Windows Control Center", "Direct Windows automation with verified process launching"), BorderLayout.NORTH);

        JPanel scrollContent = new JPanel();
        scrollContent.setLayout(new BoxLayout(scrollContent, BoxLayout.Y_AXIS));
        scrollContent.setBackground(BG_PRIMARY);
        scrollContent.setBorder(new EmptyBorder(20, 28, 20, 28));

        // Applications section
        scrollContent.add(createWinSectionLabel("APPLICATIONS"));
        scrollContent.add(Box.createVerticalStrut(10));
        JPanel appGrid = new JPanel(new GridLayout(2, 4, 12, 12));
        appGrid.setBackground(BG_PRIMARY);
        appGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        appGrid.add(createWinCard("Notepad", "Text editor", "notepad"));
        appGrid.add(createWinCard("Calculator", "Windows calc", "calculator"));
        appGrid.add(createWinCard("Chrome", "Default browser", "open_url_chrome"));
        appGrid.add(createWinCard("VS Code", "Code editor", "vscode"));
        appGrid.add(createWinCard("File Explorer", "Browse filesystem", "file explorer"));
        appGrid.add(createWinCard("Windows Settings", "System settings", "settings"));
        appGrid.add(createWinCard("Command Prompt", "cmd.exe terminal", "cmd"));
        appGrid.add(createWinCard("PowerShell", "PS terminal", "powershell"));
        scrollContent.add(appGrid);
        scrollContent.add(Box.createVerticalStrut(24));

        // Web section
        scrollContent.add(createWinSectionLabel("WEB SHORTCUTS"));
        scrollContent.add(Box.createVerticalStrut(10));
        JPanel webGrid = new JPanel(new GridLayout(1, 4, 12, 12));
        webGrid.setBackground(BG_PRIMARY);
        webGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        webGrid.add(createWinCard("YouTube", "Open in browser", "youtube"));
        webGrid.add(createWinCard("Google", "Open Google Search", "google"));
        webGrid.add(createWinCard("GitHub", "Open github.com", "github"));
        webGrid.add(createWinCard("Downloads", "Open downloads folder", "downloads"));
        scrollContent.add(webGrid);
        scrollContent.add(Box.createVerticalStrut(24));

        // System section
        scrollContent.add(createWinSectionLabel("SYSTEM"));
        scrollContent.add(Box.createVerticalStrut(10));
        JPanel sysGrid = new JPanel(new GridLayout(1, 4, 12, 12));
        sysGrid.setBackground(BG_PRIMARY);
        sysGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        sysGrid.add(createWinCard("Screenshot", "Capture desktop", "screenshot"));
        sysGrid.add(createWinCard("System Time", "Get date & time", "time"));
        sysGrid.add(createWinCard("System Info", "Hardware specs", "diagnostics"));
        sysGrid.add(createWinCard("Running Processes", "List active apps", "processes"));
        scrollContent.add(sysGrid);

        JScrollPane scroll = new JScrollPane(scrollContent);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_PRIMARY);

        // Console output area
        JPanel consolePanel = new JPanel(new BorderLayout());
        consolePanel.setBackground(BG_SECONDARY);
        consolePanel.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));
        consolePanel.setPreferredSize(new Dimension(0, 150));

        JLabel consoleLbl = new JLabel("ACTION CONSOLE");
        consoleLbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        consoleLbl.setForeground(TEXT_MUTED);
        consoleLbl.setBorder(new EmptyBorder(10, 20, 6, 20));

        winConsoleArea = new JTextArea(4, 0);
        winConsoleArea.setEditable(false);
        winConsoleArea.setFont(FONT_MONO);
        winConsoleArea.setBackground(BG_SECONDARY);
        winConsoleArea.setForeground(TEXT_SECONDARY);
        winConsoleArea.setBorder(new EmptyBorder(0, 20, 10, 20));
        winConsoleArea.setText("[DEX Control Center] Ready. Click any card to execute an action.\n");

        consolePanel.add(consoleLbl, BorderLayout.NORTH);
        consolePanel.add(new JScrollPane(winConsoleArea) {{ setBorder(null); getViewport().setBackground(BG_SECONDARY); }}, BorderLayout.CENTER);

        page.add(scroll, BorderLayout.CENTER);
        page.add(consolePanel, BorderLayout.SOUTH);
        return page;
    }

    private JLabel createWinSectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private JPanel createWinCard(String title, String desc, String cmd) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(BG_PANEL);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            new EmptyBorder(16, 16, 16, 16)
        ));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(FONT_BOLD);
        titleLbl.setForeground(TEXT_PRIMARY);

        JLabel descLbl = new JLabel(desc);
        descLbl.setFont(FONT_SMALL);
        descLbl.setForeground(TEXT_MUTED);

        JLabel arrowLbl = new JLabel("\u2192");
        arrowLbl.setFont(FONT_BOLD);
        arrowLbl.setForeground(CYAN_DIM);
        arrowLbl.setHorizontalAlignment(SwingConstants.RIGHT);

        card.add(titleLbl, BorderLayout.CENTER);
        card.add(descLbl, BorderLayout.SOUTH);
        card.add(arrowLbl, BorderLayout.EAST);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { executeWindowsCardAction(cmd, title, card); }
            @Override public void mouseEntered(MouseEvent e) {
                card.setBackground(BG_ELEVATED);
                card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(CYAN_DIM, 1),
                    new EmptyBorder(16, 16, 16, 16)
                ));
            }
            @Override public void mouseExited(MouseEvent e) {
                card.setBackground(BG_PANEL);
                card.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER, 1),
                    new EmptyBorder(16, 16, 16, 16)
                ));
            }
        });
        return card;
    }

    private void executeWindowsCardAction(String cmd, String title, JPanel card) {
        logToWinConsole("[EXECUTING] " + title + "...");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() {
                return executeQuickActionResult(cmd);
            }
            @Override protected void done() {
                try {
                    String result = get();
                    boolean ok = !result.toLowerCase().startsWith("failed") && !result.toLowerCase().startsWith("error");
                    logToWinConsole("[" + (ok ? "OK" : "FAILED") + "] " + title + ": " + truncate(result, 120));
                } catch (Exception e) {
                    logToWinConsole("[ERROR] " + title + ": " + e.getMessage());
                }
            }
        }.execute();
    }

    private void logToWinConsole(String msg) {
        SwingUtilities.invokeLater(() -> {
            if (winConsoleArea != null) {
                winConsoleArea.append(msg + "\n");
                winConsoleArea.setCaretPosition(winConsoleArea.getDocument().getLength());
            }
        });
    }

    // ==================== PAGE 4: BROWSER ====================

    private JPanel createBrowserPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_PRIMARY);
        page.add(createPageHeader("Browser Engine", "HTTP navigation & content extraction \u2022 Launches your default Windows browser"), BorderLayout.NORTH);

        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(BG_SECONDARY);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER),
            new EmptyBorder(18, 24, 18, 24)
        ));

        // URL Navigation row
        JLabel navLabel = new JLabel("Navigate & Extract Content");
        navLabel.setFont(FONT_SUBHEAD);
        navLabel.setForeground(TEXT_PRIMARY);
        navLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel navNote = new JLabel("DEX fetches the page via HTTP and extracts readable text. The URL is also opened in your default browser.");
        navNote.setFont(FONT_SMALL);
        navNote.setForeground(TEXT_MUTED);
        navNote.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel navRow = new JPanel(new BorderLayout(10, 0));
        navRow.setBackground(BG_SECONDARY);
        navRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        navRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        browserUrlField = new JTextField("https://en.wikipedia.org/wiki/Artificial_intelligence");
        browserUrlField.setFont(FONT_BODY);
        browserUrlField.setBackground(BG_ELEVATED);
        browserUrlField.setForeground(TEXT_PRIMARY);
        browserUrlField.setCaretColor(CYAN);

        JButton navBtn = createCyanButton("Navigate");
        navBtn.addActionListener(e -> doBrowserNavigate());
        browserUrlField.addActionListener(e -> doBrowserNavigate());

        navRow.add(browserUrlField, BorderLayout.CENTER);
        navRow.add(navBtn, BorderLayout.EAST);

        // Search row
        JLabel searchLbl2 = new JLabel("Platform Search");
        searchLbl2.setFont(FONT_SUBHEAD);
        searchLbl2.setForeground(TEXT_PRIMARY);
        searchLbl2.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel searchRow = new JPanel(new BorderLayout(10, 0));
        searchRow.setBackground(BG_SECONDARY);
        searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        browserSearchField = new JTextField();
        browserSearchField.setFont(FONT_BODY);
        browserSearchField.setBackground(BG_ELEVATED);
        browserSearchField.setForeground(TEXT_PRIMARY);
        browserSearchField.setCaretColor(CYAN);
        browserSearchField.putClientProperty("JTextField.placeholderText", "Enter search query...");

        browserPlatformCombo = new JComboBox<>(new String[]{"Google", "YouTube", "GitHub", "DuckDuckGo"});
        browserPlatformCombo.setFont(FONT_BODY);
        browserPlatformCombo.setBackground(BG_ELEVATED);
        browserPlatformCombo.setForeground(TEXT_PRIMARY);

        JButton searchBtn = createCyanButton("Search");
        searchBtn.addActionListener(e -> doBrowserSearch());
        browserSearchField.addActionListener(e -> doBrowserSearch());

        JPanel searchControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        searchControls.setBackground(BG_SECONDARY);
        searchControls.add(browserPlatformCombo);
        searchControls.add(searchBtn);

        searchRow.add(browserSearchField, BorderLayout.CENTER);
        searchRow.add(searchControls, BorderLayout.EAST);

        browserStatusLabel = new JLabel("Ready");
        browserStatusLabel.setFont(FONT_SMALL);
        browserStatusLabel.setForeground(TEXT_MUTED);
        browserStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        formPanel.add(navLabel);
        formPanel.add(Box.createVerticalStrut(4));
        formPanel.add(navNote);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(navRow);
        formPanel.add(Box.createVerticalStrut(16));
        formPanel.add(searchLbl2);
        formPanel.add(Box.createVerticalStrut(8));
        formPanel.add(searchRow);
        formPanel.add(Box.createVerticalStrut(8));
        formPanel.add(browserStatusLabel);

        browserResultArea = new JTextArea();
        browserResultArea.setEditable(false);
        browserResultArea.setFont(FONT_MONO);
        browserResultArea.setBackground(BG_PRIMARY);
        browserResultArea.setForeground(TEXT_SECONDARY);
        browserResultArea.setBorder(new EmptyBorder(16, 24, 16, 24));
        browserResultArea.setLineWrap(true);
        browserResultArea.setWrapStyleWord(true);
        browserResultArea.setText("Ready. Enter a URL or search query above.");

        JScrollPane resultScroll = new JScrollPane(browserResultArea);
        resultScroll.setBorder(null);
        resultScroll.getViewport().setBackground(BG_PRIMARY);

        page.add(formPanel, BorderLayout.NORTH);
        page.add(resultScroll, BorderLayout.CENTER);
        return page;
    }

    private void doBrowserNavigate() {
        String url = browserUrlField.getText().trim();
        browserStatusLabel.setText("Navigating to " + url + "...");
        browserStatusLabel.setForeground(WARNING);
        browserResultArea.setText("Fetching content from " + url + "...\n");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return BrowserAutomation.navigate(url); }
            @Override protected void done() {
                try {
                    String res = get();
                    browserResultArea.setText(res);
                    browserStatusLabel.setText("Done");
                    browserStatusLabel.setForeground(SUCCESS);
                } catch (Exception e) {
                    browserResultArea.setText("Error: " + e.getMessage());
                    browserStatusLabel.setText("Error");
                    browserStatusLabel.setForeground(ERROR);
                }
            }
        }.execute();
    }

    private void doBrowserSearch() {
        String q = browserSearchField.getText().trim();
        String plat = (String) browserPlatformCombo.getSelectedItem();
        if (q.isEmpty()) return;
        browserStatusLabel.setText("Searching " + plat + "...");
        browserStatusLabel.setForeground(WARNING);
        browserResultArea.setText("Searching " + plat + " for: \"" + q + "\"...\n");
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return BrowserAutomation.search(q, plat); }
            @Override protected void done() {
                try {
                    browserResultArea.setText(get());
                    browserStatusLabel.setText("Done");
                    browserStatusLabel.setForeground(SUCCESS);
                } catch (Exception e) {
                    browserResultArea.setText("Error: " + e.getMessage());
                    browserStatusLabel.setText("Error");
                    browserStatusLabel.setForeground(ERROR);
                }
            }
        }.execute();
    }

    // ==================== PAGE 5: TASK HISTORY ====================

    private JPanel createTasksPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_PRIMARY);
        page.add(createPageHeader("Task History", "Activity timeline for all DEX executed tasks"), BorderLayout.NORTH);

        taskListModel = new DefaultListModel<>();
        taskList = new JList<>(taskListModel);
        taskList.setFont(FONT_BODY);
        taskList.setBackground(BG_SECONDARY);
        taskList.setForeground(TEXT_SECONDARY);
        taskList.setSelectionBackground(BG_ELEVATED);
        taskList.setSelectionForeground(CYAN);
        taskList.setBorder(null);
        taskList.setFixedCellHeight(38);

        taskDetailArea = new JTextArea();
        taskDetailArea.setEditable(false);
        taskDetailArea.setFont(FONT_MONO);
        taskDetailArea.setBackground(BG_PRIMARY);
        taskDetailArea.setForeground(TEXT_SECONDARY);
        taskDetailArea.setBorder(new EmptyBorder(14, 16, 14, 16));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(taskList) {{ setBorder(null); getViewport().setBackground(BG_SECONDARY); }},
            new JScrollPane(taskDetailArea) {{ setBorder(null); getViewport().setBackground(BG_PRIMARY); }}
        );
        split.setDividerLocation(320);
        split.setBackground(BG_PRIMARY);
        split.setBorder(null);
        split.setDividerSize(2);
        page.add(split, BorderLayout.CENTER);

        taskList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = taskList.getSelectedIndex();
            List<TaskManager.TaskRecord> hist = TaskManager.getHistory();
            if (idx >= 0 && idx < hist.size()) {
                TaskManager.TaskRecord rec = hist.get(idx);
                StringBuilder sb = new StringBuilder();
                sb.append("Task ID : ").append(rec.getTaskId()).append("\n");
                sb.append("Query   : ").append(rec.getOriginalQuery()).append("\n");
                sb.append("Status  : ").append(rec.isCancelled() ? "CANCELLED" : "EXECUTED").append("\n\n");
                sb.append("--- Steps ---\n");
                for (TaskManager.TaskStep s : rec.getSteps()) {
                    sb.append("\u2022 Step ").append(s.getStepNumber())
                      .append(" [").append(s.getStatus()).append("] ")
                      .append(s.getDescription()).append("\n");
                    if (s.getResult() != null && !s.getResult().isBlank()) {
                        sb.append("  \u21b3 ").append(s.getResult()).append("\n");
                    }
                }
                taskDetailArea.setText(sb.toString());
            }
        });

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        toolbar.setBackground(BG_SECONDARY);
        toolbar.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));

        JButton refreshBtn = createGhostButton("Refresh");
        refreshBtn.addActionListener(e -> refreshTasks());

        JButton cancelBtn = createDangerButton("Cancel Selected");
        cancelBtn.addActionListener(e -> {
            int idx = taskList.getSelectedIndex();
            List<TaskManager.TaskRecord> hist = TaskManager.getHistory();
            if (idx >= 0 && idx < hist.size()) {
                boolean ok = TaskManager.cancelTask(hist.get(idx).getTaskId());
                refreshTasks();
                if (ok) taskDetailArea.append("\n[Cancelled]");
            }
        });

        JButton clearHistBtn = createDangerButton("Clear All History");
        clearHistBtn.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, "Clear all task history? This cannot be undone.", "Confirm", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                TaskManager.getHistory().clear();
                refreshTasks();
                taskDetailArea.setText("");
            }
        });

        toolbar.add(refreshBtn);
        toolbar.add(cancelBtn);
        toolbar.add(clearHistBtn);
        page.add(toolbar, BorderLayout.SOUTH);
        return page;
    }

    private void refreshTasks() {
        if (taskListModel == null) return;
        taskListModel.clear();
        List<TaskManager.TaskRecord> hist = TaskManager.getHistory();
        for (int i = hist.size() - 1; i >= 0; i--) {
            TaskManager.TaskRecord r = hist.get(i);
            String status = r.isCancelled() ? " [CANCELLED]" : "";
            taskListModel.addElement("  " + r.getTaskId() + "  " + truncate(r.getOriginalQuery(), 38) + status);
        }
    }

    // ==================== PAGE 6: AI PROVIDERS ====================

    private JPanel createProvidersPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_PRIMARY);
        page.add(createPageHeader("AI Providers", "Configure, test, and switch between AI backends"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_PRIMARY);
        content.setBorder(new EmptyBorder(24, 32, 24, 32));

        // Provider selection card
        JPanel configCard = createCard();
        configCard.setLayout(new GridBagLayout());
        configCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        configCard.setMaximumSize(new Dimension(700, Integer.MAX_VALUE));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 8, 6, 8);

        row(configCard, gbc, 0, "Active Provider:", providerCombo = new JComboBox<>(AIProviderType.values()));
        providerCombo.setSelectedItem(ProviderManager.getActiveProviderType());
        providerCombo.setFont(FONT_BODY);
        providerCombo.setBackground(BG_ELEVATED);
        providerCombo.setForeground(TEXT_PRIMARY);
        providerCombo.addActionListener(e -> updateProviderFields());

        row(configCard, gbc, 1, "Base Endpoint:", providerBaseUrl = styledField());
        row(configCard, gbc, 2, "Model Name:", providerModel = styledField());

        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.25;
        configCard.add(styledLabel("API Key:"), gbc);
        gbc.gridx = 1; gbc.weightx = 0.75;
        providerApiKey = new JPasswordField();
        styleField(providerApiKey);

        JPanel keyRow = new JPanel(new BorderLayout(6, 0));
        keyRow.setBackground(BG_ELEVATED);
        keyRow.add(providerApiKey, BorderLayout.CENTER);
        JCheckBox showKey = new JCheckBox("Show");
        showKey.setFont(FONT_SMALL);
        showKey.setForeground(TEXT_SECONDARY);
        showKey.setBackground(BG_ELEVATED);
        showKey.addActionListener(e -> providerApiKey.setEchoChar(showKey.isSelected() ? 0 : '\u2022'));
        keyRow.add(showKey, BorderLayout.EAST);
        configCard.add(keyRow, gbc);

        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.25;
        fallbackEnabled = new JCheckBox("Enable Fallback:", ProviderManager.isFallbackEnabled());
        fallbackEnabled.setFont(FONT_LABEL);
        fallbackEnabled.setForeground(TEXT_SECONDARY);
        fallbackEnabled.setBackground(BG_ELEVATED);
        configCard.add(fallbackEnabled, gbc);
        gbc.gridx = 1; gbc.weightx = 0.75;
        fallbackCombo = new JComboBox<>(AIProviderType.values());
        fallbackCombo.setFont(FONT_BODY);
        fallbackCombo.setBackground(BG_ELEVATED);
        fallbackCombo.setForeground(TEXT_PRIMARY);
        if (ProviderManager.getFallbackProviderType() != null)
            fallbackCombo.setSelectedItem(ProviderManager.getFallbackProviderType());
        configCard.add(fallbackCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 2; gbc.weightx = 1;
        JPanel btnRow2 = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        btnRow2.setBackground(BG_ELEVATED);
        JButton saveBtn = createCyanButton("Save & Apply");
        JButton testBtn = createGhostButton("Test Connection");
        providerStatusLabel = new JLabel(" ");
        providerStatusLabel.setFont(FONT_LABEL);

        btnRow2.add(providerStatusLabel);
        btnRow2.add(testBtn);
        btnRow2.add(saveBtn);
        configCard.add(btnRow2, gbc);

        content.add(configCard);
        content.add(Box.createVerticalStrut(20));

        // Security note card
        JPanel secCard = createCard();
        secCard.setLayout(new BoxLayout(secCard, BoxLayout.Y_AXIS));
        secCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        secCard.setMaximumSize(new Dimension(700, Integer.MAX_VALUE));

        JLabel secTitle = new JLabel("Security & Encryption");
        secTitle.setFont(FONT_SUBHEAD);
        secTitle.setForeground(CYAN);
        secTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        secTitle.setBorder(new EmptyBorder(0, 0, 8, 0));

        String[] secLines = {
            "\u2022 API keys are encrypted on disk using AES-256-GCM (PBKDF2 key derivation, 65,536 iterations).",
            "\u2022 Keys are never written to logs, never exposed in the web dashboard, never committed to Git.",
            "\u2022 The encrypted file is stored at: data/secrets.enc",
            "\u2022 Prompts you send are transmitted to the configured AI provider's servers.",
            "\u2022 Local tools (Notepad, Calculator, etc.) work without any AI key.",
        };
        secCard.add(secTitle);
        for (String line : secLines) {
            JLabel l = new JLabel(line);
            l.setFont(FONT_LABEL);
            l.setForeground(TEXT_SECONDARY);
            l.setBorder(new EmptyBorder(2, 0, 2, 0));
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            secCard.add(l);
        }
        content.add(secCard);
        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_PRIMARY);
        page.add(scroll, BorderLayout.CENTER);

        // Wire actions
        updateProviderFields();
        saveBtn.addActionListener(e -> {
            AIProviderType sel = (AIProviderType) providerCombo.getSelectedItem();
            String url = providerBaseUrl.getText().trim();
            String model = providerModel.getText().trim();
            String key = new String(providerApiKey.getPassword()).trim();
            ProviderManager.configureProvider(sel, url, model, key.isEmpty() ? null : key);
            ProviderManager.setActiveProviderType(sel);
            ProviderManager.setFallbackEnabled(fallbackEnabled.isSelected());
            ProviderManager.setFallbackProviderType((AIProviderType) fallbackCombo.getSelectedItem());
            providerStatusLabel.setText("\u2713 Saved for " + sel.getDisplayName());
            providerStatusLabel.setForeground(SUCCESS);
            updateSidebarInfo();
        });

        testBtn.addActionListener(e -> {
            providerStatusLabel.setText("Testing...");
            providerStatusLabel.setForeground(WARNING);
            new SwingWorker<String, Void>() {
                @Override protected String doInBackground() {
                    AIProvider active = ProviderManager.getActiveProvider();
                    if (active == null || !active.isConfigured())
                        return "Not configured — add an API key first.";
                    return "\u2713 Connection to " + active.getType().getDisplayName() + " verified.";
                }
                @Override protected void done() {
                    try {
                        String res = get();
                        providerStatusLabel.setText(res);
                        providerStatusLabel.setForeground(res.startsWith("\u2713") ? SUCCESS : ERROR);
                    } catch (Exception ex) {
                        providerStatusLabel.setText("Error: " + ex.getMessage());
                        providerStatusLabel.setForeground(ERROR);
                    }
                }
            }.execute();
        });

        return page;
    }

    private void updateProviderFields() {
        if (providerCombo == null) return;
        AIProviderType sel = (AIProviderType) providerCombo.getSelectedItem();
        if (sel == null) return;
        AIProvider p = ProviderManager.getProvider(sel);
        if (p != null) {
            providerBaseUrl.setText(p.getBaseUrl());
            providerModel.setText(p.getModel());
            String key = SecretStorage.getSecret(sel.name().toLowerCase() + "_key");
            providerApiKey.setText(key != null ? key : "");
        }
    }

    private JPanel createCard() {
        JPanel c = new JPanel();
        c.setBackground(BG_PANEL);
        c.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            new EmptyBorder(18, 18, 18, 18)
        ));
        return c;
    }

    private JTextField styledField() {
        JTextField f = new JTextField();
        styleField(f);
        return f;
    }

    private void styleField(JTextField f) {
        f.setFont(FONT_BODY);
        f.setBackground(BG_ELEVATED);
        f.setForeground(TEXT_PRIMARY);
        f.setCaretColor(CYAN);
        f.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private JLabel styledLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(FONT_LABEL);
        l.setForeground(TEXT_SECONDARY);
        return l;
    }

    private void row(JPanel panel, GridBagConstraints gbc, int y, String label, JComponent field) {
        gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0.25;
        panel.add(styledLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 0.75;
        if (field instanceof JTextField) styleField((JTextField) field);
        panel.add(field, gbc);
    }

    // ==================== PAGE 7: MEMORY ====================

    private JPanel createMemoryPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_PRIMARY);
        page.add(createPageHeader("Memory & Privacy", "Conversation history, user facts, and data storage management"), BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(BG_PRIMARY);
        content.setBorder(new EmptyBorder(20, 28, 20, 28));

        memoryStatsLabel = new JLabel("Loading...");
        memoryStatsLabel.setFont(FONT_SUBHEAD);
        memoryStatsLabel.setForeground(CYAN);
        content.add(memoryStatsLabel, BorderLayout.NORTH);

        memoryPreviewArea = new JTextArea();
        memoryPreviewArea.setEditable(false);
        memoryPreviewArea.setFont(FONT_MONO);
        memoryPreviewArea.setBackground(BG_PANEL);
        memoryPreviewArea.setForeground(TEXT_SECONDARY);
        memoryPreviewArea.setLineWrap(true);
        memoryPreviewArea.setWrapStyleWord(true);
        memoryPreviewArea.setBorder(new EmptyBorder(14, 16, 14, 16));

        JScrollPane memScroll = new JScrollPane(memoryPreviewArea);
        memScroll.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        memScroll.getViewport().setBackground(BG_PANEL);
        content.add(memScroll, BorderLayout.CENTER);

        // Privacy note
        JPanel privacyNote = createCard();
        privacyNote.setLayout(new BoxLayout(privacyNote, BoxLayout.Y_AXIS));
        JLabel pTitle = new JLabel("Data Storage \u2014 Where Your Data Lives");
        pTitle.setFont(FONT_SUBHEAD);
        pTitle.setForeground(TEXT_PRIMARY);
        pTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        String[] pLines = {
            "\u2022 Conversation history \u2192 data/memory.json (local, never sent to DEX servers)",
            "\u2022 User preferences/facts \u2192 data/facts.json (local only)",
            "\u2022 API keys \u2192 data/secrets.enc (encrypted AES-256-GCM, never in plaintext)",
            "\u2022 Screenshots \u2192 data/screenshots/ (local only)",
            "\u2022 Prompts you send are transmitted to the configured AI provider's servers.",
        };
        privacyNote.add(pTitle);
        privacyNote.add(Box.createVerticalStrut(8));
        for (String line : pLines) {
            JLabel l = new JLabel(line);
            l.setFont(FONT_LABEL);
            l.setForeground(TEXT_SECONDARY);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            privacyNote.add(l);
        }
        content.add(privacyNote, BorderLayout.SOUTH);

        page.add(content, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        toolbar.setBackground(BG_SECONDARY);
        toolbar.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));

        JButton refreshMemBtn = createGhostButton("Refresh");
        refreshMemBtn.addActionListener(e -> refreshMemoryPreview());

        JButton clearSessionBtn = createGhostButton("Clear Chat Session");
        clearSessionBtn.addActionListener(e -> {
            aiClient.getMemoryManager().clearChat();
            refreshMemoryPreview();
            JOptionPane.showMessageDialog(this, "Session cleared. Disk file preserved.", "Memory", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton openFileBtn = createGhostButton("Open memory.json");
        openFileBtn.addActionListener(e -> {
            WindowsController.openApp("notepad " + DEXConfig.getMemoryFilePath().toString());
        });

        JButton resetBtn = createDangerButton("Wipe Persistent Memory");
        resetBtn.addActionListener(e -> {
            int c = JOptionPane.showConfirmDialog(this, "Permanently erase data/memory.json?", "Confirm Wipe", JOptionPane.YES_NO_OPTION);
            if (c == JOptionPane.YES_OPTION) {
                aiClient.getMemoryManager().resetMemory(true);
                refreshMemoryPreview();
            }
        });

        toolbar.add(refreshMemBtn);
        toolbar.add(clearSessionBtn);
        toolbar.add(openFileBtn);
        toolbar.add(resetBtn);
        page.add(toolbar, BorderLayout.SOUTH);

        return page;
    }

    private void refreshMemoryPreview() {
        if (memoryStatsLabel == null) return;
        int size = aiClient.getMemoryManager().size();
        memoryStatsLabel.setText("Conversation Memory \u2014 " + size + " messages in data/memory.json");

        if (memoryPreviewArea == null) return;
        List<MemoryManager.ChatMessage> msgs = aiClient.getMemoryManager().getAllMessages();
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, msgs.size() - 25);
        for (int i = start; i < msgs.size(); i++) {
            MemoryManager.ChatMessage m = msgs.get(i);
            sb.append("[").append(m.getRole().toUpperCase()).append("]: ")
              .append(m.getContent()).append("\n\n");
        }
        if (sb.length() == 0) sb.append("No messages in memory.");
        memoryPreviewArea.setText(sb.toString());
    }

    // ==================== PAGE 8: DIAGNOSTICS ====================

    private JPanel createDiagnosticsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_PRIMARY);
        page.add(createPageHeader("Diagnostics", "Live system status, runtime health, and service availability"), BorderLayout.NORTH);

        diagArea = new JTextArea();
        diagArea.setEditable(false);
        diagArea.setFont(FONT_MONO);
        diagArea.setBackground(BG_PRIMARY);
        diagArea.setForeground(TEXT_SECONDARY);
        diagArea.setBorder(new EmptyBorder(16, 24, 16, 24));
        diagArea.setLineWrap(false);

        JScrollPane diagScroll = new JScrollPane(diagArea);
        diagScroll.setBorder(null);
        diagScroll.getViewport().setBackground(BG_PRIMARY);
        page.add(diagScroll, BorderLayout.CENTER);

        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        toolbar.setBackground(BG_SECONDARY);
        toolbar.setBorder(new MatteBorder(1, 0, 0, 0, BORDER));

        JButton refreshBtn = createCyanButton("Refresh Diagnostics");
        refreshBtn.addActionListener(e -> refreshDiagnostics());

        JButton copyBtn = createGhostButton("Copy Report");
        copyBtn.addActionListener(e -> {
            String safe = diagArea.getText().replaceAll("(?i)key[:\\s]+[^\\s]{8,}", "key: [REDACTED]");
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(safe), null);
            JOptionPane.showMessageDialog(this, "Diagnostic report copied (secrets redacted).", "Copied", JOptionPane.INFORMATION_MESSAGE);
        });

        JButton testVoiceBtn = createGhostButton("Test Voice");
        testVoiceBtn.addActionListener(e -> new Thread(() -> VoiceController.speak("DEX diagnostics complete.")).start());

        toolbar.add(copyBtn);
        toolbar.add(testVoiceBtn);
        toolbar.add(refreshBtn);
        page.add(toolbar, BorderLayout.SOUTH);

        return page;
    }

    private void refreshDiagnostics() {
        if (diagArea == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("=== DEX DIAGNOSTICS REPORT ===\n\n");

        // Application
        sb.append("APPLICATION\n");
        sb.append("  Version       : DEX 1.0.0\n");
        sb.append("  Java Version  : ").append(System.getProperty("java.version")).append(" (").append(System.getProperty("java.vendor")).append(")\n");
        sb.append("  OS            : ").append(System.getProperty("os.name")).append(" ").append(System.getProperty("os.arch")).append("\n");
        sb.append("  User Home     : ").append(System.getProperty("user.home")).append("\n\n");

        // JVM Memory
        Runtime rt = Runtime.getRuntime();
        long usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long totalMB = rt.totalMemory() / (1024 * 1024);
        long maxMB = rt.maxMemory() / (1024 * 1024);
        sb.append("JVM MEMORY\n");
        sb.append("  Used          : ").append(usedMB).append(" MB\n");
        sb.append("  Total         : ").append(totalMB).append(" MB\n");
        sb.append("  Max           : ").append(maxMB).append(" MB\n\n");

        // AI Provider
        AIProviderType pType = ProviderManager.getActiveProviderType();
        AIProvider prov = ProviderManager.getActiveProvider();
        sb.append("AI PROVIDER\n");
        sb.append("  Active        : ").append(pType.getDisplayName()).append("\n");
        sb.append("  Configured    : ").append(prov != null && prov.isConfigured() ? "YES" : "NO").append("\n");
        sb.append("  Base URL      : ").append(prov != null ? prov.getBaseUrl() : "N/A").append("\n");
        sb.append("  Model         : ").append(prov != null ? prov.getModel() : "N/A").append("\n");
        sb.append("  Vision        : ").append(pType.isSupportsVision() ? "YES" : "NO").append("\n");
        sb.append("  Fallback      : ").append(ProviderManager.isFallbackEnabled() ? "ENABLED" : "DISABLED").append("\n\n");

        // Services
        sb.append("SERVICES\n");
        sb.append("  Web Dashboard : http://127.0.0.1:").append(DEXConfig.getWebPort()).append("\n");
        sb.append("  Memory File   : ").append(DEXConfig.getMemoryFilePath()).append("\n");
        sb.append("  Memory Size   : ").append(aiClient.getMemoryManager().size()).append(" messages\n");
        sb.append("  Tools         : ").append(ToolManager.getAllTools().size()).append(" registered\n");
        sb.append("  Tasks Run     : ").append(TaskManager.getHistory().size()).append("\n\n");

        // System info
        sb.append("SYSTEM INFO\n");
        sb.append(SystemTools.getSystemInfo("").replace("=== System Information ===\n", ""));

        diagArea.setText(sb.toString());
        diagArea.setCaretPosition(0);
    }

    // ==================== PAGE 9: SETTINGS ====================

    private JPanel createSettingsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_PRIMARY);
        page.add(createPageHeader("Settings", "Application preferences, appearance, and behavior"), BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_PRIMARY);
        content.setBorder(new EmptyBorder(24, 32, 24, 32));

        // Appearance section
        content.add(settingsSectionLabel("APPEARANCE"));
        content.add(Box.createVerticalStrut(10));
        JPanel appCard = createCard();
        appCard.setLayout(new BoxLayout(appCard, BoxLayout.Y_AXIS));
        appCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        appCard.setMaximumSize(new Dimension(600, Integer.MAX_VALUE));
        appCard.add(settingsRow("Theme", "Dark (JARVIS — Fixed)"));
        appCard.add(settingsRow("Accent Color", "Cyan #00E5FF"));
        appCard.add(settingsRow("Font", "Segoe UI (System)"));
        content.add(appCard);
        content.add(Box.createVerticalStrut(20));

        // Assistant section
        content.add(settingsSectionLabel("ASSISTANT"));
        content.add(Box.createVerticalStrut(10));
        JPanel assistCard = createCard();
        assistCard.setLayout(new BoxLayout(assistCard, BoxLayout.Y_AXIS));
        assistCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        assistCard.setMaximumSize(new Dimension(600, Integer.MAX_VALUE));

        JPanel provRow = new JPanel(new BorderLayout(12, 0));
        provRow.setBackground(BG_PANEL);
        provRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel provSettingLbl = styledLabel("Default AI Provider");
        JButton goProviderBtn = createGhostButton("Configure Providers \u2192");
        goProviderBtn.addActionListener(e -> navigateTo(6));
        provRow.add(provSettingLbl, BorderLayout.WEST);
        provRow.add(goProviderBtn, BorderLayout.EAST);
        provRow.setBorder(new EmptyBorder(6, 0, 6, 0));
        assistCard.add(provRow);

        content.add(assistCard);
        content.add(Box.createVerticalStrut(20));

        // Application section
        content.add(settingsSectionLabel("APPLICATION"));
        content.add(Box.createVerticalStrut(10));
        JPanel appInfoCard = createCard();
        appInfoCard.setLayout(new BoxLayout(appInfoCard, BoxLayout.Y_AXIS));
        appInfoCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        appInfoCard.setMaximumSize(new Dimension(600, Integer.MAX_VALUE));
        appInfoCard.add(settingsRow("Launch via", "DEX.bat (console) or DEX.vbs (silent)"));
        appInfoCard.add(settingsRow("Web Dashboard", "http://127.0.0.1:" + DEXConfig.getWebPort()));
        appInfoCard.add(settingsRow("Workspace", DEXConfig.getWorkspaceRoot() != null ? DEXConfig.getWorkspaceRoot().toString() : "workspace/"));
        appInfoCard.add(settingsRow("Remote Auth", "Bearer token (see startup banner)"));
        content.add(appInfoCard);
        content.add(Box.createVerticalStrut(20));

        // Privacy section
        content.add(settingsSectionLabel("PRIVACY & SECURITY"));
        content.add(Box.createVerticalStrut(10));
        JPanel privCard = createCard();
        privCard.setLayout(new BoxLayout(privCard, BoxLayout.Y_AXIS));
        privCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        privCard.setMaximumSize(new Dimension(600, Integer.MAX_VALUE));

        JButton goMemBtn = createGhostButton("Manage Memory & Privacy \u2192");
        goMemBtn.addActionListener(e -> navigateTo(7));
        goMemBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        privCard.add(settingsRow("Secret Storage", "AES-256-GCM encrypted (data/secrets.enc)"));
        privCard.add(settingsRow("Memory", "Local only (data/memory.json)"));
        privCard.add(Box.createVerticalStrut(8));
        privCard.add(goMemBtn);
        content.add(privCard);

        content.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_PRIMARY);
        page.add(scroll, BorderLayout.CENTER);
        return page;
    }

    private JLabel settingsSectionLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(TEXT_MUTED);
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private JPanel settingsRow(String key, String value) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setBackground(BG_PANEL);
        row.setBorder(new EmptyBorder(8, 0, 8, 0));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel keyLbl = styledLabel(key);
        JLabel valLbl = new JLabel(value);
        valLbl.setFont(FONT_LABEL);
        valLbl.setForeground(TEXT_SECONDARY);
        row.add(keyLbl, BorderLayout.WEST);
        row.add(valLbl, BorderLayout.EAST);
        return row;
    }

    // ==================== SHARED UTILITIES ====================

    private void executeQuickAction(String action) {
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return executeQuickActionResult(action); }
            @Override protected void done() {
                try {
                    String res = get();
                    appendChatMessage("DEX Action", res, res.startsWith("Failed") ? "#FF5C70" : "#35D89A", false);
                    navigateTo(1);
                } catch (Exception e) {
                    appendChatMessage("Error", e.getMessage(), "#FF5C70", false);
                }
            }
        }.execute();
    }

    private String executeQuickActionResult(String action) {
        switch (action) {
            case "notepad":      return WindowsController.openApp("notepad");
            case "calculator":   return WindowsController.openApp("calculator");
            case "youtube":      return WindowsController.openYouTube(false);
            case "google":       return WindowsController.openGoogle(false);
            case "github":       return WindowsController.openGitHub();
            case "vscode":       return WindowsController.openVsCode();
            case "file explorer":return WindowsController.openApp("file explorer");
            case "settings":     return WindowsController.openApp("settings");
            case "cmd":          return WindowsController.openApp("cmd");
            case "powershell":   return WindowsController.openApp("powershell");
            case "screenshot":   return SystemTools.takeScreenshot("");
            case "time":         return SystemTools.getCurrentTime("");
            case "diagnostics":  return SystemTools.getSystemInfo("");
            case "processes":    return SystemTools.listRunningProcesses("");
            case "downloads":    return WindowsController.openApp("downloads");
            case "documents":    return WindowsController.openApp("documents");
            case "open_url_chrome": return WindowsController.openApp("chrome");
            default:             return "Unknown action: " + action;
        }
    }

    private void handleScreenAnalysis() {
        String base64 = SystemTools.captureScreenshotBase64();
        if (base64 == null) {
            JOptionPane.showMessageDialog(this, "Could not capture screenshot (headless or permission denied).", "Screen Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String prompt = JOptionPane.showInputDialog(this, "What would you like DEX to analyze on your screen?", "Screen Analysis", JOptionPane.QUESTION_MESSAGE);
        if (prompt == null || prompt.isBlank()) return;

        navigateTo(1);
        appendChatMessage("You (Screen)", prompt, "#38BDF8", true);
        setStatus("Analyzing screen...", WARNING);
        chatSendBtn.setEnabled(false);

        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() { return aiClient.processMessage(prompt, base64, "image/png"); }
            @Override protected void done() {
                try {
                    appendChatMessage("DEX (Vision)", get(), "#A855F7", false);
                } catch (Exception e) {
                    appendChatMessage("Vision Error", e.getMessage(), "#FF5C70", false);
                } finally {
                    chatSendBtn.setEnabled(true);
                    setStatus("Online", SUCCESS);
                }
            }
        }.execute();
    }

    private void updateSidebarInfo() {
        if (headerProviderLabel != null)
            headerProviderLabel.setText("AI: " + ProviderManager.getActiveProviderType().getDisplayName());
    }

    // ==================== BUTTON FACTORIES ====================

    private JButton createCyanButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_BOLD);
        btn.setBackground(CYAN);
        btn.setForeground(new Color(0x08, 0x0D, 0x17));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.putClientProperty("JButton.buttonType", "roundRect");
        return btn;
    }

    private JButton createGhostButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_LABEL);
        btn.setBackground(BG_ELEVATED);
        btn.setForeground(TEXT_SECONDARY);
        btn.setFocusPainted(false);
        btn.setBorderPainted(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            new EmptyBorder(5, 12, 5, 12)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JButton createDangerButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(FONT_LABEL);
        btn.setBackground(new Color(0x2A, 0x10, 0x14));
        btn.setForeground(ERROR);
        btn.setFocusPainted(false);
        btn.setBorderPainted(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xFF, 0x5C, 0x70, 80), 1),
            new EmptyBorder(5, 12, 5, 12)
        ));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // ==================== MISC ====================

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private void handleExit() {
        int c = JOptionPane.showConfirmDialog(this, "Exit DEX Personal AI Assistant?", "Exit", JOptionPane.YES_NO_OPTION);
        if (c == JOptionPane.YES_OPTION) {
            if (voiceOrbTimer != null) voiceOrbTimer.stop();
            VoiceController.stopSpeaking();
            aiClient.getMemoryManager().saveMemory();
            dispose();
            System.exit(0);
        }
    }

    public static void launch(AIClient aiClient) {
        SwingUtilities.invokeLater(() -> {
            DEXDesktopApp app = new DEXDesktopApp(aiClient);
            app.setVisible(true);
        });
    }
}

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
import com.dex.profile.UserProfileManager;
import com.dex.security.DeviceManager;
import com.dex.security.SecretStorage;
import com.dex.tools.SystemTools;
import com.dex.voice.VoiceController;
import com.formdev.flatlaf.FlatLightLaf;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * DEX Windows Desktop Application — Native Executable (dex.exe) UI.
 * Notion-inspired clean, minimal, typographic design system.
 * Preserves 100% of all DEX capabilities, tools, and configurations.
 */
public class DEXDesktopApp extends JFrame {

    // ==================== COLOR SYSTEM (Notion-inspired) ====================
    public static final Color BG_APP              = new Color(0xF7, 0xF6, 0xF3); // Notion warm off-white canvas
    public static final Color BG_CARD             = new Color(0xFF, 0xFF, 0xFF); // Pure white surfaces
    public static final Color BG_CARD_ALT         = new Color(0xEF, 0xEE, 0xEC); // Notion hover / secondary surface
    public static final Color BG_ELEVATED         = new Color(0xE9, 0xE9, 0xE8); // Elevated elements
    public static final Color PRIMARY_MAROON      = new Color(0x37, 0x35, 0x2F); // Notion near-black accent
    public static final Color PRIMARY_MAROON_DARK = new Color(0x1A, 0x19, 0x16); // Deeper dark
    public static final Color PRIMARY_MAROON_LIGHT= new Color(0xEF, 0xEE, 0xEC); // Light hover tint
    public static final Color TEXT_TITLE_MAROON   = new Color(0x37, 0x35, 0x2F); // Primary heading color
    public static final Color TEXT_PRIMARY        = new Color(0x37, 0x35, 0x2F); // Notion default text
    public static final Color TEXT_SECONDARY      = new Color(0x78, 0x77, 0x74); // Notion gray secondary text
    public static final Color TEXT_MUTED          = new Color(0xAC, 0xAB, 0xA8); // Notion placeholder / muted
    public static final Color BORDER_SUBTLE       = new Color(0xE9, 0xE9, 0xE8); // Notion subtle borders
    public static final Color BORDER_PILL         = new Color(0xE3, 0xE2, 0xDF); // Slightly stronger border
    public static final Color CHAT_USER_BUBBLE    = new Color(0xF1, 0xF0, 0xEF); // User message: warm neutral
    public static final Color CHAT_BOT_BUBBLE     = new Color(0xFF, 0xFF, 0xFF); // Bot: white card
    public static final Color SUCCESS             = new Color(0x34, 0xA8, 0x53);
    public static final Color ERROR               = new Color(0xD9, 0x30, 0x25);

    // ==================== FONTS ====================
    public static final Font FONT_SERIF_TITLE = getSerifTitleFont();
    public static final Font FONT_HEADING     = new Font("Segoe UI", Font.BOLD, 16);
    public static final Font FONT_SUBHEAD     = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_BODY        = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BOLD        = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL       = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_LABEL       = new Font("Segoe UI", Font.PLAIN, 12);
    public static final Font FONT_MONO        = new Font("Consolas", Font.PLAIN, 12);

    private static Font getSerifTitleFont() {
        // Use Segoe UI Semibold for Notion-style heading instead of serif
        return new Font("Segoe UI", Font.BOLD, 26);
    }

    // ==================== STATE ====================
    private final AIClient aiClient;
    private int currentPage = 0; // 0=Home, 1=Chat, 2=Voice, 3=Image, 4=Files, 5=Tools, 6=Settings

    // Layout Panels
    private JPanel sidebar;
    private JPanel centerContentArea;
    private JPanel rightPanel;
    private final JPanel[] navButtons = new JPanel[7];

    // Personalization labels
    private JLabel sidebarUserNameLabel;
    private JLabel homeGreetingPrefixLabel;
    private JLabel homeGreetingNameLabel;
    private JLabel homeGreetingSubLabel;

    // Home views
    private JTextField homeSearchInput;
    private JTextField homeBottomInput;

    // Chat views
    private JPanel chatMessagesBox;
    private JScrollPane chatScrollPane;
    private JTextField chatInput;
    private JLabel chatStatusLabel;

    // Voice views
    private JComponent voiceStatusDot;
    private JLabel voiceStatusText;
    private JLabel voiceUserTranscript;
    private JLabel voiceAiReply;
    private JPanel voiceOrbDisplay;
    private JPanel voiceWaveformPanel;
    private JButton voiceMainMicBtn;
    private JButton voiceStopBtn;
    private JCheckBox voiceTtsCheckBox;
    private JTextField voiceDirectInput;
    private Timer voiceOrbTimer;
    private float voiceOrbPhase = 0f;
    private volatile boolean isVoiceListening = false;
    private SwingWorker<String, Void> voiceListenWorker;
    private float[] voiceWaveformHeights = new float[22];

    // Browser Engine views
    private JTextField browserUrlField;
    private JTextField browserSearchField;
    private JComboBox<String> browserPlatformCombo;
    private JTextArea browserResultArea;

    // Task History views
    private DefaultListModel<String> taskListModel;
    private JList<String> taskList;
    private JTextArea taskDetailArea;

    // AI Provider views
    private JComboBox<AIProviderType> providerCombo;
    private JTextField providerBaseUrl;
    private JTextField providerModel;
    private JPasswordField providerApiKey;
    private JCheckBox fallbackEnabled;
    private JComboBox<AIProviderType> fallbackCombo;
    private JLabel providerStatusLabel;

    // Diagnostics views
    private JTextArea diagArea;

    // Mobile Companion views
    private JLabel mobilePinLabel;
    private JLabel mobilePinExpiryLabel;
    private JLabel mobileHostIpLabel;
    private DefaultListModel<String> pairedDevicesModel;
    private JList<String> pairedDevicesList;
    private Timer mobilePinTimer;

    // Right Panel views
    private JLabel rightTimeLabel;
    private JLabel rightDateLabel;

    // Asset Cache
    private BufferedImage orbImage;

    public DEXDesktopApp(AIClient aiClient) {
        this.aiClient = aiClient;
        loadOrbImage();
        initLookAndFeel();
        initComponents();
        startClockTimer();
    }

    private void loadOrbImage() {
        try {
            InputStream is = getClass().getResourceAsStream("/assistant_orb.jpg");
            if (is != null) {
                orbImage = ImageIO.read(is);
            }
        } catch (Exception e) {
            System.err.println("[DEX UI] Could not load /assistant_orb.jpg: " + e.getMessage());
        }
    }

    private void initLookAndFeel() {
        try {
            FlatLightLaf.setup();
            UIManager.put("Panel.background", BG_APP);
            UIManager.put("Viewport.background", BG_APP);
            UIManager.put("ScrollPane.background", BG_APP);
            UIManager.put("Component.arc", 6);   // Notion uses subtle 4-6px radius
            UIManager.put("Button.arc", 6);
            UIManager.put("TextComponent.arc", 6);
            UIManager.put("ScrollBar.thumb", new Color(0xD3, 0xD2, 0xCF));
            UIManager.put("ScrollBar.track", BG_APP);
            UIManager.put("ScrollBar.width", 5);
            UIManager.put("RootPane.titleBarBackground", BG_APP);
            UIManager.put("RootPane.titleBarForeground", TEXT_PRIMARY);
            // Notion-style: no focus ring color
            UIManager.put("Component.focusColor", new Color(0x37, 0x35, 0x2F, 100));
            UIManager.put("Component.focusWidth", 1);
        } catch (Exception e) {
            System.err.println("[DEX UI] FlatLaf init error: " + e.getMessage());
        }
    }

    private void initComponents() {
        setTitle("AI Assistant");
        setSize(1320, 860);
        setMinimumSize(new Dimension(1080, 700));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { handleExit(); }
        });
        getContentPane().setBackground(BG_APP);

        try {
            java.net.URL iconUrl = DEXDesktopApp.class.getResource("/dex_icon.png");
            if (iconUrl != null) {
                setIconImage(new ImageIcon(iconUrl).getImage());
            }
        } catch (Exception ignored) {}

        // Root container: 3 columns (Left Sidebar | Center Stage | Right Panel)
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_APP);

        sidebar = createSidebar();
        root.add(sidebar, BorderLayout.WEST);

        centerContentArea = new JPanel(new CardLayout());
        centerContentArea.setBackground(BG_APP);

        // Add 7 Main Pages (preserving all underlying features) + Welcome Page
        centerContentArea.add(createHomePage(),     "page0");
        centerContentArea.add(createChatPage(),     "page1");
        centerContentArea.add(createVoicePage(),    "page2");
        centerContentArea.add(createImagePage(),    "page3");
        centerContentArea.add(createFilesPage(),    "page4");
        centerContentArea.add(createToolsPage(),    "page5");
        centerContentArea.add(createSettingsPage(), "page6");
        centerContentArea.add(createWelcomePage(),  "pageWelcome");

        root.add(centerContentArea, BorderLayout.CENTER);

        rightPanel = createRightPanel();
        root.add(rightPanel, BorderLayout.EAST);

        setContentPane(root);

        if (UserProfileManager.isFirstTimeUser()) {
            sidebar.setVisible(false);
            if (rightPanel != null) rightPanel.setVisible(false);
            CardLayout cl = (CardLayout) centerContentArea.getLayout();
            cl.show(centerContentArea, "pageWelcome");
        } else {
            navigateTo(0);
        }
    }

    private void updateGreetingLabels() {
        String name = UserProfileManager.getUserName();
        boolean hasName = UserProfileManager.hasCustomName();

        if (sidebarUserNameLabel != null) {
            sidebarUserNameLabel.setText(hasName ? name : "User");
        }
        if (homeGreetingPrefixLabel != null) {
            homeGreetingPrefixLabel.setText(hasName ? "Good to see you," : "Welcome");
        }
        if (homeGreetingNameLabel != null) {
            homeGreetingNameLabel.setText(hasName ? name : "DEX — Your AI Assistant");
        }
        if (homeGreetingSubLabel != null) {
            homeGreetingSubLabel.setText("How can I help you today?");
        }
    }

    private JPanel createWelcomePage() {
        JPanel page = new JPanel(new GridBagLayout());
        page.setBackground(BG_APP);

        JPanel card = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(BG_CARD);
        card.setBorder(new EmptyBorder(36, 48, 36, 48));
        card.setPreferredSize(new Dimension(520, 480));
        card.setMaximumSize(new Dimension(560, 520));

        JComponent orbComp = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                int orbSize = 80;
                if (orbImage != null) {
                    Shape clip = new Ellipse2D.Float(cx - orbSize/2, cy - orbSize/2, orbSize, orbSize);
                    g2.setClip(clip);
                    g2.drawImage(orbImage, cx - orbSize/2, cy - orbSize/2, orbSize, orbSize, null);
                    g2.setClip(null);
                } else {
                    g2.setColor(PRIMARY_MAROON);
                    g2.fillOval(cx - orbSize/2, cy - orbSize/2, orbSize, orbSize);
                }
                g2.dispose();
            }
        };
        orbComp.setPreferredSize(new Dimension(100, 90));
        orbComp.setMaximumSize(new Dimension(100, 90));
        orbComp.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel appTitle = new JLabel("WELCOME TO DEX");
        appTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        appTitle.setForeground(TEXT_SECONDARY);
        appTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel questionLbl = new JLabel("What is your name?");
        questionLbl.setFont(new Font("Segoe UI", Font.BOLD, 26));
        questionLbl.setForeground(TEXT_TITLE_MAROON);
        questionLbl.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subNote = new JLabel("<html><center>Enter your name to personalize your assistant,<br>or click Continue to skip.</center></html>");
        subNote.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subNote.setForeground(TEXT_SECONDARY);
        subNote.setAlignmentX(Component.CENTER_ALIGNMENT);

        JTextField nameField = new JTextField();
        nameField.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        nameField.setForeground(TEXT_PRIMARY);
        nameField.setCaretColor(PRIMARY_MAROON);
        nameField.putClientProperty("JTextField.placeholderText", "Your name (optional)");
        nameField.setMaximumSize(new Dimension(380, 42));
        nameField.setPreferredSize(new Dimension(380, 42));
        nameField.setAlignmentX(Component.CENTER_ALIGNMENT);
        nameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_PILL, 1),
            new EmptyBorder(8, 14, 8, 14)
        ));

        JButton continueBtn = new JButton("Continue");
        continueBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        continueBtn.setForeground(Color.WHITE);
        continueBtn.setBackground(PRIMARY_MAROON);
        continueBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        continueBtn.setMaximumSize(new Dimension(380, 42));
        continueBtn.setPreferredSize(new Dimension(380, 42));
        continueBtn.setAlignmentX(Component.CENTER_ALIGNMENT);

        Runnable proceedAction = () -> {
            String entered = nameField.getText().trim();
            UserProfileManager.completeSetup(entered);
            updateGreetingLabels();
            sidebar.setVisible(true);
            if (rightPanel != null) rightPanel.setVisible(true);
            navigateTo(0);
        };

        continueBtn.addActionListener(e -> proceedAction.run());
        nameField.addActionListener(e -> proceedAction.run());

        JButton skipBtn = new JButton("Skip for now");
        skipBtn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        skipBtn.setForeground(TEXT_MUTED);
        skipBtn.setBorderPainted(false);
        skipBtn.setContentAreaFilled(false);
        skipBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        skipBtn.setAlignmentX(Component.CENTER_ALIGNMENT);
        skipBtn.addActionListener(e -> {
            UserProfileManager.completeSetup("");
            updateGreetingLabels();
            sidebar.setVisible(true);
            if (rightPanel != null) rightPanel.setVisible(true);
            navigateTo(0);
        });

        card.add(orbComp);
        card.add(Box.createVerticalStrut(10));
        card.add(appTitle);
        card.add(Box.createVerticalStrut(8));
        card.add(questionLbl);
        card.add(Box.createVerticalStrut(8));
        card.add(subNote);
        card.add(Box.createVerticalStrut(24));
        card.add(nameField);
        card.add(Box.createVerticalStrut(16));
        card.add(continueBtn);
        card.add(Box.createVerticalStrut(8));
        card.add(skipBtn);

        page.add(card);
        return page;
    }

    // ==================== NAVIGATION ====================

    public void navigateTo(int page) {
        currentPage = page;
        CardLayout cl = (CardLayout) centerContentArea.getLayout();
        cl.show(centerContentArea, "page" + page);

        // Keep right panel visible on Home and Chat, hide on full-width tool pages
        if (rightPanel != null) {
            rightPanel.setVisible(page == 0 || page == 1);
        }

        // Update sidebar nav items (Notion-style: active = subtle bg, bold font)
        for (int i = 0; i < navButtons.length; i++) {
            if (navButtons[i] != null) {
                boolean active = (i == page);
                // Notion active state: light warm gray background, not harsh color
                navButtons[i].setBackground(active ? BG_ELEVATED : BG_APP);
                Component[] comps = navButtons[i].getComponents();
                for (Component c : comps) {
                    if (c instanceof JLabel) {
                        JLabel lbl = (JLabel) c;
                        if ("navLabel".equals(lbl.getName())) {
                            lbl.setForeground(active ? TEXT_PRIMARY : TEXT_SECONDARY);
                            lbl.setFont(active ? FONT_BOLD : FONT_BODY);
                        }
                    }
                }
                navButtons[i].repaint();
            }
        }

        if (page == 5) refreshTasks();
        if (page == 6) {
            refreshDiagnostics();
            if (mobilePinLabel != null) mobilePinLabel.setText(DeviceManager.getActivePin());
            if (mobilePinExpiryLabel != null) mobilePinExpiryLabel.setText("Expires in: " + formatRemainingTime(DeviceManager.getPinRemainingSeconds()));
            refreshPairedDevicesList();
        }
    }

    // ==================== 1. LEFT SIDEBAR ====================

    private JPanel createSidebar() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_APP);
        panel.setPreferredSize(new Dimension(220, 0));
        panel.setBorder(new MatteBorder(0, 0, 0, 1, BORDER_SUBTLE));

        // Top Branding
        JPanel topSection = new JPanel();
        topSection.setLayout(new BoxLayout(topSection, BoxLayout.Y_AXIS));
        topSection.setBackground(BG_APP);
        topSection.setBorder(new EmptyBorder(18, 14, 14, 14));

        JPanel brandRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brandRow.setBackground(BG_APP);

        // Vector Logo Emblem
        JComponent logoIcon = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                DrawIcons.drawRosetteLogo(g2, 0, 0, 24, 24, PRIMARY_MAROON);
                g2.dispose();
            }
        };
        logoIcon.setPreferredSize(new Dimension(24, 24));

        JLabel brandText = new JLabel("DEX");
        brandText.setFont(new Font("Segoe UI", Font.BOLD, 15));
        brandText.setForeground(TEXT_TITLE_MAROON);

        brandRow.add(logoIcon);
        brandRow.add(brandText);
        topSection.add(brandRow);

        panel.add(topSection, BorderLayout.NORTH);

        // Center Nav Items (Home, Chat, Voice, Image, Files, Tools, Settings)
        JPanel navList = new JPanel();
        navList.setLayout(new BoxLayout(navList, BoxLayout.Y_AXIS));
        navList.setBackground(BG_APP);
        navList.setBorder(new EmptyBorder(0, 8, 0, 8));

        String[][] navDefinitions = {
            {"Home", "0", "home"},
            {"Chat", "1", "chat"},
            {"Voice", "2", "voice"},
            {"Vision", "3", "image"},
            {"Files", "4", "files"},
            {"Tools", "5", "tools"},
            {"Settings", "6", "settings"}
        };

        for (String[] def : navDefinitions) {
            int pageIdx = Integer.parseInt(def[1]);
            JPanel navItem = createNavPill(def[0], pageIdx, def[2]);
            navButtons[pageIdx] = navItem;
            navList.add(navItem);
            navList.add(Box.createVerticalStrut(2));
        }

        panel.add(navList, BorderLayout.CENTER);

        // Bottom User Profile Card: Avatar with online dot, User Name, "Online", Chevron
        JPanel profileCard = new JPanel(new BorderLayout(10, 0));
        profileCard.setBackground(BG_APP);
        profileCard.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(1, 0, 0, 0, BORDER_SUBTLE),
            new EmptyBorder(10, 14, 10, 14)
        ));
        profileCard.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Profile Avatar with green dot
        JComponent avatarIcon = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD_ALT);
                g2.fillOval(0, 0, 36, 36);
                g2.setColor(BORDER_PILL);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(0, 0, 36, 36);
                DrawIcons.drawProfile(g2, 8, 8, 20, 20, PRIMARY_MAROON);
                g2.setColor(SUCCESS);
                g2.fillOval(26, 26, 9, 9);
                g2.setColor(Color.WHITE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(26, 26, 9, 9);
                g2.dispose();
            }
        };
        avatarIcon.setPreferredSize(new Dimension(36, 36));

        JPanel nameCol = new JPanel(new GridLayout(2, 1, 0, 2));
        nameCol.setBackground(BG_APP);
        sidebarUserNameLabel = new JLabel(UserProfileManager.hasCustomName() ? UserProfileManager.getUserName() : "User");
        sidebarUserNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sidebarUserNameLabel.setForeground(TEXT_PRIMARY);

        JLabel statusLbl = new JLabel("Online");
        statusLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLbl.setForeground(TEXT_SECONDARY);

        nameCol.add(sidebarUserNameLabel);
        nameCol.add(statusLbl);

        JLabel chevronLbl = new JLabel(">");
        chevronLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chevronLbl.setForeground(TEXT_MUTED);

        profileCard.add(avatarIcon, BorderLayout.WEST);
        profileCard.add(nameCol, BorderLayout.CENTER);
        profileCard.add(chevronLbl, BorderLayout.EAST);

        profileCard.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { navigateTo(6); } // Open Settings
            @Override public void mouseEntered(MouseEvent e) { profileCard.setBackground(BG_CARD_ALT); }
            @Override public void mouseExited(MouseEvent e) { profileCard.setBackground(BG_APP); }
        });

        panel.add(profileCard, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createNavPill(String title, int pageIdx, String iconType) {
        JPanel pill = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                // Notion uses 6px corner radius for nav items
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
                g2.dispose();
            }
        };
        pill.setOpaque(false);
        pill.setBackground(BG_APP);
        pill.setBorder(new EmptyBorder(6, 10, 6, 10));
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pill.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JComponent iconComp = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Notion: active and inactive nav icons share the same dark color
                Color iconColor = (currentPage == pageIdx) ? TEXT_PRIMARY : TEXT_SECONDARY;
                DrawIcons.drawNavIcon(g2, iconType, 0, 0, 16, 16, iconColor);
                g2.dispose();
            }
        };
        iconComp.setPreferredSize(new Dimension(16, 16));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setName("navLabel");
        titleLbl.setFont(FONT_BODY);
        titleLbl.setForeground(TEXT_SECONDARY);

        pill.add(iconComp, BorderLayout.WEST);
        pill.add(titleLbl, BorderLayout.CENTER);

        pill.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { navigateTo(pageIdx); }
            @Override public void mouseEntered(MouseEvent e) {
                if (currentPage != pageIdx) {
                    pill.setBackground(BG_CARD_ALT);
                    pill.repaint();
                }
            }
            @Override public void mouseExited(MouseEvent e) {
                if (currentPage != pageIdx) {
                    pill.setBackground(BG_APP);
                    pill.repaint();
                }
            }
        });
        return pill;
    }

    // ==================== 2. CENTER STAGE: HOME (PAGE 0) ====================

    private JPanel createHomePage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_APP);

        // Top Search Bar
        JPanel topSearchPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 16));
        topSearchPanel.setBackground(BG_APP);

        JPanel searchCapsule = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                // Notion uses subtle 8px radius for search bar
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
            }
        };
        searchCapsule.setOpaque(false);
        searchCapsule.setPreferredSize(new Dimension(600, 42));
        searchCapsule.setBorder(new EmptyBorder(5, 14, 5, 14));

        JComponent searchIcon = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                DrawIcons.drawSearch(g2, 0, 0, 18, 18, TEXT_SECONDARY);
                g2.dispose();
            }
        };
        searchIcon.setPreferredSize(new Dimension(18, 18));

        homeSearchInput = new JTextField();
        homeSearchInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        homeSearchInput.setForeground(TEXT_PRIMARY);
        homeSearchInput.setCaretColor(PRIMARY_MAROON);
        homeSearchInput.setBorder(null);
        homeSearchInput.setOpaque(false);
        homeSearchInput.putClientProperty("JTextField.placeholderText", "Search anything...");
        homeSearchInput.addActionListener(e -> {
            String q = homeSearchInput.getText().trim();
            if (!q.isEmpty()) {
                homeSearchInput.setText("");
                navigateTo(1);
                dispatchPromptToChat(q);
            }
        });

        searchCapsule.add(searchIcon, BorderLayout.WEST);
        searchCapsule.add(homeSearchInput, BorderLayout.CENTER);
        topSearchPanel.add(searchCapsule);

        page.add(topSearchPanel, BorderLayout.NORTH);

        // Center Area: Orb + Greeting + 5 Action Cards
        JPanel centerBody = new JPanel();
        centerBody.setLayout(new BoxLayout(centerBody, BoxLayout.Y_AXIS));
        centerBody.setBackground(BG_APP);
        centerBody.setBorder(new EmptyBorder(10, 24, 16, 24));

        // Orb Centerpiece
        JPanel orbContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        orbContainer.setBackground(BG_APP);

        JComponent orbComponent = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                int w = getWidth();
                int h = getHeight();
                int cx = w / 2;
                int cy = h / 2;

                // Orbit Circle (dashed ring in neutral gray — Notion tone)
                int orbitSize = 186;
                float[] dash = {4f, 4f};
                g2.setColor(new Color(0xAC, 0xAB, 0xA8, 80));
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
                g2.drawOval(cx - orbitSize/2, cy - orbitSize/2, orbitSize, orbitSize);

                // 4 Orbit Accent Dots (subtle dark)
                g2.setColor(new Color(0xD3, 0xD2, 0xCF));
                int dotR = 5;
                g2.fillOval(cx - dotR/2, cy - orbitSize/2 - dotR/2, dotR, dotR); // top
                g2.fillOval(cx - dotR/2, cy + orbitSize/2 - dotR/2, dotR, dotR); // bottom
                g2.fillOval(cx - orbitSize/2 - dotR/2, cy - dotR/2, dotR, dotR); // left
                g2.fillOval(cx + orbitSize/2 - dotR/2, cy - dotR/2, dotR, dotR); // right

                // 3D Orb Image or neutral gradient fallback
                int orbSize = 144;
                if (orbImage != null) {
                    Shape clip = new Ellipse2D.Float(cx - orbSize/2, cy - orbSize/2, orbSize, orbSize);
                    g2.setClip(clip);
                    g2.drawImage(orbImage, cx - orbSize/2, cy - orbSize/2, orbSize, orbSize, null);
                    g2.setClip(null);
                } else {
                    RadialGradientPaint rgp = new RadialGradientPaint(
                        cx - 20, cy - 20, orbSize/2,
                        new float[]{0f, 0.6f, 1f},
                        new Color[]{new Color(0xE0, 0xDF, 0xDC), new Color(0xAC, 0xAB, 0xA8), PRIMARY_MAROON}
                    );
                    g2.setPaint(rgp);
                    g2.fillOval(cx - orbSize/2, cy - orbSize/2, orbSize, orbSize);
                }

                g2.dispose();
            }
        };
        orbComponent.setPreferredSize(new Dimension(200, 190));
        orbContainer.add(orbComponent);
        centerBody.add(orbContainer);

        // Greeting Section
        JPanel greetingPanel = new JPanel();
        greetingPanel.setLayout(new BoxLayout(greetingPanel, BoxLayout.Y_AXIS));
        greetingPanel.setBackground(BG_APP);
        greetingPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        homeGreetingPrefixLabel = new JLabel(UserProfileManager.hasCustomName() ? "Good to see you," : "Welcome");
        homeGreetingPrefixLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        homeGreetingPrefixLabel.setForeground(TEXT_SECONDARY);
        homeGreetingPrefixLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        homeGreetingNameLabel = new JLabel(UserProfileManager.hasCustomName() ? UserProfileManager.getUserName() : "DEX — Your AI Assistant");
        homeGreetingNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 26));
        homeGreetingNameLabel.setForeground(TEXT_PRIMARY);
        homeGreetingNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        homeGreetingSubLabel = new JLabel("How can I help you today?");
        homeGreetingSubLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        homeGreetingSubLabel.setForeground(TEXT_SECONDARY);
        homeGreetingSubLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        greetingPanel.add(homeGreetingPrefixLabel);
        greetingPanel.add(Box.createVerticalStrut(4));
        greetingPanel.add(homeGreetingNameLabel);
        greetingPanel.add(Box.createVerticalStrut(6));
        greetingPanel.add(homeGreetingSubLabel);

        centerBody.add(greetingPanel);
        centerBody.add(Box.createVerticalStrut(20));

        // 5 Action Cards (Chat, Voice, Vision, File Analysis, Tools)
        JPanel actionRow = new JPanel(new GridLayout(1, 5, 10, 0));
        actionRow.setBackground(BG_APP);
        actionRow.setMaximumSize(new Dimension(720, 122));
        actionRow.setPreferredSize(new Dimension(720, 122));
        actionRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        actionRow.add(createActionCard("Chat", "Ask anything", "chat", e -> navigateTo(1)));
        actionRow.add(createActionCard("Voice", "Speak & get insights", "mic", e -> {
            navigateTo(2);
            startInAppMic();
        }));
        actionRow.add(createActionCard("Vision", "Screen analysis", "image", e -> navigateTo(3)));
        actionRow.add(createActionCard("File Analysis", "Documents, code, more", "files", e -> navigateTo(4)));
        actionRow.add(createActionCard("Tools", "Code, math, translate", "wrench", e -> navigateTo(5)));

        centerBody.add(actionRow);
        page.add(centerBody, BorderLayout.CENTER);

        // Bottom Message Input Dock
        JPanel bottomDockPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 12));
        bottomDockPanel.setBackground(BG_APP);

        JPanel dockCapsule = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                // Notion style: 8px border radius, clean white, 1px border
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_PILL);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
            }
        };
        dockCapsule.setOpaque(false);
        dockCapsule.setPreferredSize(new Dimension(650, 48));
        dockCapsule.setBorder(new EmptyBorder(4, 14, 4, 6));

        // Plus Attach Button
        JButton plusBtn = new JButton("+");
        plusBtn.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        plusBtn.setForeground(TEXT_SECONDARY);
        plusBtn.setContentAreaFilled(false);
        plusBtn.setBorderPainted(false);
        plusBtn.setFocusPainted(false);
        plusBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        plusBtn.addActionListener(e -> navigateTo(4));

        homeBottomInput = new JTextField();
        homeBottomInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        homeBottomInput.setForeground(TEXT_PRIMARY);
        homeBottomInput.setCaretColor(PRIMARY_MAROON);
        homeBottomInput.setBorder(null);
        homeBottomInput.setOpaque(false);
        homeBottomInput.putClientProperty("JTextField.placeholderText", "Type a message...");
        homeBottomInput.addActionListener(e -> sendHomeBottomMessage());

        // Mic Button
        JButton micBtn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                DrawIcons.drawNavIcon(g2, "voice", 6, 6, 18, 18, TEXT_SECONDARY);
                g2.dispose();
            }
        };
        micBtn.setPreferredSize(new Dimension(32, 32));
        micBtn.setContentAreaFilled(false);
        micBtn.setBorderPainted(false);
        micBtn.setFocusPainted(false);
        micBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        micBtn.addActionListener(e -> {
            navigateTo(2);
            startInAppMic();
        });

        // Maroon Circular Send Button with Paper Airplane
        JButton sendBtn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PRIMARY_MAROON);
                g2.fillOval(0, 0, getWidth(), getHeight());
                DrawIcons.drawSendAirplane(g2, (getWidth()-18)/2, (getHeight()-18)/2, 18, 18, Color.WHITE);
                g2.dispose();
            }
        };
        sendBtn.setPreferredSize(new Dimension(42, 42));
        sendBtn.setContentAreaFilled(false);
        sendBtn.setBorderPainted(false);
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendBtn.addActionListener(e -> sendHomeBottomMessage());

        JPanel dockRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        dockRight.setOpaque(false);
        dockRight.add(micBtn);
        dockRight.add(sendBtn);

        dockCapsule.add(plusBtn, BorderLayout.WEST);
        dockCapsule.add(homeBottomInput, BorderLayout.CENTER);
        dockCapsule.add(dockRight, BorderLayout.EAST);

        bottomDockPanel.add(dockCapsule);
        page.add(bottomDockPanel, BorderLayout.SOUTH);

        return page;
    }

    private void sendHomeBottomMessage() {
        String msg = homeBottomInput.getText().trim();
        if (msg.isEmpty()) return;
        homeBottomInput.setText("");
        navigateTo(1);
        dispatchPromptToChat(msg);
    }

    private JPanel createActionCard(String title, String desc, String iconType, ActionListener listener) {
        JPanel card = new JPanel(new BorderLayout(0, 6)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                // Notion card: 8px radius, minimal shadow
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBackground(BG_CARD);
        card.setBorder(new EmptyBorder(12, 12, 10, 12));
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JComponent iconBadge = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Notion icon badge: subtle gray square with 6px radius
                g2.setColor(BG_ELEVATED);
                g2.fill(new RoundRectangle2D.Float(0, 0, 30, 30, 6, 6));
                DrawIcons.drawActionIcon(g2, iconType, 6, 6, 18, 18, PRIMARY_MAROON);
                g2.dispose();
            }
        };
        iconBadge.setPreferredSize(new Dimension(30, 30));
        card.add(iconBadge, BorderLayout.NORTH);

        JPanel textPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        textPanel.setOpaque(false);

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        titleLbl.setForeground(TEXT_PRIMARY);

        JLabel descLbl = new JLabel(desc);
        descLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        descLbl.setForeground(TEXT_MUTED);

        textPanel.add(titleLbl);
        textPanel.add(descLbl);
        card.add(textPanel, BorderLayout.CENTER);

        JPanel bottomRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        bottomRow.setOpaque(false);

        JComponent arrowBtn = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_ELEVATED);
                g2.fill(new RoundRectangle2D.Float(0, 0, 20, 20, 4, 4));
                DrawIcons.drawRightArrow(g2, 4, 4, 12, 12, TEXT_SECONDARY);
                g2.dispose();
            }
        };
        arrowBtn.setPreferredSize(new Dimension(20, 20));
        bottomRow.add(arrowBtn);
        card.add(bottomRow, BorderLayout.SOUTH);

        card.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { listener.actionPerformed(null); }
            @Override public void mouseEntered(MouseEvent e) {
                card.setBackground(BG_CARD_ALT);
                card.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                card.setBackground(BG_CARD);
                card.repaint();
            }
        });
        return card;
    }

    // ==================== 3. RIGHT PANEL: RECENT CHATS & TOOLS ====================

    private JPanel createRightPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(BG_APP);
        panel.setPreferredSize(new Dimension(270, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 1, 0, 0, BORDER_SUBTLE),
            new EmptyBorder(16, 14, 14, 14)
        ));

        // Card 1: Recent Chats
        JPanel recentCard = new JPanel(new BorderLayout(0, 8)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                // Notion card: 8px radius, 1px border
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
            }
        };
        recentCard.setOpaque(false);
        recentCard.setBackground(BG_CARD);
        recentCard.setBorder(new EmptyBorder(12, 14, 12, 14));

        JPanel recentHeader = new JPanel(new BorderLayout());
        recentHeader.setOpaque(false);
        JLabel rTitle = new JLabel("Recent Chats");
        rTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        rTitle.setForeground(TEXT_PRIMARY);

        JLabel rSeeAll = new JLabel("See All");
        rSeeAll.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        rSeeAll.setForeground(TEXT_SECONDARY);
        rSeeAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rSeeAll.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { navigateTo(1); }
        });

        recentHeader.add(rTitle, BorderLayout.WEST);
        recentHeader.add(rSeeAll, BorderLayout.EAST);
        recentCard.add(recentHeader, BorderLayout.NORTH);

        JPanel recentList = new JPanel();
        recentList.setLayout(new BoxLayout(recentList, BoxLayout.Y_AXIS));
        recentList.setOpaque(false);

        String[][] recentData = {
            {"Study Plan for Maths", "Can you help me with the midpoint...", "09:15 AM", "school"},
            {"Desktop Diagnostics", "Run system performance & memory audit...", "Yesterday", "tools"},
            {"YouTube Tags", "Give me viral tags for Brawl Stars...", "Yesterday", "play"},
            {"Web App Deployment", "My Vercel deployment shows 404 error...", "2 days ago", "code"},
            {"Gaming Setup Help", "How to build a steering wheel for Xbox...", "3 days ago", "gamepad"},
            {"Instagram Bio", "Give me a bio for handmade crochets...", "4 days ago", "camera"},
            {"Top Drifting Fails Tags", "Give me YouTube tags for top drifting fails...", "5 days ago", "tag"}
        };

        for (String[] r : recentData) {
            recentList.add(createRecentRow(r[0], r[1], r[2], r[3]));
            recentList.add(Box.createVerticalStrut(4));
        }

        recentCard.add(recentList, BorderLayout.CENTER);
        panel.add(recentCard);
        panel.add(Box.createVerticalStrut(12));

        // Card 2: Tools Card (Code, Math, Translate, Notes)
        JPanel toolsCard = new JPanel(new BorderLayout(0, 10)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                // Notion card: 8px radius
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
            }
        };
        toolsCard.setOpaque(false);
        toolsCard.setBackground(BG_CARD);
        toolsCard.setBorder(new EmptyBorder(12, 14, 14, 14));

        JPanel toolsHeader = new JPanel(new BorderLayout());
        toolsHeader.setOpaque(false);
        JLabel tTitle = new JLabel("Tools");
        tTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tTitle.setForeground(TEXT_PRIMARY);

        JLabel tSeeAll = new JLabel("See All");
        tSeeAll.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        tSeeAll.setForeground(TEXT_SECONDARY);
        tSeeAll.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tSeeAll.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { navigateTo(5); }
        });

        toolsHeader.add(tTitle, BorderLayout.WEST);
        toolsHeader.add(tSeeAll, BorderLayout.EAST);
        toolsCard.add(toolsHeader, BorderLayout.NORTH);

        JPanel toolsGrid = new JPanel(new GridLayout(1, 4, 8, 0));
        toolsGrid.setOpaque(false);
        toolsGrid.add(createMiniToolBtn("</>", "Code", e -> executeQuickAction("vscode")));
        toolsGrid.add(createMiniToolBtn("\u229E", "Math", e -> executeQuickAction("calculator")));
        toolsGrid.add(createMiniToolBtn("\u6587A", "Translate", e -> {
            navigateTo(1);
            dispatchPromptToChat("Translate text between languages");
        }));
        toolsGrid.add(createMiniToolBtn("\u270E", "Notes", e -> executeQuickAction("notepad")));

        toolsCard.add(toolsGrid, BorderLayout.CENTER);
        panel.add(toolsCard);

        panel.add(Box.createVerticalGlue());

        // Bottom Status Row: Wi-Fi, Sun, Time, Date
        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setBackground(BG_APP);
        statusRow.setBorder(new EmptyBorder(6, 4, 4, 4));

        JPanel iconGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        iconGroup.setBackground(BG_APP);

        JComponent wifiIcon = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                DrawIcons.drawWifi(g2, 0, 0, 16, 16, TEXT_SECONDARY);
                g2.dispose();
            }
        };
        wifiIcon.setPreferredSize(new Dimension(16, 16));

        JComponent sunIcon = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                DrawIcons.drawSun(g2, 0, 0, 16, 16, TEXT_SECONDARY);
                g2.dispose();
            }
        };
        sunIcon.setPreferredSize(new Dimension(16, 16));

        iconGroup.add(wifiIcon);
        iconGroup.add(sunIcon);

        JPanel clockGroup = new JPanel(new GridLayout(2, 1, 0, 1));
        clockGroup.setBackground(BG_APP);

        rightTimeLabel = new JLabel("09:41 AM");
        rightTimeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        rightTimeLabel.setForeground(TEXT_PRIMARY);
        rightTimeLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        rightDateLabel = new JLabel("28 Oct 2026");
        rightDateLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        rightDateLabel.setForeground(TEXT_SECONDARY);
        rightDateLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        clockGroup.add(rightTimeLabel);
        clockGroup.add(rightDateLabel);

        statusRow.add(iconGroup, BorderLayout.WEST);
        statusRow.add(clockGroup, BorderLayout.EAST);
        panel.add(statusRow);

        return panel;
    }

    private JPanel createRecentRow(String title, String desc, String time, String iconType) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(4, 2, 4, 2));
        row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JComponent icon = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD_ALT);
                g2.fillOval(0, 0, 26, 26);
                DrawIcons.drawRecentIcon(g2, iconType, 5, 5, 16, 16, PRIMARY_MAROON);
                g2.dispose();
            }
        };
        icon.setPreferredSize(new Dimension(26, 26));

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 1));
        text.setOpaque(false);
        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 11));
        titleLbl.setForeground(TEXT_PRIMARY);

        JLabel descLbl = new JLabel(desc.length() > 22 ? desc.substring(0, 22) + "..." : desc);
        descLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        descLbl.setForeground(TEXT_MUTED);

        text.add(titleLbl);
        text.add(descLbl);

        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 4));
        rightSide.setOpaque(false);
        JLabel timeLbl = new JLabel(time);
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLbl.setForeground(TEXT_MUTED);

        JLabel arrowLbl = new JLabel(">");
        arrowLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        arrowLbl.setForeground(TEXT_MUTED);

        rightSide.add(timeLbl);
        rightSide.add(arrowLbl);

        row.add(icon, BorderLayout.WEST);
        row.add(text, BorderLayout.CENTER);
        row.add(rightSide, BorderLayout.EAST);

        row.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                navigateTo(1);
                dispatchPromptToChat(title);
            }
        });
        return row;
    }

    private JPanel createMiniToolBtn(String symbol, String label, ActionListener listener) {
        JPanel btn = new JPanel(new BorderLayout(0, 4)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                // Notion: small 6px radius squares
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 6, 6));
                g2.dispose();
            }
        };
        btn.setOpaque(false);
        btn.setBackground(BG_CARD_ALT);
        btn.setBorder(new EmptyBorder(8, 4, 8, 4));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        JLabel symLbl = new JLabel(symbol);
        symLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        symLbl.setForeground(TEXT_PRIMARY);
        symLbl.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel textLbl = new JLabel(label);
        textLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        textLbl.setForeground(TEXT_SECONDARY);
        textLbl.setHorizontalAlignment(SwingConstants.CENTER);

        btn.add(symLbl, BorderLayout.CENTER);
        btn.add(textLbl, BorderLayout.SOUTH);

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) { listener.actionPerformed(null); }
            @Override public void mouseEntered(MouseEvent e) {
                btn.setBackground(BG_ELEVATED);
                btn.repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                btn.setBackground(BG_CARD_ALT);
                btn.repaint();
            }
        });
        return btn;
    }

    private void startClockTimer() {
        Timer timer = new Timer(1000, e -> {
            Date now = new Date();
            if (rightTimeLabel != null) {
                rightTimeLabel.setText(new SimpleDateFormat("hh:mm a").format(now));
            }
            if (rightDateLabel != null) {
                rightDateLabel.setText(new SimpleDateFormat("dd MMM yyyy").format(now));
            }
        });
        timer.start();
    }

    // ==================== 4. CHAT PAGE (PAGE 1) ====================

    private JPanel createChatPage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_APP);

        // Header: Back arrow, logo, title, provider badge, clear chat
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(BG_CARD);
        chatHeader.setBorder(BorderFactory.createCompoundBorder(
            new MatteBorder(0, 0, 1, 0, BORDER_SUBTLE),
            new EmptyBorder(12, 20, 12, 20)
        ));

        JPanel leftGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftGroup.setOpaque(false);

        JButton backBtn = new JButton("< Home");
        backBtn.setFont(FONT_BOLD);
        backBtn.setForeground(PRIMARY_MAROON);
        backBtn.setContentAreaFilled(false);
        backBtn.setBorderPainted(false);
        backBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        backBtn.addActionListener(e -> navigateTo(0));

        JLabel titleLbl = new JLabel("AI Assistant \u2022 Chat");
        titleLbl.setFont(FONT_HEADING);
        titleLbl.setForeground(TEXT_TITLE_MAROON);

        leftGroup.add(backBtn);
        leftGroup.add(titleLbl);
        chatHeader.add(leftGroup, BorderLayout.WEST);

        JPanel rightGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightGroup.setOpaque(false);

        JButton clearBtn = new JButton("Clear Session");
        clearBtn.setFont(FONT_SMALL);
        clearBtn.setForeground(TEXT_SECONDARY);
        clearBtn.setContentAreaFilled(false);
        clearBtn.setBorder(BorderFactory.createLineBorder(BORDER_SUBTLE, 1));
        clearBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        clearBtn.addActionListener(e -> {
            aiClient.getMemoryManager().clearChat();
            chatMessagesBox.removeAll();
            addSampleChatThread();
            chatMessagesBox.revalidate();
            chatMessagesBox.repaint();
        });

        chatStatusLabel = new JLabel("Provider: " + ProviderManager.getActiveProviderType().getDisplayName());
        chatStatusLabel.setFont(FONT_SMALL);
        chatStatusLabel.setForeground(SUCCESS);

        rightGroup.add(chatStatusLabel);
        rightGroup.add(clearBtn);
        chatHeader.add(rightGroup, BorderLayout.EAST);

        page.add(chatHeader, BorderLayout.NORTH);

        // Chat Message Box (Scrollable)
        chatMessagesBox = new JPanel();
        chatMessagesBox.setLayout(new BoxLayout(chatMessagesBox, BoxLayout.Y_AXIS));
        chatMessagesBox.setBackground(BG_APP);
        chatMessagesBox.setBorder(new EmptyBorder(16, 24, 16, 24));

        addSampleChatThread();

        chatScrollPane = new JScrollPane(chatMessagesBox);
        chatScrollPane.setBorder(null);
        chatScrollPane.getViewport().setBackground(BG_APP);
        chatScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        page.add(chatScrollPane, BorderLayout.CENTER);

        // Bottom Message Dock
        JPanel bottomDockPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 16));
        bottomDockPanel.setBackground(BG_APP);

        JPanel dockCapsule = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
            }
        };
        dockCapsule.setOpaque(false);
        dockCapsule.setPreferredSize(new Dimension(680, 52));
        dockCapsule.setBorder(new EmptyBorder(4, 14, 4, 6));

        JButton plusBtn = new JButton("+");
        plusBtn.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        plusBtn.setForeground(TEXT_SECONDARY);
        plusBtn.setContentAreaFilled(false);
        plusBtn.setBorderPainted(false);
        plusBtn.setFocusPainted(false);
        plusBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        plusBtn.addActionListener(e -> navigateTo(4));

        chatInput = new JTextField();
        chatInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatInput.setForeground(TEXT_PRIMARY);
        chatInput.setCaretColor(PRIMARY_MAROON);
        chatInput.setBorder(null);
        chatInput.setOpaque(false);
        chatInput.putClientProperty("JTextField.placeholderText", "Type a message...");
        chatInput.addActionListener(e -> sendChatMessage());

        JButton micBtn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                DrawIcons.drawNavIcon(g2, "voice", 6, 6, 18, 18, TEXT_SECONDARY);
                g2.dispose();
            }
        };
        micBtn.setPreferredSize(new Dimension(32, 32));
        micBtn.setContentAreaFilled(false);
        micBtn.setBorderPainted(false);
        micBtn.setFocusPainted(false);
        micBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        micBtn.addActionListener(e -> {
            navigateTo(2);
            startInAppMic();
        });

        JButton sendBtn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PRIMARY_MAROON);
                g2.fillOval(0, 0, getWidth(), getHeight());
                DrawIcons.drawSendAirplane(g2, (getWidth()-18)/2, (getHeight()-18)/2, 18, 18, Color.WHITE);
                g2.dispose();
            }
        };
        sendBtn.setPreferredSize(new Dimension(42, 42));
        sendBtn.setContentAreaFilled(false);
        sendBtn.setBorderPainted(false);
        sendBtn.setFocusPainted(false);
        sendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        sendBtn.addActionListener(e -> sendChatMessage());

        JPanel dockRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        dockRight.setOpaque(false);
        dockRight.add(micBtn);
        dockRight.add(sendBtn);

        dockCapsule.add(plusBtn, BorderLayout.WEST);
        dockCapsule.add(chatInput, BorderLayout.CENTER);
        dockCapsule.add(dockRight, BorderLayout.EAST);

        bottomDockPanel.add(dockCapsule);
        page.add(bottomDockPanel, BorderLayout.SOUTH);

        return page;
    }

    private void addSampleChatThread() {
        addUserChatBubble("What is Web3?", "09:41 AM");
        addAssistantChatBubble(
            "Web3 is a decentralized internet built on blockchain technology, giving users control over their data, identity, and digital assets.\n\n" +
            "Key Features of Web3:\n" +
            "\u2022 Decentralization\n  No central authority controls the network.\n" +
            "\u2022 User Ownership\n  You own your data and assets.\n" +
            "\u2022 Blockchain Technology\n  Ensures transparency and security.",
            "09:41 AM"
        );
    }

    private void sendChatMessage() {
        String msg = chatInput.getText().trim();
        if (msg.isEmpty()) return;
        chatInput.setText("");
        dispatchPromptToChat(msg);
    }

    private void dispatchPromptToChat(String message) {
        String time = new SimpleDateFormat("hh:mm a").format(new Date());
        addUserChatBubble(message, time);

        // Async AI Request with verified tools execution
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override protected String doInBackground() {
                try {
                    return aiClient.chat(message);
                } catch (Exception ex) {
                    return "Error from AI Provider: " + ex.getMessage();
                }
            }

            @Override protected void done() {
                try {
                    String reply = get();
                    String replyTime = new SimpleDateFormat("hh:mm a").format(new Date());
                    addAssistantChatBubble(reply, replyTime);
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void addUserChatBubble(String text, String time) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        row.setOpaque(false);

        JPanel bubble = new JPanel(new BorderLayout(0, 4)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CHAT_USER_BUBBLE);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.dispose();
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(12, 16, 10, 16));

        JLabel msgLbl = new JLabel("<html><p style='width:360px;'>" + escapeHtml(text) + "</p></html>");
        msgLbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        msgLbl.setForeground(TEXT_PRIMARY);

        JPanel timeRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        timeRow.setOpaque(false);
        JLabel timeLbl = new JLabel(time + " \u2713\u2713");
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLbl.setForeground(TEXT_SECONDARY);
        timeRow.add(timeLbl);

        bubble.add(msgLbl, BorderLayout.CENTER);
        bubble.add(timeRow, BorderLayout.SOUTH);

        row.add(bubble);
        chatMessagesBox.add(row);
        chatMessagesBox.revalidate();
        scrollChatToBottom();
    }

    private void addAssistantChatBubble(String text, String time) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        row.setOpaque(false);

        // Assistant Logo Emblem
        JComponent emblem = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                DrawIcons.drawRosetteLogo(g2, 0, 0, 28, 28, PRIMARY_MAROON);
                g2.dispose();
            }
        };
        emblem.setPreferredSize(new Dimension(28, 28));
        row.add(emblem);

        JPanel bubble = new JPanel(new BorderLayout(0, 8)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(CHAT_BOT_BUBBLE);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(new EmptyBorder(14, 18, 12, 18));

        String htmlText = text.replace("\n", "<br>");
        JLabel msgLbl = new JLabel("<html><p style='width:440px;line-height:1.4;'>" + htmlText + "</p></html>");
        msgLbl.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        msgLbl.setForeground(TEXT_PRIMARY);

        JPanel bottomRow = new JPanel(new BorderLayout());
        bottomRow.setOpaque(false);
        JLabel timeLbl = new JLabel(time);
        timeLbl.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLbl.setForeground(TEXT_MUTED);

        JButton copyBtn = new JButton("\u2398 Copy");
        copyBtn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        copyBtn.setForeground(TEXT_SECONDARY);
        copyBtn.setContentAreaFilled(false);
        copyBtn.setBorderPainted(false);
        copyBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copyBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            JOptionPane.showMessageDialog(this, "Copied response to clipboard.");
        });

        bottomRow.add(timeLbl, BorderLayout.WEST);
        bottomRow.add(copyBtn, BorderLayout.EAST);

        bubble.add(msgLbl, BorderLayout.CENTER);
        bubble.add(bottomRow, BorderLayout.SOUTH);

        row.add(bubble);
        chatMessagesBox.add(row);
        chatMessagesBox.revalidate();
        scrollChatToBottom();
    }

    private void scrollChatToBottom() {
        SwingUtilities.invokeLater(() -> {
            if (chatScrollPane != null) {
                JScrollBar v = chatScrollPane.getVerticalScrollBar();
                v.setValue(v.getMaximum());
            }
        });
    }

    private String escapeHtml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // ==================== 5. VOICE PAGE (PAGE 2) ====================

    private JPanel createVoicePage() {
        JPanel page = new JPanel(new BorderLayout(0, 0));
        page.setBackground(BG_APP);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setBackground(BG_APP);
        center.setBorder(new EmptyBorder(24, 32, 24, 32));

        // Header Title & Subtitle
        JLabel title = new JLabel("Voice Assistant");
        title.setFont(new Font("Segoe UI", Font.BOLD, 26));
        title.setForeground(TEXT_TITLE_MAROON);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel subTitle = new JLabel("Real-time in-app microphone listening and speech intelligence");
        subTitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        subTitle.setForeground(TEXT_SECONDARY);
        subTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Status Pill Capsule
        JPanel statusPill = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 4)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 6, 6));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 6, 6));
                g2.dispose();
            }
        };
        statusPill.setOpaque(false);
        statusPill.setMaximumSize(new Dimension(520, 34));
        statusPill.setPreferredSize(new Dimension(520, 34));
        statusPill.setAlignmentX(Component.CENTER_ALIGNMENT);

        voiceStatusDot = new JComponent() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color dotColor = isVoiceListening ? new Color(0xE5, 0x39, 0x35) : (VoiceController.isSpeaking() ? PRIMARY_MAROON : SUCCESS);
                g2.setColor(dotColor);
                g2.fillOval(2, 2, 8, 8);
                if (isVoiceListening) {
                    g2.setColor(new Color(0xE5, 0x39, 0x35, 80));
                    g2.drawOval(0, 0, 12, 12);
                }
                g2.dispose();
            }
        };
        voiceStatusDot.setPreferredSize(new Dimension(14, 14));

        voiceStatusText = new JLabel("Microphone Ready • Click the mic button to speak");
        voiceStatusText.setFont(new Font("Segoe UI", Font.BOLD, 12));
        voiceStatusText.setForeground(TEXT_PRIMARY);

        statusPill.add(voiceStatusDot);
        statusPill.add(voiceStatusText);

        // Pulsing Glowing Silk Orb (Properly cleared, zero ghosting)
        voiceOrbDisplay = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                int cx = getWidth() / 2;
                int cy = getHeight() / 2;

                // Halo glow pulse
                float pulse = (float) (Math.sin(voiceOrbPhase) * (isVoiceListening ? 16 : 8));
                int haloR = isVoiceListening ? 92 : 78;
                Color haloColor = isVoiceListening ? new Color(0x37, 0x35, 0x2F, 50) : new Color(0xEF, 0xEE, 0xEC, 70);
                g2.setColor(haloColor);
                g2.fillOval(cx - haloR - (int)pulse, cy - haloR - (int)pulse, (haloR*2) + (int)(pulse*2), (haloR*2) + (int)(pulse*2));

                // Dashed orbit ring
                int orbitSize = 176;
                float[] dash = {4f, 4f};
                g2.setColor(new Color(0xAC, 0xAB, 0xA8, 80));
                g2.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10f, dash, 0f));
                g2.drawOval(cx - orbitSize/2, cy - orbitSize/2, orbitSize, orbitSize);

                // Orb Image / radial gradient
                int orbSize = 132;
                if (orbImage != null) {
                    Shape clip = new Ellipse2D.Float(cx - orbSize/2, cy - orbSize/2, orbSize, orbSize);
                    g2.setClip(clip);
                    g2.drawImage(orbImage, cx - orbSize/2, cy - orbSize/2, orbSize, orbSize, null);
                    g2.setClip(null);
                } else {
                    g2.setColor(PRIMARY_MAROON);
                    g2.fillOval(cx - orbSize/2, cy - orbSize/2, orbSize, orbSize);
                }
                g2.dispose();
            }
        };
        voiceOrbDisplay.setOpaque(false);
        voiceOrbDisplay.setPreferredSize(new Dimension(240, 200));
        voiceOrbDisplay.setMaximumSize(new Dimension(240, 200));
        voiceOrbDisplay.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Animated Waveform Visualizer
        voiceWaveformPanel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int totalBars = voiceWaveformHeights.length;
                int barWidth = 6;
                int barGap = 5;
                int totalW = (totalBars * barWidth) + ((totalBars - 1) * barGap);
                int startX = (getWidth() - totalW) / 2;
                int midY = getHeight() / 2;

                for (int i = 0; i < totalBars; i++) {
                    float h = voiceWaveformHeights[i];
                    if (!isVoiceListening && !VoiceController.isSpeaking()) {
                        h = 4f;
                    }
                    int barH = Math.max(4, (int) h);
                    g2.setColor(isVoiceListening ? new Color(0xC7, 0x2C, 0x48) : PRIMARY_MAROON);
                    g2.fillRoundRect(startX + (i * (barWidth + barGap)), midY - barH/2, barWidth, barH, 4, 4);
                }
                g2.dispose();
            }
        };
        voiceWaveformPanel.setOpaque(false);
        voiceWaveformPanel.setPreferredSize(new Dimension(320, 32));
        voiceWaveformPanel.setMaximumSize(new Dimension(320, 32));
        voiceWaveformPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Timer for animating Orb + Waveform
        voiceOrbTimer = new Timer(40, e -> {
            voiceOrbPhase += 0.12f;
            if (isVoiceListening) {
                for (int i = 0; i < voiceWaveformHeights.length; i++) {
                    voiceWaveformHeights[i] = (float) (6 + Math.abs(Math.sin(voiceOrbPhase * 1.8 + i * 0.45)) * 24);
                }
            } else if (VoiceController.isSpeaking()) {
                for (int i = 0; i < voiceWaveformHeights.length; i++) {
                    voiceWaveformHeights[i] = (float) (4 + Math.abs(Math.sin(voiceOrbPhase * 1.2 + i * 0.35)) * 16);
                }
            }
            voiceOrbDisplay.repaint();
            voiceWaveformPanel.repaint();
            if (voiceStatusDot != null) voiceStatusDot.repaint();
        });
        voiceOrbTimer.start();

        // Control Row with In-Built App Mic Button
        JPanel controlRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 0));
        controlRow.setBackground(BG_APP);
        controlRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        voiceStopBtn = new JButton("■ Stop");
        voiceStopBtn.setFont(FONT_BOLD);
        voiceStopBtn.setForeground(TEXT_PRIMARY);
        voiceStopBtn.setBackground(BG_CARD);
        voiceStopBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
            new EmptyBorder(8, 16, 8, 16)
        ));
        voiceStopBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        voiceStopBtn.addActionListener(e -> stopInAppMicAndSpeech());

        voiceMainMicBtn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int size = Math.min(getWidth(), getHeight());
                int x = (getWidth() - size) / 2;
                int y = (getHeight() - size) / 2;

                if (isVoiceListening) {
                    float pulse = (float)(Math.sin(voiceOrbPhase * 3) * 4);
                    g2.setColor(new Color(0xE5, 0x39, 0x35, 120));
                    g2.fillOval(x - 6 - (int)pulse, y - 6 - (int)pulse, size + 12 + (int)(pulse*2), size + 12 + (int)(pulse*2));
                    g2.setColor(new Color(0xC7, 0x2C, 0x48));
                } else {
                    g2.setColor(PRIMARY_MAROON);
                }
                g2.fillOval(x, y, size, size);

                // Draw White Mic Icon in Center
                DrawIcons.drawNavIcon(g2, "voice", x + (size - 22)/2, y + (size - 22)/2, 22, 22, Color.WHITE);
                g2.dispose();
            }
        };
        voiceMainMicBtn.setPreferredSize(new Dimension(64, 64));
        voiceMainMicBtn.setContentAreaFilled(false);
        voiceMainMicBtn.setBorderPainted(false);
        voiceMainMicBtn.setFocusPainted(false);
        voiceMainMicBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        voiceMainMicBtn.setToolTipText("Click to toggle in-built app microphone");
        voiceMainMicBtn.addActionListener(e -> toggleInAppMic());

        JButton winHBtn = new JButton("Win + H");
        winHBtn.setFont(FONT_BOLD);
        winHBtn.setForeground(TEXT_SECONDARY);
        winHBtn.setBackground(BG_CARD);
        winHBtn.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
            new EmptyBorder(8, 16, 8, 16)
        ));
        winHBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        winHBtn.setToolTipText("Trigger native Windows Voice Typing");
        winHBtn.addActionListener(e -> VoiceController.triggerWindowsVoiceTyping());

        controlRow.add(voiceStopBtn);
        controlRow.add(voiceMainMicBtn);
        controlRow.add(winHBtn);

        // TTS Checkbox
        voiceTtsCheckBox = new JCheckBox("Speak AI replies aloud (TTS)", true);
        voiceTtsCheckBox.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        voiceTtsCheckBox.setForeground(TEXT_SECONDARY);
        voiceTtsCheckBox.setOpaque(false);
        voiceTtsCheckBox.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Live Transcript Card
        JPanel transcriptCard = new JPanel(new BorderLayout(0, 8)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, 8, 8));
                g2.dispose();
            }
        };
        transcriptCard.setOpaque(false);
        transcriptCard.setMaximumSize(new Dimension(680, 110));
        transcriptCard.setPreferredSize(new Dimension(680, 110));
        transcriptCard.setBorder(new EmptyBorder(12, 18, 12, 18));
        transcriptCard.setAlignmentX(Component.CENTER_ALIGNMENT);

        voiceUserTranscript = new JLabel("<html><span style='color:#787774;'>You said:</span> <span style='color:#37352F;'>Ready to listen. Click the microphone above to start speaking.</span></html>");
        voiceUserTranscript.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        voiceAiReply = new JLabel("<html><span style='color:#37352F;font-weight:bold;'>DEX:</span> <span style='color:#37352F;'>How can I assist you today?</span></html>");
        voiceAiReply.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        JPanel transcriptBox = new JPanel(new GridLayout(2, 1, 0, 6));
        transcriptBox.setOpaque(false);
        transcriptBox.add(voiceUserTranscript);
        transcriptBox.add(voiceAiReply);

        transcriptCard.add(transcriptBox, BorderLayout.CENTER);

        // Direct Text Input Dock at bottom of Voice page
        JPanel voiceInputDock = new JPanel(new BorderLayout(10, 0)) {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, 8, 8));
                g2.dispose();
            }
        };
        voiceInputDock.setOpaque(false);
        voiceInputDock.setMaximumSize(new Dimension(680, 44));
        voiceInputDock.setPreferredSize(new Dimension(680, 44));
        voiceInputDock.setBorder(new EmptyBorder(4, 16, 4, 6));
        voiceInputDock.setAlignmentX(Component.CENTER_ALIGNMENT);

        voiceDirectInput = new JTextField();
        voiceDirectInput.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        voiceDirectInput.setForeground(TEXT_PRIMARY);
        voiceDirectInput.setCaretColor(PRIMARY_MAROON);
        voiceDirectInput.setBorder(null);
        voiceDirectInput.setOpaque(false);
        voiceDirectInput.putClientProperty("JTextField.placeholderText", "Or type your voice command here and press Enter...");
        voiceDirectInput.addActionListener(e -> {
            String text = voiceDirectInput.getText().trim();
            if (!text.isEmpty()) {
                voiceDirectInput.setText("");
                processVoiceTranscript(text);
            }
        });

        JButton voiceSendBtn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(PRIMARY_MAROON);
                g2.fillOval(0, 0, getWidth(), getHeight());
                DrawIcons.drawSendAirplane(g2, (getWidth()-16)/2, (getHeight()-16)/2, 16, 16, Color.WHITE);
                g2.dispose();
            }
        };
        voiceSendBtn.setPreferredSize(new Dimension(34, 34));
        voiceSendBtn.setContentAreaFilled(false);
        voiceSendBtn.setBorderPainted(false);
        voiceSendBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        voiceSendBtn.addActionListener(e -> {
            String text = voiceDirectInput.getText().trim();
            if (!text.isEmpty()) {
                voiceDirectInput.setText("");
                processVoiceTranscript(text);
            }
        });

        voiceInputDock.add(voiceDirectInput, BorderLayout.CENTER);
        voiceInputDock.add(voiceSendBtn, BorderLayout.EAST);

        // Assemble with proper vertical spacing
        center.add(title);
        center.add(Box.createVerticalStrut(4));
        center.add(subTitle);
        center.add(Box.createVerticalStrut(12));
        center.add(statusPill);
        center.add(Box.createVerticalStrut(14));
        center.add(voiceOrbDisplay);
        center.add(Box.createVerticalStrut(8));
        center.add(voiceWaveformPanel);
        center.add(Box.createVerticalStrut(12));
        center.add(controlRow);
        center.add(Box.createVerticalStrut(8));
        center.add(voiceTtsCheckBox);
        center.add(Box.createVerticalStrut(12));
        center.add(transcriptCard);
        center.add(Box.createVerticalStrut(10));
        center.add(voiceInputDock);

        page.add(center, BorderLayout.CENTER);
        return page;
    }

    private void toggleInAppMic() {
        if (isVoiceListening) {
            stopInAppMicAndSpeech();
        } else {
            startInAppMic();
        }
    }

    private void startInAppMic() {
        if (isVoiceListening) return;
        isVoiceListening = true;
        VoiceController.stopSpeaking();
        if (voiceStatusText != null) {
            voiceStatusText.setText("Listening to your microphone... Speak clearly now.");
        }
        if (voiceStatusDot != null) voiceStatusDot.repaint();
        if (voiceMainMicBtn != null) voiceMainMicBtn.repaint();

        voiceListenWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                return VoiceController.listenFromMicrophone(8);
            }

            @Override
            protected void done() {
                isVoiceListening = false;
                if (voiceMainMicBtn != null) voiceMainMicBtn.repaint();
                if (voiceStatusDot != null) voiceStatusDot.repaint();

                try {
                    String transcript = get();
                    if (transcript != null && !transcript.isBlank()) {
                        processVoiceTranscript(transcript);
                    } else {
                        if (voiceStatusText != null) {
                            voiceStatusText.setText("No speech detected. Click the microphone to try again.");
                        }
                    }
                } catch (Exception ex) {
                    if (voiceStatusText != null) {
                        voiceStatusText.setText("Microphone ready. Click to speak.");
                    }
                }
            }
        };
        voiceListenWorker.execute();
    }

    private void stopInAppMicAndSpeech() {
        isVoiceListening = false;
        VoiceController.cancelListening();
        VoiceController.stopSpeaking();
        if (voiceListenWorker != null && !voiceListenWorker.isDone()) {
            voiceListenWorker.cancel(true);
        }
        if (voiceStatusText != null) {
            voiceStatusText.setText("Microphone stopped. Click to speak.");
        }
        if (voiceStatusDot != null) voiceStatusDot.repaint();
        if (voiceMainMicBtn != null) voiceMainMicBtn.repaint();
        for (int i = 0; i < voiceWaveformHeights.length; i++) {
            voiceWaveformHeights[i] = 4f;
        }
        if (voiceWaveformPanel != null) voiceWaveformPanel.repaint();
    }

    private void processVoiceTranscript(String text) {
        if (text == null || text.isBlank()) return;
        if (voiceStatusText != null) {
            voiceStatusText.setText("Processing with DEX AI...");
        }
        if (voiceUserTranscript != null) {
            voiceUserTranscript.setText("<html><span style='color:#787774;'>You said:</span> <span style='color:#37352F;'>\"" + escapeHtml(text) + "\"</span></html>");
        }
        if (voiceAiReply != null) {
            voiceAiReply.setText("<html><span style='color:#37352F;font-weight:bold;'>DEX:</span> <span style='color:#787774;'>Thinking...</span></html>");
        }

        SwingWorker<String, Void> aiWorker = new SwingWorker<>() {
            @Override
            protected String doInBackground() {
                try {
                    return aiClient.chat(text);
                } catch (Exception ex) {
                    return "Error: " + ex.getMessage();
                }
            }

            @Override
            protected void done() {
                try {
                    String reply = get();
                    if (voiceAiReply != null) {
                        voiceAiReply.setText("<html><span style='color:#37352F;font-weight:bold;'>DEX:</span> <span style='color:#37352F;'>" + escapeHtml(reply).replace("\n", "<br>") + "</span></html>");
                    }
                    if (voiceStatusText != null) {
                        voiceStatusText.setText(voiceTtsCheckBox != null && voiceTtsCheckBox.isSelected() ? "Speaking response aloud..." : "Response ready.");
                    }
                    if (voiceTtsCheckBox != null && voiceTtsCheckBox.isSelected()) {
                        VoiceController.speak(reply);
                    }
                    Timer resetTimer = new Timer(4000, e -> {
                        if (!isVoiceListening && voiceStatusText != null) {
                            voiceStatusText.setText("Microphone ready. Click to speak.");
                            if (voiceStatusDot != null) voiceStatusDot.repaint();
                        }
                    });
                    resetTimer.setRepeats(false);
                    resetTimer.start();
                } catch (Exception ex) {
                    if (voiceStatusText != null) {
                        voiceStatusText.setText("Error processing voice request: " + ex.getMessage());
                    }
                }
            }
        };
        aiWorker.execute();
    }

    // ==================== 6. SCREEN VISION & ANALYSIS PAGE (PAGE 3) ====================

    private JPanel createImagePage() {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setBackground(BG_APP);
        page.setBorder(new EmptyBorder(24, 32, 24, 32));

        JLabel title = new JLabel("Screen Vision & Desktop Analysis");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_TITLE_MAROON);
        page.add(title, BorderLayout.NORTH);

        // Desktop Screen Analysis Card (Image Generation removed per specification)
        JPanel screenCard = createTitledCard("Desktop Screen Analysis");
        JTextField promptField = new JTextField("What is currently visible on my screen?");
        promptField.setFont(FONT_BODY);
        promptField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));

        JTextArea resultArea = new JTextArea("Click 'Capture Screen' to take a desktop screenshot.");
        resultArea.setFont(FONT_BODY);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);
        resultArea.setEditable(false);
        resultArea.setBackground(BG_CARD_ALT);
        resultArea.setBorder(new EmptyBorder(12, 12, 12, 12));

        JButton captureBtn = new JButton("Capture Screen");
        captureBtn.setFont(FONT_BOLD);
        captureBtn.setForeground(Color.WHITE);
        captureBtn.setBackground(PRIMARY_MAROON);
        captureBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        captureBtn.addActionListener(e -> {
            resultArea.setText("Capturing desktop screenshot...");
            SwingWorker<String, Void> worker = new SwingWorker<>() {
                @Override protected String doInBackground() {
                    return SystemTools.takeScreenshot("");
                }
                @Override protected void done() {
                    try { resultArea.setText(get()); } catch (Exception ignored) {}
                }
            };
            worker.execute();
        });

        screenCard.add(promptField, BorderLayout.NORTH);
        screenCard.add(new JScrollPane(resultArea), BorderLayout.CENTER);
        screenCard.add(captureBtn, BorderLayout.SOUTH);

        page.add(screenCard, BorderLayout.CENTER);
        return page;
    }

    // ==================== 7. FILES & MEMORY PAGE (PAGE 4) ====================

    private JPanel createFilesPage() {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setBackground(BG_APP);
        page.setBorder(new EmptyBorder(24, 32, 24, 32));

        JLabel title = new JLabel("Files & Memory Manager");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_TITLE_MAROON);
        page.add(title, BorderLayout.NORTH);

        JPanel body = createTitledCard("Active Workspace & Persistent Memory");

        JTextArea memArea = new JTextArea();
        memArea.setFont(FONT_MONO);
        memArea.setEditable(false);
        memArea.setBackground(BG_CARD_ALT);
        memArea.setBorder(new EmptyBorder(12, 12, 12, 12));

        MemoryManager mm = aiClient.getMemoryManager();
        StringBuilder sb = new StringBuilder();
        sb.append("=== DEX PERSISTENT MEMORY ===\n");
        sb.append("Stored conversation messages: ").append(mm.size()).append("\n\n");
        sb.append("Recent Memory Snippets:\n");
        List<MemoryManager.ChatMessage> allMsgs = mm.getAllMessages();
        int sIndex = Math.max(0, allMsgs.size() - 8);
        for (int i = sIndex; i < allMsgs.size(); i++) {
            MemoryManager.ChatMessage m = allMsgs.get(i);
            sb.append("[").append(m.getRole().toUpperCase()).append("]: ").append(m.getContent()).append("\n\n");
        }
        memArea.setText(sb.toString());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);

        JButton openFolderBtn = new JButton("Open Workspace Folder");
        openFolderBtn.setFont(FONT_BODY);
        openFolderBtn.addActionListener(e -> executeQuickAction("file explorer"));

        JButton clearMemBtn = new JButton("Clear Memory");
        clearMemBtn.setFont(FONT_BODY);
        clearMemBtn.setForeground(ERROR);
        clearMemBtn.addActionListener(e -> {
            mm.clearChat();
            memArea.setText("Memory cleared.");
            JOptionPane.showMessageDialog(this, "Persistent memory reset.");
        });

        actions.add(openFolderBtn);
        actions.add(clearMemBtn);

        body.add(new JScrollPane(memArea), BorderLayout.CENTER);
        body.add(actions, BorderLayout.SOUTH);

        page.add(body, BorderLayout.CENTER);
        return page;
    }

    // ==================== 8. TOOLS PAGE (PAGE 5) ====================

    private JPanel createToolsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 16));
        page.setBackground(BG_APP);
        page.setBorder(new EmptyBorder(24, 32, 24, 32));

        JLabel title = new JLabel("Windows Control & Tools");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(TEXT_TITLE_MAROON);
        page.add(title, BorderLayout.NORTH);

        JTabbedPane toolsTabs = new JTabbedPane();
        toolsTabs.setFont(FONT_BOLD);

        // Tab A: Windows Remote Apps
        JPanel appsPanel = new JPanel(new GridLayout(2, 4, 14, 14));
        appsPanel.setBackground(BG_APP);
        appsPanel.setBorder(new EmptyBorder(16, 16, 16, 16));
        appsPanel.add(createActionCard("VS Code", "Open editor", "code", e -> executeQuickAction("vscode")));
        appsPanel.add(createActionCard("Calculator", "Launch calculator", "calc", e -> executeQuickAction("calculator")));
        appsPanel.add(createActionCard("Chrome", "Open web browser", "browser", e -> executeQuickAction("chrome")));
        appsPanel.add(createActionCard("Notepad", "Open text editor", "notes", e -> executeQuickAction("notepad")));
        appsPanel.add(createActionCard("YouTube", "Browse YouTube", "play", e -> executeQuickAction("youtube")));
        appsPanel.add(createActionCard("File Explorer", "Open files", "files", e -> executeQuickAction("file explorer")));
        appsPanel.add(createActionCard("Settings", "Windows settings", "settings", e -> executeQuickAction("settings")));
        appsPanel.add(createActionCard("Terminal", "Open command prompt", "terminal", e -> executeQuickAction("cmd")));
        toolsTabs.addTab("Applications", appsPanel);

        // Tab B: Browser Engine (full original feature)
        JPanel browserPanel = new JPanel(new BorderLayout(0, 10));
        browserPanel.setBackground(BG_APP);
        browserPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel browserTop = new JPanel(new GridLayout(2, 1, 0, 8));
        browserTop.setOpaque(false);

        JPanel navRow = new JPanel(new BorderLayout(8, 0));
        navRow.setOpaque(false);
        browserUrlField = new JTextField("https://en.wikipedia.org/wiki/Artificial_intelligence");
        browserUrlField.setFont(FONT_BODY);
        JButton navBtn = new JButton("Navigate & Extract");
        navBtn.setFont(FONT_BOLD);
        navBtn.setBackground(PRIMARY_MAROON);
        navBtn.setForeground(Color.WHITE);
        navBtn.addActionListener(e -> doBrowserNavigate());
        navRow.add(browserUrlField, BorderLayout.CENTER);
        navRow.add(navBtn, BorderLayout.EAST);

        JPanel searchRow = new JPanel(new BorderLayout(8, 0));
        searchRow.setOpaque(false);
        browserSearchField = new JTextField();
        browserSearchField.setFont(FONT_BODY);
        browserSearchField.putClientProperty("JTextField.placeholderText", "Search Google, YouTube, GitHub...");
        browserPlatformCombo = new JComboBox<>(new String[]{"Google", "YouTube", "GitHub", "DuckDuckGo"});
        JButton searchBtn = new JButton("Search");
        searchBtn.setFont(FONT_BOLD);
        searchBtn.addActionListener(e -> doBrowserSearch());
        searchRow.add(browserPlatformCombo, BorderLayout.WEST);
        searchRow.add(browserSearchField, BorderLayout.CENTER);
        searchRow.add(searchBtn, BorderLayout.EAST);

        browserTop.add(navRow);
        browserTop.add(searchRow);
        browserPanel.add(browserTop, BorderLayout.NORTH);

        browserResultArea = new JTextArea("Browser extraction results will appear here.");
        browserResultArea.setFont(FONT_MONO);
        browserResultArea.setEditable(false);
        browserResultArea.setBackground(BG_CARD_ALT);
        browserPanel.add(new JScrollPane(browserResultArea), BorderLayout.CENTER);
        toolsTabs.addTab("Browser Engine", browserPanel);

        // Tab C: Task History (full original feature)
        JPanel tasksPanel = new JPanel(new BorderLayout(10, 0));
        tasksPanel.setBackground(BG_APP);
        tasksPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        taskListModel = new DefaultListModel<>();
        taskList = new JList<>(taskListModel);
        taskList.setFont(FONT_BODY);
        taskList.setBackground(BG_CARD);
        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskList.addListSelectionListener(e -> showSelectedTaskDetails());

        taskDetailArea = new JTextArea();
        taskDetailArea.setFont(FONT_MONO);
        taskDetailArea.setEditable(false);
        taskDetailArea.setBackground(BG_CARD_ALT);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
            new JScrollPane(taskList),
            new JScrollPane(taskDetailArea)
        );
        split.setDividerLocation(300);
        tasksPanel.add(split, BorderLayout.CENTER);

        JPanel taskBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        taskBottom.setOpaque(false);
        JButton refreshTaskBtn = new JButton("Refresh Tasks");
        refreshTaskBtn.addActionListener(e -> refreshTasks());
        taskBottom.add(refreshTaskBtn);
        tasksPanel.add(taskBottom, BorderLayout.SOUTH);

        toolsTabs.addTab("Task History", tasksPanel);

        page.add(toolsTabs, BorderLayout.CENTER);
        return page;
    }

    private void doBrowserNavigate() {
        String url = browserUrlField.getText().trim();
        if (url.isEmpty()) return;
        browserResultArea.setText("Fetching content from: " + url + "...");
        SwingWorker<String, Void> worker = new SwingWorker<>() {
            @Override protected String doInBackground() {
                try {
                    return BrowserAutomation.navigate(url);
                } catch (Exception ex) {
                    return "Error: " + ex.getMessage();
                }
            }
            @Override protected void done() {
                try { browserResultArea.setText(get()); } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void doBrowserSearch() {
        String query = browserSearchField.getText().trim();
        String platform = (String) browserPlatformCombo.getSelectedItem();
        if (query.isEmpty()) return;
        try {
            String res = BrowserAutomation.search(query, platform != null ? platform.toLowerCase() : "google");
            browserResultArea.setText(res);
        } catch (Exception ex) {
            browserResultArea.setText("Search error: " + ex.getMessage());
        }
    }

    private void refreshTasks() {
        if (taskListModel == null) return;
        taskListModel.clear();
        List<TaskManager.TaskRecord> history = TaskManager.getHistory();
        for (TaskManager.TaskRecord r : history) {
            String status = r.isCancelled() ? "[CANCELLED]" : (r.getSteps().isEmpty() ? "[DONE]" : "[" + r.getSteps().get(r.getSteps().size() - 1).getStatus() + "]");
            taskListModel.addElement(status + " " + r.getOriginalQuery());
        }
    }

    private void showSelectedTaskDetails() {
        int idx = taskList.getSelectedIndex();
        List<TaskManager.TaskRecord> history = TaskManager.getHistory();
        if (idx >= 0 && idx < history.size()) {
            TaskManager.TaskRecord r = history.get(idx);
            StringBuilder sb = new StringBuilder();
            sb.append("TASK ID: ").append(r.getTaskId()).append("\n");
            sb.append("QUERY:   ").append(r.getOriginalQuery()).append("\n");
            sb.append("TIME:    ").append(new java.util.Date(r.getTimestamp())).append("\n");
            sb.append("STATUS:  ").append(r.isCancelled() ? "CANCELLED" : "ACTIVE / COMPLETED").append("\n\n");
            sb.append("STEPS (").append(r.getSteps().size()).append("):\n");
            for (TaskManager.TaskStep s : r.getSteps()) {
                sb.append("  • Step ").append(s.getStepNumber()).append(" [").append(s.getStatus()).append("]: ")
                  .append(s.getDescription()).append("\n")
                  .append("    Tool: ").append(s.getToolName()).append(" | Args: ").append(s.getArgument()).append("\n");
                if (s.getResult() != null && !s.getResult().isEmpty()) {
                    sb.append("    Result: ").append(s.getResult().replace("\n", "\n    ")).append("\n");
                }
            }
            taskDetailArea.setText(sb.toString());
        }
    }

    // ==================== 9. SETTINGS PAGE (PAGE 6) ====================

    private JPanel createSettingsPage() {
        JPanel page = new JPanel(new BorderLayout(0, 10));
        page.setBackground(BG_APP);
        page.setBorder(new EmptyBorder(14, 24, 14, 24));

        JLabel title = new JLabel("Settings & Diagnostics");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(TEXT_TITLE_MAROON);
        page.add(title, BorderLayout.NORTH);

        JTabbedPane settingsTabs = new JTabbedPane();
        settingsTabs.setFont(FONT_BOLD);

        // Tab A: AI Providers
        JPanel provPanel = new JPanel(new BorderLayout(0, 16));
        provPanel.setBackground(BG_APP);
        provPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel provForm = new JPanel(new GridLayout(6, 2, 10, 10));
        provForm.setOpaque(false);

        provForm.add(new JLabel("Active AI Provider:"));
        providerCombo = new JComboBox<>(AIProviderType.values());
        providerCombo.setSelectedItem(ProviderManager.getActiveProviderType());
        providerCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                AIProviderType sel = (AIProviderType) e.getItem();
                AIProvider p = ProviderManager.getProvider(sel);
                if (p != null) {
                    providerBaseUrl.setText(p.getBaseUrl());
                    providerModel.setText(p.getModel());
                    String masked = SecretStorage.getMaskedSecret(sel.name().toLowerCase() + "_key");
                    if ("NOT CONFIGURED".equals(masked) && !sel.getDefaultEnvVar().isBlank()) {
                        masked = SecretStorage.getMaskedSecret(sel.getDefaultEnvVar());
                    }
                    providerStatusLabel.setText(sel.getDisplayName() + " Key: " + masked);
                }
            }
        });
        provForm.add(providerCombo);

        provForm.add(new JLabel("Base Endpoint:"));
        providerBaseUrl = new JTextField(ProviderManager.getActiveProvider().getBaseUrl());
        provForm.add(providerBaseUrl);

        provForm.add(new JLabel("Model Name:"));
        providerModel = new JTextField(ProviderManager.getActiveProvider().getModel());
        provForm.add(providerModel);

        provForm.add(new JLabel("API Key:"));
        providerApiKey = new JPasswordField();
        provForm.add(providerApiKey);

        fallbackEnabled = new JCheckBox("Enable Fallback Provider", ProviderManager.isFallbackEnabled());
        fallbackEnabled.setOpaque(false);
        provForm.add(fallbackEnabled);

        fallbackCombo = new JComboBox<>(AIProviderType.values());
        if (ProviderManager.getFallbackProviderType() != null) {
            fallbackCombo.setSelectedItem(ProviderManager.getFallbackProviderType());
        }
        provForm.add(fallbackCombo);

        JButton saveProvBtn = new JButton("Save & Apply Provider");
        saveProvBtn.setFont(FONT_BOLD);
        saveProvBtn.setForeground(Color.WHITE);
        saveProvBtn.setBackground(PRIMARY_MAROON);
        saveProvBtn.addActionListener(e -> {
            AIProviderType pType = (AIProviderType) providerCombo.getSelectedItem();
            ProviderManager.setActiveProviderType(pType);
            String key = new String(providerApiKey.getPassword()).trim();
            ProviderManager.configureProvider(pType, providerBaseUrl.getText().trim(), providerModel.getText().trim(), key.isEmpty() ? null : key);
            ProviderManager.setFallbackEnabled(fallbackEnabled.isSelected());
            ProviderManager.setFallbackProviderType((AIProviderType) fallbackCombo.getSelectedItem());
            providerStatusLabel.setText("Saved configuration for " + pType.getDisplayName());
            JOptionPane.showMessageDialog(this, "AI Provider updated successfully!");
        });

        providerStatusLabel = new JLabel(" ");
        providerStatusLabel.setFont(FONT_SMALL);
        providerStatusLabel.setForeground(SUCCESS);

        JPanel provBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        provBottom.setOpaque(false);
        provBottom.add(providerStatusLabel);
        provBottom.add(saveProvBtn);

        provPanel.add(provForm, BorderLayout.NORTH);
        provPanel.add(provBottom, BorderLayout.SOUTH);
        settingsTabs.addTab("AI Providers", provPanel);

        // Tab B: User Profile
        JPanel profilePanel = new JPanel(new BorderLayout(0, 16));
        profilePanel.setBackground(BG_APP);
        profilePanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        JPanel profileCard = createTitledCard("Personal Profile & Identity");
        profileCard.setLayout(new BoxLayout(profileCard, BoxLayout.Y_AXIS));

        JLabel profileHint = new JLabel("Personalize your assistant greeting name or reset your profile:");
        profileHint.setFont(FONT_BODY);
        profileHint.setForeground(TEXT_SECONDARY);

        JTextField profileNameField = new JTextField(UserProfileManager.getUserName());
        profileNameField.setFont(FONT_BODY);
        profileNameField.setMaximumSize(new Dimension(400, 38));
        profileNameField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_SUBTLE, 1),
            new EmptyBorder(8, 12, 8, 12)
        ));

        JPanel profileBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        profileBtnRow.setOpaque(false);

        JButton saveProfileBtn = new JButton("Save Name");
        saveProfileBtn.setFont(FONT_BOLD);
        saveProfileBtn.setForeground(Color.WHITE);
        saveProfileBtn.setBackground(PRIMARY_MAROON);
        saveProfileBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        saveProfileBtn.addActionListener(e -> {
            String updated = profileNameField.getText().trim();
            UserProfileManager.setUserName(updated);
            updateGreetingLabels();
            JOptionPane.showMessageDialog(this, "Profile name updated to: " + (updated.isEmpty() ? "(None)" : updated));
        });

        JButton resetProfileBtn = new JButton("Reset Profile (First-Time Setup)");
        resetProfileBtn.setFont(FONT_BODY);
        resetProfileBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        resetProfileBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "Reset your user profile? You will see the first-time welcome screen on next launch.",
                "Reset Profile", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                UserProfileManager.resetProfile();
                profileNameField.setText("");
                updateGreetingLabels();
                JOptionPane.showMessageDialog(this, "Profile reset successfully.");
            }
        });

        profileBtnRow.add(saveProfileBtn);
        profileBtnRow.add(resetProfileBtn);

        profileCard.add(profileHint);
        profileCard.add(Box.createVerticalStrut(12));
        profileCard.add(new JLabel("Your Name:"));
        profileCard.add(Box.createVerticalStrut(4));
        profileCard.add(profileNameField);
        profileCard.add(Box.createVerticalStrut(16));
        profileCard.add(profileBtnRow);

        profilePanel.add(profileCard, BorderLayout.NORTH);
        settingsTabs.addTab("Profile", profilePanel);

        // Tab B: Mobile Companion Pairing
        settingsTabs.addTab("Mobile Companion", createMobileCompanionPanel());

        // Tab C: System Diagnostics
        JPanel diagPanel = new JPanel(new BorderLayout(0, 10));
        diagPanel.setBackground(BG_APP);
        diagPanel.setBorder(new EmptyBorder(16, 16, 16, 16));

        diagArea = new JTextArea();
        diagArea.setFont(FONT_MONO);
        diagArea.setEditable(false);
        diagArea.setBackground(BG_CARD_ALT);
        diagArea.setBorder(new EmptyBorder(14, 14, 14, 14));
        refreshDiagnostics();

        JPanel diagBottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        diagBottom.setOpaque(false);
        JButton refreshDiagBtn = new JButton("Refresh Diagnostics");
        refreshDiagBtn.addActionListener(e -> refreshDiagnostics());
        JButton copyDiagBtn = new JButton("Copy Report");
        copyDiagBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(diagArea.getText()), null);
            JOptionPane.showMessageDialog(this, "Diagnostics copied to clipboard.");
        });

        diagBottom.add(copyDiagBtn);
        diagBottom.add(refreshDiagBtn);

        diagPanel.add(new JScrollPane(diagArea), BorderLayout.CENTER);
        diagPanel.add(diagBottom, BorderLayout.SOUTH);
        settingsTabs.addTab("Diagnostics", diagPanel);

        page.add(settingsTabs, BorderLayout.CENTER);
        return page;
    }

    private JPanel createMobileCompanionPanel() {
        JPanel mobilePanel = new JPanel(new BorderLayout(0, 10));
        mobilePanel.setBackground(BG_APP);
        mobilePanel.setBorder(new EmptyBorder(8, 12, 8, 12));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(BG_APP);

        // 1. Slim Top Status Card
        JPanel topBanner = new JPanel(new BorderLayout(12, 0)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
            }
        };
        topBanner.setOpaque(false);
        topBanner.setBorder(new EmptyBorder(8, 16, 8, 16));
        topBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        topBanner.setPreferredSize(new Dimension(800, 42));

        JLabel statusLbl = new JLabel("● DEX Web Server is ONLINE on port " + DEXConfig.getWebPort());
        statusLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLbl.setForeground(SUCCESS);

        JButton openWebBtn = new JButton("🌐 Open Web Dashboard");
        openWebBtn.setFont(FONT_SMALL);
        openWebBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        openWebBtn.addActionListener(e -> SystemTools.openUrl("http://127.0.0.1:" + DEXConfig.getWebPort()));

        topBanner.add(statusLbl, BorderLayout.WEST);
        topBanner.add(openWebBtn, BorderLayout.EAST);

        content.add(topBanner);
        content.add(Box.createVerticalStrut(10));

        // 2. Main Two Column Grid (1 row, 2 columns)
        JPanel grid = new JPanel(new GridLayout(1, 2, 12, 0));
        grid.setOpaque(false);

        String lanIp = DEXConfig.getLanIp();
        int port = DEXConfig.getWebPort();

        // ========== LEFT CARD: Pairing PIN & Network ==========
        JPanel leftCard = createTitledCard("Device Pairing & Connection");
        leftCard.setLayout(new BoxLayout(leftCard, BoxLayout.Y_AXIS));

        JLabel pinHint = new JLabel("Enter this 6-digit PIN on your phone to authorize:");
        pinHint.setFont(FONT_LABEL);
        pinHint.setForeground(TEXT_SECONDARY);
        pinHint.setAlignmentX(Component.CENTER_ALIGNMENT);

        // PIN display + Expiry timer in clean centered row
        JPanel pinBlock = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        pinBlock.setOpaque(false);

        mobilePinLabel = new JLabel(DeviceManager.getActivePin());
        mobilePinLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        mobilePinLabel.setForeground(PRIMARY_MAROON);

        mobilePinExpiryLabel = new JLabel("Expires: " + formatRemainingTime(DeviceManager.getPinRemainingSeconds()));
        mobilePinExpiryLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        mobilePinExpiryLabel.setForeground(TEXT_MUTED);

        pinBlock.add(mobilePinLabel);
        pinBlock.add(mobilePinExpiryLabel);

        // PIN Action Buttons
        JPanel pinBtnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        pinBtnRow.setOpaque(false);

        JButton copyPinBtn = new JButton("📋 Copy PIN");
        copyPinBtn.setFont(FONT_SMALL);
        copyPinBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copyPinBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(mobilePinLabel.getText()), null);
            JOptionPane.showMessageDialog(this, "PIN copied: " + mobilePinLabel.getText());
        });

        JButton genPinBtn = new JButton("🔄 Generate New PIN");
        genPinBtn.setFont(FONT_SMALL);
        genPinBtn.setBackground(PRIMARY_MAROON);
        genPinBtn.setForeground(Color.WHITE);
        genPinBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        genPinBtn.addActionListener(e -> {
            String newPin = DeviceManager.generatePairingPin();
            mobilePinLabel.setText(newPin);
            mobilePinExpiryLabel.setText("Expires: " + formatRemainingTime(DeviceManager.getPinRemainingSeconds()));
        });

        pinBtnRow.add(copyPinBtn);
        pinBtnRow.add(genPinBtn);

        // Horizontal Divider
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_SUBTLE);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        // Network Section
        JLabel netSectionTitle = new JLabel("DEX PC Network Details (Enter in Mobile App):");
        netSectionTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        netSectionTitle.setForeground(TEXT_PRIMARY);

        JPanel netGrid = new JPanel(new GridLayout(1, 2, 8, 0));
        netGrid.setOpaque(false);

        // IP Box
        JPanel ipBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        ipBox.setOpaque(false);
        JLabel ipTag = new JLabel("IP: ");
        ipTag.setFont(FONT_SMALL);
        ipTag.setForeground(TEXT_SECONDARY);
        mobileHostIpLabel = new JLabel(lanIp);
        mobileHostIpLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        mobileHostIpLabel.setForeground(PRIMARY_MAROON);
        JButton copyIpBtn = new JButton("📋");
        copyIpBtn.setFont(FONT_SMALL);
        copyIpBtn.setToolTipText("Copy IP");
        copyIpBtn.setMargin(new Insets(2, 6, 2, 6));
        copyIpBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copyIpBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(mobileHostIpLabel.getText()), null);
            JOptionPane.showMessageDialog(this, "Host IP copied: " + mobileHostIpLabel.getText());
        });
        ipBox.add(ipTag);
        ipBox.add(mobileHostIpLabel);
        ipBox.add(copyIpBtn);

        // Port Box
        JPanel portBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        portBox.setOpaque(false);
        JLabel portTag = new JLabel("Port: ");
        portTag.setFont(FONT_SMALL);
        portTag.setForeground(TEXT_SECONDARY);
        JLabel portValLbl = new JLabel(String.valueOf(port));
        portValLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        portValLbl.setForeground(PRIMARY_MAROON);
        JButton copyPortBtn = new JButton("📋");
        copyPortBtn.setFont(FONT_SMALL);
        copyPortBtn.setToolTipText("Copy Port");
        copyPortBtn.setMargin(new Insets(2, 6, 2, 6));
        copyPortBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        copyPortBtn.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(String.valueOf(port)), null);
            JOptionPane.showMessageDialog(this, "Port copied: " + port);
        });
        portBox.add(portTag);
        portBox.add(portValLbl);
        portBox.add(copyPortBtn);

        netGrid.add(ipBox);
        netGrid.add(portBox);

        // Network Interface Selector
        JPanel nicRow = new JPanel(new BorderLayout(6, 0));
        nicRow.setOpaque(false);
        JLabel nicLbl = new JLabel("Network Interface:");
        nicLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        nicLbl.setForeground(TEXT_MUTED);

        JComboBox<String> ipsCombo = new JComboBox<>(DEXConfig.getDetectedHostIps().toArray(new String[0]));
        ipsCombo.setFont(FONT_SMALL);
        ipsCombo.addActionListener(e -> {
            String selected = (String) ipsCombo.getSelectedItem();
            if (selected != null) {
                String clean = selected.replaceAll("\\s*\\(.*\\)", "").trim();
                mobileHostIpLabel.setText(clean);
            }
        });
        nicRow.add(nicLbl, BorderLayout.WEST);
        nicRow.add(ipsCombo, BorderLayout.CENTER);

        leftCard.add(pinHint);
        leftCard.add(Box.createVerticalStrut(4));
        leftCard.add(pinBlock);
        leftCard.add(Box.createVerticalStrut(4));
        leftCard.add(pinBtnRow);
        leftCard.add(Box.createVerticalStrut(8));
        leftCard.add(sep);
        leftCard.add(Box.createVerticalStrut(8));
        leftCard.add(netSectionTitle);
        leftCard.add(Box.createVerticalStrut(4));
        leftCard.add(netGrid);
        leftCard.add(Box.createVerticalStrut(6));
        leftCard.add(nicRow);

        grid.add(leftCard);

        // ========== RIGHT CARD: Instructions & Authorized Devices ==========
        JPanel rightCard = createTitledCard("Setup Guide & Devices");
        rightCard.setLayout(new BoxLayout(rightCard, BoxLayout.Y_AXIS));

        // Quick 3-Step Guide (compact)
        String guideHtml = "<html><body style='line-height:1.45;color:#2D2120;font-size:11px;'>"
            + "<b>1. Wi-Fi:</b> Connect your phone to the same Wi-Fi as this PC.<br>"
            + "<b>2. Settings:</b> In DEX-Mobile, enter Host IP & Port.<br>"
            + "<b>3. Authorize:</b> Enter the 6-digit PIN & tap <b>\"Pair Device\"</b> &rarr; Status becomes green!"
            + "</body></html>";
        JLabel guideLbl = new JLabel(guideHtml);
        guideLbl.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        guideLbl.setAlignmentX(Component.LEFT_ALIGNMENT);

        JSeparator rightSep = new JSeparator();
        rightSep.setForeground(BORDER_SUBTLE);
        rightSep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));

        // Authorized Devices section
        JPanel devicesHeaderRow = new JPanel(new BorderLayout(6, 0));
        devicesHeaderRow.setOpaque(false);
        devicesHeaderRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel devTitle = new JLabel("Authorized Companion Devices");
        devTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        devTitle.setForeground(TEXT_PRIMARY);

        JPanel devBtnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        devBtnRow.setOpaque(false);

        JButton refreshDevBtn = new JButton("🔄 Refresh");
        refreshDevBtn.setFont(FONT_SMALL);
        refreshDevBtn.setMargin(new Insets(2, 6, 2, 6));
        refreshDevBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        refreshDevBtn.addActionListener(e -> refreshPairedDevicesList());

        JButton revokeBtn = new JButton("Revoke Selected");
        revokeBtn.setFont(FONT_SMALL);
        revokeBtn.setForeground(ERROR);
        revokeBtn.setMargin(new Insets(2, 6, 2, 6));
        revokeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        revokeBtn.addActionListener(e -> {
            int idx = pairedDevicesList.getSelectedIndex();
            java.util.List<DeviceManager.PairedDevice> devs = DeviceManager.getPairedDevices();
            if (idx >= 0 && idx < devs.size()) {
                DeviceManager.revokeDevice(devs.get(idx).getDeviceId());
                refreshPairedDevicesList();
                JOptionPane.showMessageDialog(this, "Device unlinked.");
            }
        });

        devBtnRow.add(refreshDevBtn);
        devBtnRow.add(revokeBtn);

        devicesHeaderRow.add(devTitle, BorderLayout.WEST);
        devicesHeaderRow.add(devBtnRow, BorderLayout.EAST);

        pairedDevicesModel = new DefaultListModel<>();
        pairedDevicesList = new JList<>(pairedDevicesModel);
        pairedDevicesList.setFont(FONT_SMALL);
        pairedDevicesList.setBackground(BG_CARD_ALT);
        pairedDevicesList.setVisibleRowCount(3);
        refreshPairedDevicesList();

        JScrollPane devScroll = new JScrollPane(pairedDevicesList);
        devScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        devScroll.setPreferredSize(new Dimension(300, 72));
        devScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        devScroll.setBorder(BorderFactory.createLineBorder(BORDER_SUBTLE, 1));

        rightCard.add(guideLbl);
        rightCard.add(Box.createVerticalStrut(8));
        rightCard.add(rightSep);
        rightCard.add(Box.createVerticalStrut(8));
        rightCard.add(devicesHeaderRow);
        rightCard.add(Box.createVerticalStrut(4));
        rightCard.add(devScroll);

        grid.add(rightCard);

        content.add(grid);

        JScrollPane scrollPane = new JScrollPane(content);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);

        mobilePanel.add(scrollPane, BorderLayout.CENTER);

        // 1-second auto-refresh timer for PIN countdown
        mobilePinTimer = new Timer(1000, e -> {
            long remaining = DeviceManager.getPinRemainingSeconds();
            if (remaining <= 0) {
                String newPin = DeviceManager.getActivePin();
                if (mobilePinLabel != null) mobilePinLabel.setText(newPin);
            }
            if (mobilePinExpiryLabel != null) {
                mobilePinExpiryLabel.setText("Expires: " + formatRemainingTime(DeviceManager.getPinRemainingSeconds()));
            }
        });
        mobilePinTimer.start();

        return mobilePanel;
    }

    private String formatRemainingTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%dm %02ds", m, s);
    }

    private void refreshPairedDevicesList() {
        if (pairedDevicesModel == null) return;
        pairedDevicesModel.clear();
        java.util.List<DeviceManager.PairedDevice> devices = DeviceManager.getPairedDevices();
        if (devices.isEmpty()) {
            pairedDevicesModel.addElement("No paired devices. Enter the PIN on your phone to pair.");
        } else {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm");
            for (DeviceManager.PairedDevice d : devices) {
                pairedDevicesModel.addElement("📱 " + d.getDeviceName() + " (ID: " + d.getDeviceId() + " • Paired: " + sdf.format(new Date(d.getPairedAt())) + ")");
            }
        }
    }

    private void refreshDiagnostics() {
        if (diagArea != null) {
            diagArea.setText(SystemTools.getSystemInfo(""));
        }
    }

    private JPanel createTitledCard(String title) {
        JPanel card = new JPanel(new BorderLayout(0, 12)) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_CARD);
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));
                g2.setColor(BORDER_SUBTLE);
                g2.setStroke(new BasicStroke(1f));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth() - 1, getHeight() - 1, 8, 8));
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setBackground(BG_CARD);
        card.setBorder(new EmptyBorder(12, 16, 12, 16));

        JLabel titleLbl = new JLabel(title);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setForeground(TEXT_TITLE_MAROON);
        card.add(titleLbl, BorderLayout.NORTH);
        return card;
    }

    private void executeQuickAction(String action) {
        new Thread(() -> {
            try {
                if ("chrome".equalsIgnoreCase(action)) WindowsController.openApp("chrome");
                else if ("vscode".equalsIgnoreCase(action)) WindowsController.openApp("vscode");
                else if ("calculator".equalsIgnoreCase(action)) WindowsController.openApp("calculator");
                else if ("notepad".equalsIgnoreCase(action)) WindowsController.openApp("notepad");
                else if ("youtube".equalsIgnoreCase(action)) WindowsController.openApp("youtube");
                else if ("file explorer".equalsIgnoreCase(action)) WindowsController.openApp("file explorer");
                else if ("settings".equalsIgnoreCase(action)) WindowsController.openApp("settings");
                else if ("cmd".equalsIgnoreCase(action)) WindowsController.openApp("cmd");
                else if (action.startsWith("http")) SystemTools.openUrl(action);
            } catch (Exception ex) {
                System.err.println("[DEX UI] Quick action error: " + ex.getMessage());
            }
        }).start();
    }

    private void handleExit() {
        int c = JOptionPane.showConfirmDialog(this, "Exit AI Assistant (DEX)?", "Exit", JOptionPane.YES_NO_OPTION);
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

    // ==================== VECTOR ICONS SYSTEM ====================

    static class DrawIcons {

        public static void drawRosetteLogo(Graphics2D g, int x, int y, int w, int h, Color color) {
            g.setColor(color);
            int cx = x + w / 2;
            int cy = y + h / 2;
            int r1 = w / 2 - 1;
            int r2 = r1 - 4;

            Polygon p = new Polygon();
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI / 8;
                int r = (i % 2 == 0) ? r1 : r2;
                p.addPoint((int)(cx + r * Math.cos(angle)), (int)(cy + r * Math.sin(angle)));
            }
            g.fillPolygon(p);
            g.setColor(BG_APP);
            g.fillOval(cx - 5, cy - 5, 10, 10);
            g.setColor(color);
            g.fillOval(cx - 2, cy - 2, 4, 4);
        }

        public static void drawProfile(Graphics2D g, int x, int y, int w, int h, Color color) {
            g.setColor(color);
            int cx = x + w / 2;
            g.fillOval(cx - 4, y + 2, 8, 8);
            g.fillArc(x + 2, y + 10, w - 4, h - 8, 0, 180);
        }

        public static void drawSearch(Graphics2D g, int x, int y, int w, int h, Color color) {
            g.setColor(color);
            g.setStroke(new BasicStroke(1.8f));
            g.drawOval(x + 2, y + 2, 10, 10);
            g.drawLine(x + 10, y + 10, x + 16, y + 16);
        }

        public static void drawSendAirplane(Graphics2D g, int x, int y, int w, int h, Color color) {
            g.setColor(color);
            Polygon p = new Polygon();
            p.addPoint(x + 2, y + 2);
            p.addPoint(x + w - 2, y + h / 2);
            p.addPoint(x + 2, y + h - 2);
            p.addPoint(x + 6, y + h / 2);
            g.fillPolygon(p);
        }

        public static void drawRightArrow(Graphics2D g, int x, int y, int w, int h, Color color) {
            g.setColor(color);
            g.setStroke(new BasicStroke(1.5f));
            g.drawLine(x + 2, y + h/2, x + w - 3, y + h/2);
            g.drawLine(x + w - 6, y + 2, x + w - 3, y + h/2);
            g.drawLine(x + w - 6, y + h - 2, x + w - 3, y + h/2);
        }

        public static void drawWifi(Graphics2D g, int x, int y, int w, int h, Color color) {
            g.setColor(color);
            g.setStroke(new BasicStroke(1.5f));
            g.drawArc(x + 1, y + 3, 14, 14, 45, 90);
            g.drawArc(x + 4, y + 6, 8, 8, 45, 90);
            g.fillOval(x + 7, y + 11, 2, 2);
        }

        public static void drawSun(Graphics2D g, int x, int y, int w, int h, Color color) {
            g.setColor(color);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(x + 4, y + 4, 8, 8);
            g.drawLine(x + 8, y + 1, x + 8, y + 3);
            g.drawLine(x + 8, y + 13, x + 8, y + 15);
            g.drawLine(x + 1, y + 8, x + 3, y + 8);
            g.drawLine(x + 13, y + 8, x + 15, y + 8);
        }

        public static void drawNavIcon(Graphics2D g, String type, int x, int y, int w, int h, Color color) {
            g.setColor(color);
            g.setStroke(new BasicStroke(1.6f));
            switch (type) {
                case "home":
                    Polygon p = new Polygon();
                    p.addPoint(x + w / 2, y + 2);
                    p.addPoint(x + 2, y + 8);
                    p.addPoint(x + w - 2, y + 8);
                    g.fillPolygon(p);
                    g.fillRect(x + 4, y + 8, w - 8, h - 9);
                    break;
                case "chat":
                    g.drawRoundRect(x + 2, y + 3, w - 4, h - 7, 6, 6);
                    g.drawLine(x + 5, y + h - 4, x + 9, y + h - 1);
                    break;
                case "voice":
                    g.drawRoundRect(x + 6, y + 2, w - 12, h - 9, 6, 6);
                    g.drawArc(x + 3, y + 5, w - 6, 9, 0, -180);
                    g.drawLine(x + w / 2, y + 14, x + w / 2, y + h - 2);
                    break;
                case "image":
                    g.drawRoundRect(x + 2, y + 3, w - 4, h - 6, 4, 4);
                    g.drawOval(x + 5, y + 6, 3, 3);
                    g.drawLine(x + 4, y + 13, x + 9, y + 8);
                    g.drawLine(x + 9, y + 8, x + 14, y + 13);
                    break;
                case "files":
                    g.drawRoundRect(x + 2, y + 5, w - 4, h - 7, 4, 4);
                    g.drawLine(x + 4, y + 5, x + 8, y + 2);
                    g.drawLine(x + 8, y + 2, x + 12, y + 5);
                    break;
                case "tools":
                    g.drawRect(x + 3, y + 3, 5, 5);
                    g.drawRect(x + 11, y + 3, 5, 5);
                    g.drawRect(x + 3, y + 11, 5, 5);
                    g.drawRect(x + 11, y + 11, 5, 5);
                    break;
                case "settings":
                    g.drawOval(x + 5, y + 5, 9, 9);
                    g.drawLine(x + 9, y + 1, x + 9, y + 4);
                    g.drawLine(x + 9, y + 15, x + 9, y + 18);
                    g.drawLine(x + 1, y + 9, x + 4, y + 9);
                    g.drawLine(x + 15, y + 9, x + 18, y + 9);
                    break;
            }
        }

        public static void drawActionIcon(Graphics2D g, String type, int x, int y, int w, int h, Color color) {
            drawNavIcon(g, type.equals("mic") ? "voice" : (type.equals("wrench") ? "tools" : type), x, y, w, h, color);
        }

        public static void drawRecentIcon(Graphics2D g, String type, int x, int y, int w, int h, Color color) {
            g.setColor(color);
            g.setStroke(new BasicStroke(1.4f));
            switch (type) {
                case "school":
                    Polygon cap = new Polygon();
                    cap.addPoint(x + w / 2, y + 3);
                    cap.addPoint(x + 2, y + 7);
                    cap.addPoint(x + w / 2, y + 11);
                    cap.addPoint(x + w - 2, y + 7);
                    g.fillPolygon(cap);
                    g.drawLine(x + 4, y + 8, x + 4, y + 13);
                    break;
                case "image":
                    drawNavIcon(g, "image", x, y, w, h, color);
                    break;
                case "play":
                    Polygon play = new Polygon();
                    play.addPoint(x + 5, y + 4);
                    play.addPoint(x + w - 5, y + h / 2);
                    play.addPoint(x + 5, y + h - 4);
                    g.fillPolygon(play);
                    break;
                case "code":
                    g.drawString("</>", x + 1, y + 12);
                    break;
                case "gamepad":
                    g.drawRoundRect(x + 2, y + 4, w - 4, h - 8, 4, 4);
                    g.drawLine(x + 5, y + 8, x + 7, y + 8);
                    g.fillOval(x + 11, y + 7, 2, 2);
                    break;
                case "camera":
                    g.drawRoundRect(x + 2, y + 3, w - 4, h - 6, 4, 4);
                    g.drawOval(x + 6, y + 6, 4, 4);
                    break;
                case "tag":
                    Polygon tag = new Polygon();
                    tag.addPoint(x + 4, y + 2);
                    tag.addPoint(x + w - 2, y + h / 2);
                    tag.addPoint(x + w / 2, y + h - 2);
                    tag.addPoint(x + 2, y + 8);
                    g.drawPolygon(tag);
                    break;
                default:
                    g.fillOval(x + 5, y + 5, 6, 6);
                    break;
            }
        }
    }
}

package com.dex.mobile;

import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main UI coordinator for DEX Mobile Companion.
 * Delegates REST/Cloud communication to {@link DexClient}, preferences to {@link PrefsStore},
 * voice I/O to {@link VoiceController}, and chat rendering to {@link ChatBubbleRenderer}.
 */
public class MainActivity extends AppCompatActivity {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DexClient dexClient = new DexClient();

    private PrefsStore prefs;
    private VoiceController voiceController;
    private ChatBubbleRenderer chatRenderer;
    private boolean isFullscreen = false;

    // Pages & Navigation
    private View[] pages;
    private ImageView[] navIcons;
    private TextView[] navLabels;

    // Badges & Common Header Controls
    private TextView tvHomeStatusBadge, tvStatusBadge, tvEndpoint;
    private ImageView btnHomeFullscreen, btnChatFullscreen;

    // Home Views
    private TextView tvHomeUserName;
    private EditText etHomeSearch, etHomeMessage;
    private TextView[] topicTags;

    // Explore Views
    private TextView tvExploreUserName;
    private EditText etExploreMessage;

    // Chat Views
    private EditText etMessage;
    private ImageView btnChatMic, btnChatTtsToggle;
    private ScrollView chatScrollView;
    private LinearLayout chatMessagesContainer;

    // Settings, Remote & Vision Views
    private EditText etSettingsUserName, etHostIp, etPort, etPairingPin, etVisionPrompt;
    private TextView tvPairingStatus, tvWindowsActionStatus, tvVisionResult;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new PrefsStore(this);
        voiceController = new VoiceController(this, this::handleVoiceText);

        bindViews();
        chatRenderer = new ChatBubbleRenderer(this, chatMessagesContainer, chatScrollView,
                voiceController::toggleBubbleSpeech);

        loadSavedConfig();
        setupNavigation();
        setupHomeInteractions();
        setupExploreInteractions();
        setupChatInteractions();
        setupSettingsAndRemote();

        applyFullscreen(prefs.isFullscreen(), false);
        testConnection(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClientTelemetry();
        if (dexClient.isReady()) {
            executor.execute(() -> {
                try {
                    dexClient.sendHeartbeat();
                } catch (Exception ignored) {
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        voiceController.stopListening();
        voiceController.stopTts();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        voiceController.destroy();
        executor.shutdownNow();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        voiceController.onPermissionResult(requestCode, grantResults);
    }

    private void bindViews() {
        pages = new View[]{
                findViewById(R.id.pageHome),
                findViewById(R.id.pageChat),
                findViewById(R.id.pageExplore),
                findViewById(R.id.pageSettings)
        };
        navIcons = new ImageView[]{
                findViewById(R.id.ivNavHome),
                findViewById(R.id.ivNavChat),
                findViewById(R.id.ivNavExplore),
                findViewById(R.id.ivNavSettings)
        };
        navLabels = new TextView[]{
                findViewById(R.id.tvNavHome),
                findViewById(R.id.tvNavChat),
                findViewById(R.id.tvNavExplore),
                findViewById(R.id.tvNavSettings)
        };

        tvHomeStatusBadge = findViewById(R.id.tvHomeStatusBadge);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvEndpoint = findViewById(R.id.tvEndpoint);
        btnHomeFullscreen = findViewById(R.id.btnHomeFullscreen);
        btnChatFullscreen = findViewById(R.id.btnChatFullscreen);

        tvHomeUserName = findViewById(R.id.tvHomeUserName);
        etHomeSearch = findViewById(R.id.etHomeSearch);
        etHomeMessage = findViewById(R.id.etHomeMessage);
        topicTags = new TextView[]{
                findViewById(R.id.tagDailyLife),
                findViewById(R.id.tagStudy),
                findViewById(R.id.tagBusiness),
                findViewById(R.id.tagHealth),
                findViewById(R.id.tagDevelopment)
        };

        tvExploreUserName = findViewById(R.id.tvExploreUserName);
        etExploreMessage = findViewById(R.id.etExploreMessage);

        etMessage = findViewById(R.id.etMessage);
        btnChatMic = findViewById(R.id.btnChatMic);
        btnChatTtsToggle = findViewById(R.id.btnChatTtsToggle);
        chatScrollView = findViewById(R.id.chatScrollView);
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer);

        voiceController.bindUi(
                findViewById(R.id.layoutVoiceActive),
                findViewById(R.id.ivVoicePulse),
                findViewById(R.id.tvVoiceStatus),
                findViewById(R.id.btnCancelVoice),
                btnChatMic);

        etSettingsUserName = findViewById(R.id.etSettingsUserName);
        etHostIp = findViewById(R.id.etHostIp);
        etPort = findViewById(R.id.etPort);
        etPairingPin = findViewById(R.id.etPairingPin);
        tvPairingStatus = findViewById(R.id.tvPairingStatus);
        tvWindowsActionStatus = findViewById(R.id.tvWindowsActionStatus);
        etVisionPrompt = findViewById(R.id.etVisionPrompt);
        tvVisionResult = findViewById(R.id.tvVisionResult);
    }

    // ==================== CONFIG & TELEMETRY ====================

    private void loadSavedConfig() {
        String userName = prefs.getUserName(getString(R.string.user_name));
        updateUserNameDisplay(userName);

        boolean ttsOn = prefs.isTtsEnabled();
        voiceController.setTtsEnabled(ttsOn);
        updateTtsIcon();

        String host = prefs.getHost();
        int port = prefs.getPort();
        if (etHostIp != null) etHostIp.setText(host);
        if (etPort != null) etPort.setText(String.valueOf(port));

        dexClient.setServerUrl(DexClient.buildServerUrl(host, port));
        dexClient.setAuthToken(prefs.getToken());
        dexClient.setDeviceId(prefs.getOrCreateDeviceId());
        updateClientTelemetry();
        updateEndpointDisplay();
        checkFirstTimeNameSetup();
    }

    private void updateClientTelemetry() {
        DexClient.DeviceTelemetry telemetry = new DexClient.DeviceTelemetry();
        telemetry.deviceId = prefs.getOrCreateDeviceId();
        telemetry.deviceName = "Android " + Build.MODEL;
        telemetry.osBrowser = "Android " + Build.VERSION.RELEASE + " (DEX App)";
        DisplayMetrics dm = getResources().getDisplayMetrics();
        telemetry.screen = dm.widthPixels + "x" + dm.heightPixels;
        telemetry.orientation = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
                ? "landscape" : "portrait";
        telemetry.fullscreen = isFullscreen;
        dexClient.setTelemetry(telemetry);
    }

    private void saveConfig(String hostOrUrl, int port, String token, String deviceId) {
        prefs.saveConnection(hostOrUrl, port, token, deviceId);
        dexClient.setServerUrl(DexClient.buildServerUrl(hostOrUrl, port));
        dexClient.setAuthToken(token);
        if (deviceId != null) dexClient.setDeviceId(deviceId);
        updateClientTelemetry();
        updateEndpointDisplay();
    }

    private void updateEndpointDisplay() {
        if (tvEndpoint == null) return;
        String suffix = dexClient.isCloudRelay()
                ? " (Cloud Bridge)"
                : (dexClient.isPaired() ? " (Paired)" : " (Unpaired)");
        tvEndpoint.setText(dexClient.getServerUrl() + suffix);
    }

    private void checkFirstTimeNameSetup() {
        if (prefs.isFirstLaunchDone()) return;

        final EditText input = new EditText(this);
        input.setHint("Your name");
        input.setText(prefs.getUserName(""));
        input.setSingleLine(true);

        int pad = dpToPx(20);
        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = pad;
        lp.rightMargin = pad;
        input.setLayoutParams(lp);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("What is your name?")
                .setView(container)
                .setCancelable(false)
                .setPositiveButton("Get Started", (d, w) -> {
                    String entered = input.getText().toString().trim();
                    if (!entered.isEmpty()) saveUserName(entered);
                    prefs.markFirstLaunchDone();
                })
                .setNegativeButton("Skip", (d, w) -> prefs.markFirstLaunchDone())
                .show();
    }

    private void updateUserNameDisplay(String name) {
        if (name == null || name.trim().isEmpty()) return;
        String trimmed = name.trim();
        if (tvHomeUserName != null) tvHomeUserName.setText(trimmed);
        if (tvExploreUserName != null) tvExploreUserName.setText(trimmed);
        if (etSettingsUserName != null) etSettingsUserName.setText(trimmed);
    }

    private void saveUserName(String newName) {
        if (newName == null || newName.trim().isEmpty()) return;
        String trimmed = newName.trim();
        prefs.setUserName(trimmed);
        updateUserNameDisplay(trimmed);
        Toast.makeText(this, "Profile name saved: " + trimmed, Toast.LENGTH_SHORT).show();
        executor.execute(() -> {
            try {
                dexClient.updateProfile(trimmed);
            } catch (Exception ignored) {
            }
        });
    }

    // ==================== NAVIGATION & FULLSCREEN ====================

    private void setupNavigation() {
        int[] navIds = {R.id.navHome, R.id.navChat, R.id.navExplore, R.id.navSettings};
        for (int i = 0; i < navIds.length; i++) {
            final int index = i;
            View nav = findViewById(navIds[i]);
            if (nav != null) nav.setOnClickListener(v -> switchTab(index));
        }

        findViewById(R.id.btnExploreBack).setOnClickListener(v -> switchTab(0));
        findViewById(R.id.btnChatBack).setOnClickListener(v -> switchTab(0));
        findViewById(R.id.btnExploreMenu).setOnClickListener(v -> switchTab(3));
        findViewById(R.id.btnChatMenu).setOnClickListener(v -> switchTab(3));

        View profileAvatar = findViewById(R.id.btnProfileAvatar);
        if (profileAvatar != null) profileAvatar.setOnClickListener(v -> switchTab(3));
        View seeAllTopics = findViewById(R.id.btnHomeSeeAllTopics);
        if (seeAllTopics != null) seeAllTopics.setOnClickListener(v -> switchTab(2));

        if (btnHomeFullscreen != null) {
            btnHomeFullscreen.setOnClickListener(v -> applyFullscreen(!isFullscreen, true));
        }
        if (btnChatFullscreen != null) {
            btnChatFullscreen.setOnClickListener(v -> applyFullscreen(!isFullscreen, true));
        }
    }

    public void switchTab(int index) {
        for (int i = 0; i < pages.length; i++) {
            if (pages[i] != null) pages[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
        }
        int activeColor = ContextCompat.getColor(this, R.color.nav_active);
        int inactiveColor = ContextCompat.getColor(this, R.color.nav_inactive);
        for (int i = 0; i < navIcons.length; i++) {
            int color = (i == index) ? activeColor : inactiveColor;
            if (navIcons[i] != null) navIcons[i].setImageTintList(ColorStateList.valueOf(color));
            if (navLabels[i] != null) navLabels[i].setTextColor(color);
        }
    }

    private void applyFullscreen(boolean enable, boolean notifyUser) {
        isFullscreen = enable;
        prefs.setFullscreen(enable);
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (controller != null) {
            controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            if (enable) {
                controller.hide(WindowInsetsCompat.Type.systemBars());
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars());
            }
        }
        int tint = ContextCompat.getColor(this, enable ? R.color.status_online : R.color.primary_maroon);
        if (btnHomeFullscreen != null) btnHomeFullscreen.setImageTintList(ColorStateList.valueOf(tint));
        if (btnChatFullscreen != null) btnChatFullscreen.setImageTintList(ColorStateList.valueOf(tint));
        updateClientTelemetry();
        if (notifyUser) {
            Toast.makeText(this, enable ? "Fullscreen mode active" : "Standard view restored", Toast.LENGTH_SHORT).show();
        }
    }

    // ==================== HOME & EXPLORE ====================

    private void setupHomeInteractions() {
        findViewById(R.id.cardHomeChat).setOnClickListener(v -> switchTab(1));
        findViewById(R.id.cardHomeVoice).setOnClickListener(v -> startVoiceFromAnyTab());
        findViewById(R.id.cardHomeImage).setOnClickListener(v -> submitPromptFromTab("Capture desktop screen and analyze active window"));
        findViewById(R.id.cardHomeFiles).setOnClickListener(v -> submitPromptFromTab("Analyze workspace documents and show summary"));

        String[] topicNames = {"Daily Life", "Study", "Business", "Health", "Development"};
        for (int i = 0; i < topicTags.length; i++) {
            final TextView tagView = topicTags[i];
            final String topic = topicNames[i];
            if (tagView != null) {
                tagView.setOnClickListener(v -> {
                    resetTopicTags();
                    tagView.setBackgroundResource(R.drawable.bg_tag_active);
                    tagView.setTextColor(Color.WHITE);
                    submitPromptFromTab("Explore insights on " + topic);
                });
            }
        }

        Runnable homeSendAction = () -> sendFromEditText(etHomeMessage);
        findViewById(R.id.btnHomeSend).setOnClickListener(v -> homeSendAction.run());
        bindSendAction(etHomeMessage, homeSendAction);
        bindSendAction(etHomeSearch, () -> sendFromEditText(etHomeSearch));
        findViewById(R.id.btnHomeMic).setOnClickListener(v -> startVoiceFromAnyTab());
        findViewById(R.id.btnHomePlus).setOnClickListener(v -> showQuickActionsDialog());
    }

    private void resetTopicTags() {
        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);
        for (TextView tag : topicTags) {
            if (tag != null) {
                tag.setBackgroundResource(R.drawable.bg_tag_inactive);
                tag.setTextColor(inactiveColor);
            }
        }
    }

    private void setupExploreInteractions() {
        findViewById(R.id.cardExploreConcept).setOnClickListener(v -> submitPromptFromTab("Explain a concept: Quantum computing explained simply"));
        findViewById(R.id.cardExploreIdeas).setOnClickListener(v -> submitPromptFromTab("Give me ideas for productivity and creative coding workflows"));
        findViewById(R.id.cardExploreCode).setOnClickListener(v -> submitPromptFromTab("Write code: Build a REST API in Python / Node.js"));
        findViewById(R.id.cardExploreImage).setOnClickListener(v -> submitPromptFromTab("Capture desktop screen and analyze active window"));

        Runnable exploreSend = () -> sendFromEditText(etExploreMessage);
        findViewById(R.id.btnExploreSend).setOnClickListener(v -> exploreSend.run());
        bindSendAction(etExploreMessage, exploreSend);
        findViewById(R.id.btnExploreMic).setOnClickListener(v -> startVoiceFromAnyTab());
    }

    private void showQuickActionsDialog() {
        String[] actions = {
                "Paste QR / Pairing Link",
                "Capture & Analyze PC Screen",
                "Open Remote & Media Deck",
                "Toggle Fullscreen",
                "Clear Chat History"
        };
        new AlertDialog.Builder(this)
                .setTitle("DEX Quick Actions")
                .setItems(actions, (d, which) -> {
                    switch (which) {
                        case 0 -> {
                            switchTab(3);
                            pastePairingLinkFromClipboard();
                        }
                        case 1 -> submitPromptFromTab("Capture desktop screen and analyze active window");
                        case 2 -> switchTab(3);
                        case 3 -> applyFullscreen(!isFullscreen, true);
                        case 4 -> resetChatConversation();
                    }
                })
                .show();
    }

    // ==================== CHAT & VOICE ====================

    private void setupChatInteractions() {
        findViewById(R.id.btnSend).setOnClickListener(v -> sendCurrentChatMessage());
        bindSendAction(etMessage, this::sendCurrentChatMessage);
        btnChatMic.setOnClickListener(v -> voiceController.toggleListening());

        if (btnChatTtsToggle != null) {
            btnChatTtsToggle.setOnClickListener(v -> {
                boolean next = !voiceController.isTtsEnabled();
                voiceController.setTtsEnabled(next);
                prefs.setTtsEnabled(next);
                updateTtsIcon();
                Toast.makeText(this, next ? "Voice responses enabled" : "Voice responses muted", Toast.LENGTH_SHORT).show();
            });
        }
        seedWelcomeMessageIfEmpty();
    }

    private void seedWelcomeMessageIfEmpty() {
        if (chatMessagesContainer != null && chatMessagesContainer.getChildCount() == 0) {
            String userName = prefs.getUserName(getString(R.string.user_name));
            String greeting = "Hello " + userName + "! I am DEX, your personal AI assistant.\n\n"
                    + "I can launch PC applications (\"open youtube\", \"open vscode\"), inspect your screen, "
                    + "control laptop media, and answer anything over LAN or Cloud Bridge.\n\n"
                    + "Tap the mic below anytime to speak with me!";
            chatRenderer.addAssistantBubble(greeting, ChatBubbleRenderer.nowTimeLabel());
        }
    }

    private void resetChatConversation() {
        chatRenderer.clear();
        seedWelcomeMessageIfEmpty();
        Toast.makeText(this, "Chat history cleared", Toast.LENGTH_SHORT).show();
    }

    private void updateTtsIcon() {
        if (btnChatTtsToggle == null) return;
        boolean on = voiceController.isTtsEnabled();
        int color = ContextCompat.getColor(this, on ? R.color.primary_maroon : R.color.text_muted);
        btnChatTtsToggle.setImageTintList(ColorStateList.valueOf(color));
        btnChatTtsToggle.setAlpha(on ? 1.0f : 0.45f);
    }

    private void startVoiceFromAnyTab() {
        switchTab(1);
        mainHandler.postDelayed(() -> voiceController.startListening(), 250);
    }

    private void handleVoiceText(String spokenText) {
        if (etMessage != null) etMessage.setText(spokenText);
        dispatchUserMessage(spokenText);
    }

    private void sendFromEditText(EditText field) {
        if (field == null || field.getText() == null) return;
        String msg = field.getText().toString().trim();
        if (msg.isEmpty()) return;
        field.setText("");
        voiceController.markNextInputTyped();
        switchTab(1);
        dispatchUserMessage(msg);
    }

    private void submitPromptFromTab(String prompt) {
        voiceController.markNextInputTyped();
        switchTab(1);
        dispatchUserMessage(prompt);
    }

    private void sendCurrentChatMessage() {
        if (etMessage == null || etMessage.getText() == null) return;
        Editable editable = etMessage.getText();
        BaseInputConnection.setComposingSpans(editable);
        String msg = editable.toString().trim();
        if (msg.isEmpty()) return;
        etMessage.setText("");
        voiceController.markNextInputTyped();
        dispatchUserMessage(msg);
    }

    private void dispatchUserMessage(String message) {
        String time = ChatBubbleRenderer.nowTimeLabel();
        chatRenderer.addUserBubble(message, time);

        if (!dexClient.isReady()) {
            chatRenderer.addAssistantBubble(
                    "Device is not connected yet.\n\nOpen Settings to enter your PC IP + 6-digit PIN, paste the QR link, or tap **Use Cloud Relay**.",
                    time);
            return;
        }

        updateClientTelemetry();
        executor.execute(() -> {
            try {
                String reply = dexClient.sendChat(message);
                mainHandler.post(() -> {
                    chatRenderer.addAssistantBubble(reply, ChatBubbleRenderer.nowTimeLabel());
                    voiceController.speakReply(reply);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    String err = "Error connecting to DEX: " + e.getMessage()
                            + "\n\nTip: Ensure DEX is running on your Windows PC, or switch to **Use Cloud Relay** in Settings.";
                    chatRenderer.addAssistantBubble(err, ChatBubbleRenderer.nowTimeLabel());
                    voiceController.speakReply("I could not connect to your Windows PC. Please check your connection.");
                });
            }
        });
    }

    private void bindSendAction(EditText field, Runnable action) {
        if (field == null) return;
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN)) {
                action.run();
                return true;
            }
            return false;
        });
        field.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && !event.isShiftPressed()) {
                action.run();
                return true;
            }
            return false;
        });
    }

    // ==================== SETTINGS, PAIRING, REMOTE & VISION ====================

    private void setupSettingsAndRemote() {
        Button btnSaveName = findViewById(R.id.btnSaveUserName);
        if (btnSaveName != null) {
            btnSaveName.setOnClickListener(v -> {
                String name = etSettingsUserName.getText().toString().trim();
                if (!name.isEmpty()) saveUserName(name);
                else Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
            });
        }

        findViewById(R.id.btnPasteQrLink).setOnClickListener(v -> pastePairingLinkFromClipboard());
        findViewById(R.id.btnCloudRelayConnect).setOnClickListener(v -> connectToCloudRelay());
        findViewById(R.id.btnPair).setOnClickListener(v -> handlePairOrConnectClick());
        findViewById(R.id.btnCheckConnection).setOnClickListener(v -> testConnection(true));

        findViewById(R.id.btnUnpair).setOnClickListener(v -> {
            String host = etHostIp.getText().toString().trim();
            int port = parsePortOrDefault(etPort.getText().toString(), DexClient.DEFAULT_PORT);
            saveConfig(host, port, null, null);
            setOnlineStatus(false);
            tvPairingStatus.setText("Device unpaired. Enter PIN, paste QR link, or tap Use Cloud Relay.");
            Toast.makeText(this, "Device unpaired.", Toast.LENGTH_SHORT).show();
        });

        // Quick-launch app buttons
        int[] launchIds = {
                R.id.btnLaunchChrome, R.id.btnLaunchYouTube, R.id.btnLaunchGitHub, R.id.btnLaunchVsCode,
                R.id.btnLaunchNotepad, R.id.btnLaunchCalc, R.id.btnLaunchExplorer, R.id.btnLaunchSettings
        };
        String[] launchTargets = {
                "chrome", "youtube", "github", "vscode",
                "notepad", "calculator", "file explorer", "settings"
        };
        for (int i = 0; i < launchIds.length; i++) {
            final String appTarget = launchTargets[i];
            View btn = findViewById(launchIds[i]);
            if (btn != null) btn.setOnClickListener(v -> triggerRemoteLaunch(appTarget));
        }

        // Tactile media & window remote buttons
        int[] remoteIds = {
                R.id.btnRemotePlayPause, R.id.btnRemoteVolDown, R.id.btnRemoteVolUp,
                R.id.btnRemoteMute, R.id.btnRemoteMinAll
        };
        String[] remoteCommands = {
                "media_play_pause", "vol_down", "vol_up", "mute", "minimize_all"
        };
        for (int i = 0; i < remoteIds.length; i++) {
            final String remoteCmd = remoteCommands[i];
            View btn = findViewById(remoteIds[i]);
            if (btn != null) btn.setOnClickListener(v -> triggerRemoteControl(remoteCmd));
        }

        // Desktop Vision
        findViewById(R.id.btnTakeScreenshot).setOnClickListener(v -> {
            tvVisionResult.setText("Capturing PC screen...");
            executor.execute(() -> {
                try {
                    JSONObject res = dexClient.captureScreen();
                    String msg = res.optString("message", "Screenshot captured.");
                    mainHandler.post(() -> tvVisionResult.setText(msg));
                } catch (Exception e) {
                    mainHandler.post(() -> tvVisionResult.setText("Screenshot error: " + e.getMessage()));
                }
            });
        });

        findViewById(R.id.btnAnalyzeScreen).setOnClickListener(v -> {
            String prompt = etVisionPrompt.getText().toString().trim();
            tvVisionResult.setText("Analyzing PC screen with AI...");
            executor.execute(() -> {
                try {
                    String analysis = dexClient.analyzeScreen(prompt);
                    mainHandler.post(() -> tvVisionResult.setText(analysis));
                } catch (Exception e) {
                    mainHandler.post(() -> tvVisionResult.setText("Analysis error: " + e.getMessage()));
                }
            });
        });
    }

    private void pastePairingLinkFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
            if (clip != null && applyParsedPairingInput(clip.toString())) {
                return;
            }
        }
        Toast.makeText(this, "Copy the QR URL from DEX Desktop/Terminal, then tap Paste QR.", Toast.LENGTH_SHORT).show();
    }

    private boolean applyParsedPairingInput(String raw) {
        int currentPort = parsePortOrDefault(etPort.getText().toString(), DexClient.DEFAULT_PORT);
        DexClient.PairingInfo info = DexClient.parsePairingInput(raw, currentPort);
        if (info.serverUrl == null && info.pin == null) return false;

        if (info.host != null) etHostIp.setText(info.host);
        if (info.port > 0) etPort.setText(String.valueOf(info.port));
        if (info.pin != null) etPairingPin.setText(info.pin);

        if (info.token != null && info.serverUrl != null) {
            String targetHost = info.host != null ? info.host : info.serverUrl;
            saveConfig(targetHost, info.port, info.token, prefs.getOrCreateDeviceId());
            tvPairingStatus.setText("QR Token imported! Verifying connection...");
            testConnection(true);
            return true;
        }
        if (info.serverUrl != null && DexClient.buildServerUrl(info.serverUrl, info.port).startsWith("https://")) {
            saveConfig(info.serverUrl, 443, null, prefs.getOrCreateDeviceId());
            testConnection(true);
            return true;
        }
        if (info.pin != null) {
            Toast.makeText(this, "PIN parsed. Tap Pair / Connect.", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private void connectToCloudRelay() {
        etHostIp.setText(DexClient.CLOUD_RELAY_PRIMARY);
        etPort.setText("443");
        etPairingPin.setText("");
        saveConfig(DexClient.CLOUD_RELAY_PRIMARY, 443, null, prefs.getOrCreateDeviceId());
        tvPairingStatus.setText("Connecting to DEX Cloud Relay...");
        testConnection(true);
    }

    private void handlePairOrConnectClick() {
        String hostInput = etHostIp.getText().toString().trim();
        int port = parsePortOrDefault(etPort.getText().toString(), DexClient.DEFAULT_PORT);
        String pinOrLink = etPairingPin.getText().toString().trim();

        if (!pinOrLink.isEmpty() && (pinOrLink.startsWith("http") || pinOrLink.contains("token="))) {
            if (applyParsedPairingInput(pinOrLink)) return;
        }
        if (!hostInput.isEmpty() && (hostInput.contains("token=") || hostInput.contains("?pin="))) {
            if (applyParsedPairingInput(hostInput)) return;
        }

        String resolvedUrl = DexClient.buildServerUrl(hostInput, port);
        dexClient.setServerUrl(resolvedUrl);

        if (dexClient.isCloudRelay()) {
            saveConfig(resolvedUrl, 443, null, prefs.getOrCreateDeviceId());
            tvPairingStatus.setText("Connected to DEX Cloud Relay. Verifying status...");
            testConnection(true);
            return;
        }

        if (pinOrLink.isEmpty()) {
            Toast.makeText(this, "Enter 6-digit PIN from DEX PC or tap Paste QR / Use Cloud Relay.", Toast.LENGTH_SHORT).show();
            return;
        }

        tvPairingStatus.setText("Pairing device with DEX PC...");
        executor.execute(() -> {
            try {
                JSONObject resp = dexClient.pairWithPin(pinOrLink, "Android " + Build.MODEL);
                String token = resp.getString("token");
                String devId = resp.getString("deviceId");
                dexClient.sendHeartbeat();

                mainHandler.post(() -> {
                    saveConfig(hostInput, port, token, devId);
                    tvPairingStatus.setText("Successfully paired! Device ID: " + devId);
                    setOnlineStatus(true);
                    Toast.makeText(this, "Paired successfully with DEX PC!", Toast.LENGTH_LONG).show();
                    etPairingPin.setText("");
                    testConnection(false);
                    switchTab(1);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    String errMsg = "Pairing failed: " + e.getMessage();
                    if (e.getMessage() != null && e.getMessage().contains("Failed to connect")) {
                        errMsg += "\nTip: Check DEX PC IP in Mobile Companion tab or try Use Cloud Relay.";
                    }
                    tvPairingStatus.setText(errMsg);
                    setOnlineStatus(false);
                });
            }
        });
    }

    private void triggerRemoteLaunch(String appName) {
        if (!dexClient.isReady()) {
            tvWindowsActionStatus.setText("Connect or pair first before launching " + appName + ".");
            return;
        }
        updateClientTelemetry();
        tvWindowsActionStatus.setText("Launching " + appName + " on laptop...");
        executor.execute(() -> {
            try {
                String res = dexClient.launchApp(appName);
                mainHandler.post(() -> {
                    tvWindowsActionStatus.setText("Laptop: " + res);
                    Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> tvWindowsActionStatus.setText("Launch failed: " + e.getMessage()));
            }
        });
    }

    private void triggerRemoteControl(String controlCommand) {
        if (!dexClient.isReady()) {
            tvWindowsActionStatus.setText("Connect or pair first to send remote controls.");
            return;
        }
        updateClientTelemetry();
        tvWindowsActionStatus.setText("Sending " + controlCommand + " to laptop...");
        executor.execute(() -> {
            try {
                String res = dexClient.sendRemoteControl(controlCommand);
                mainHandler.post(() -> {
                    tvWindowsActionStatus.setText("Remote: " + res);
                    Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> tvWindowsActionStatus.setText("Control failed: " + e.getMessage()));
            }
        });
    }

    private void testConnection(boolean notifyUser) {
        updateClientTelemetry();
        executor.execute(() -> {
            try {
                JSONObject status = dexClient.checkStatus();
                boolean online = "ONLINE".equalsIgnoreCase(status.optString("status"));
                String userName = status.optString("userName", "");
                if (dexClient.isReady()) {
                    try {
                        dexClient.sendHeartbeat();
                    } catch (Exception ignored) {
                    }
                }
                mainHandler.post(() -> {
                    setOnlineStatus(online);
                    if (!userName.isEmpty()) {
                        prefs.setUserName(userName);
                        updateUserNameDisplay(userName);
                    }
                    if (notifyUser) {
                        String msg = "Connected to " + status.optString("assistant", "DEX")
                                + " | Provider: " + status.optString("activeProvider", "Default");
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                        tvPairingStatus.setText(msg);
                    }
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    setOnlineStatus(false);
                    if (notifyUser) {
                        Toast.makeText(this, "Connection failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        tvPairingStatus.setText("Connection failed: " + e.getMessage());
                    }
                });
            }
        });
    }

    private void setOnlineStatus(boolean online) {
        int color = ContextCompat.getColor(this, online ? R.color.status_online : R.color.status_offline);
        String text = online ? (dexClient.isReady() ? "ONLINE" : "UNPAIRED") : "OFFLINE";
        if (tvStatusBadge != null) {
            tvStatusBadge.setText(text);
            tvStatusBadge.setTextColor(color);
        }
        if (tvHomeStatusBadge != null) {
            tvHomeStatusBadge.setText(text);
            tvHomeStatusBadge.setTextColor(color);
        }
    }

    private static int parsePortOrDefault(String raw, int fallback) {
        try {
            int p = Integer.parseInt(raw != null ? raw.trim() : "");
            return p > 0 && p <= 65535 ? p : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}

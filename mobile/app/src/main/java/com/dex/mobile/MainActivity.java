package com.dex.mobile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AI Assistant / DEX Mobile Companion.
 * Matches luxury warm cream / ivory and maroon design aesthetics.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "DEX_COMPANION_PREFS";
    private static final String KEY_HOST = "dex_host";
    private static final String KEY_PORT = "dex_port";
    private static final String KEY_TOKEN = "dex_auth_token";
    private static final String KEY_DEVICE_ID = "dex_device_id";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DexClient dexClient = new DexClient();

    // Pages
    private View pageHome, pageChat, pageExplore, pageSettings;

    // Navigation Tabs
    private View navHome, navChat, navExplore, navSettings;
    private ImageView ivNavHome, ivNavChat, ivNavExplore, ivNavSettings;
    private TextView tvNavHome, tvNavChat, tvNavExplore, tvNavSettings;

    // Status Badges
    private TextView tvHomeStatusBadge, tvStatusBadge, tvEndpoint;

    // Home Views
    private EditText etHomeSearch, etHomeMessage;
    private ImageView btnHomePlus, btnHomeMic, btnHomeSend;
    private View cardHomeChat, cardHomeVoice, cardHomeImage, cardHomeFiles;
    private TextView tagDailyLife, tagStudy, tagBusiness, tagHealth, tagDevelopment;

    // Explore Views
    private EditText etExploreMessage;
    private ImageView btnExploreBack, btnExploreMenu, btnExploreMic, btnExploreSend;
    private View cardExploreConcept, cardExploreIdeas, cardExploreCode, cardExploreImage;

    // Chat Views
    private EditText etMessage;
    private ImageView btnChatBack, btnChatMenu, btnChatMic, btnSend;
    private ScrollView chatScrollView;
    private LinearLayout chatMessagesContainer;

    // Settings Views
    private EditText etHostIp, etPort, etPairingPin, etVisionPrompt;
    private Button btnPair, btnCheckConnection, btnUnpair, btnTakeScreenshot, btnAnalyzeScreen;
    private TextView tvPairingStatus, tvWindowsActionStatus, tvVisionResult;

    private int activePageIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        loadSavedConfig();
        setupNavigation();
        setupHomeInteractions();
        setupExploreInteractions();
        setupChatInteractions();
        setupSettingsAndRemote();

        // Initial connection ping
        testConnection(false);
    }

    private void bindViews() {
        // Pages
        pageHome = findViewById(R.id.pageHome);
        pageChat = findViewById(R.id.pageChat);
        pageExplore = findViewById(R.id.pageExplore);
        pageSettings = findViewById(R.id.pageSettings);

        // Navigation
        navHome = findViewById(R.id.navHome);
        navChat = findViewById(R.id.navChat);
        navExplore = findViewById(R.id.navExplore);
        navSettings = findViewById(R.id.navSettings);

        ivNavHome = findViewById(R.id.ivNavHome);
        ivNavChat = findViewById(R.id.ivNavChat);
        ivNavExplore = findViewById(R.id.ivNavExplore);
        ivNavSettings = findViewById(R.id.ivNavSettings);

        tvNavHome = findViewById(R.id.tvNavHome);
        tvNavChat = findViewById(R.id.tvNavChat);
        tvNavExplore = findViewById(R.id.tvNavExplore);
        tvNavSettings = findViewById(R.id.tvNavSettings);

        // Status
        tvHomeStatusBadge = findViewById(R.id.tvHomeStatusBadge);
        tvStatusBadge = findViewById(R.id.tvStatusBadge);
        tvEndpoint = findViewById(R.id.tvEndpoint);

        // Home
        etHomeSearch = findViewById(R.id.etHomeSearch);
        etHomeMessage = findViewById(R.id.etHomeMessage);
        btnHomePlus = findViewById(R.id.btnHomePlus);
        btnHomeMic = findViewById(R.id.btnHomeMic);
        btnHomeSend = findViewById(R.id.btnHomeSend);
        cardHomeChat = findViewById(R.id.cardHomeChat);
        cardHomeVoice = findViewById(R.id.cardHomeVoice);
        cardHomeImage = findViewById(R.id.cardHomeImage);
        cardHomeFiles = findViewById(R.id.cardHomeFiles);
        tagDailyLife = findViewById(R.id.tagDailyLife);
        tagStudy = findViewById(R.id.tagStudy);
        tagBusiness = findViewById(R.id.tagBusiness);
        tagHealth = findViewById(R.id.tagHealth);
        tagDevelopment = findViewById(R.id.tagDevelopment);

        // Explore
        etExploreMessage = findViewById(R.id.etExploreMessage);
        btnExploreBack = findViewById(R.id.btnExploreBack);
        btnExploreMenu = findViewById(R.id.btnExploreMenu);
        btnExploreMic = findViewById(R.id.btnExploreMic);
        btnExploreSend = findViewById(R.id.btnExploreSend);
        cardExploreConcept = findViewById(R.id.cardExploreConcept);
        cardExploreIdeas = findViewById(R.id.cardExploreIdeas);
        cardExploreCode = findViewById(R.id.cardExploreCode);
        cardExploreImage = findViewById(R.id.cardExploreImage);

        // Chat
        etMessage = findViewById(R.id.etMessage);
        btnChatBack = findViewById(R.id.btnChatBack);
        btnChatMenu = findViewById(R.id.btnChatMenu);
        btnChatMic = findViewById(R.id.btnChatMic);
        btnSend = findViewById(R.id.btnSend);
        chatScrollView = findViewById(R.id.chatScrollView);
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer);

        // Settings
        etHostIp = findViewById(R.id.etHostIp);
        etPort = findViewById(R.id.etPort);
        etPairingPin = findViewById(R.id.etPairingPin);
        btnPair = findViewById(R.id.btnPair);
        btnCheckConnection = findViewById(R.id.btnCheckConnection);
        btnUnpair = findViewById(R.id.btnUnpair);
        tvPairingStatus = findViewById(R.id.tvPairingStatus);
        tvWindowsActionStatus = findViewById(R.id.tvWindowsActionStatus);

        etVisionPrompt = findViewById(R.id.etVisionPrompt);
        btnTakeScreenshot = findViewById(R.id.btnTakeScreenshot);
        btnAnalyzeScreen = findViewById(R.id.btnAnalyzeScreen);
        tvVisionResult = findViewById(R.id.tvVisionResult);
    }

    private void loadSavedConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String host = prefs.getString(KEY_HOST, "192.168.1.35");
        int port = prefs.getInt(KEY_PORT, 31416);
        String token = prefs.getString(KEY_TOKEN, null);
        String devId = prefs.getString(KEY_DEVICE_ID, null);

        if (etHostIp != null) etHostIp.setText(host);
        if (etPort != null) etPort.setText(String.valueOf(port));

        String url = "http://" + host + ":" + port;
        dexClient.setServerUrl(url);
        dexClient.setAuthToken(token);
        dexClient.setDeviceId(devId);

        updateEndpointDisplay();
    }

    private void saveConfig(String host, int port, String token, String devId) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(KEY_HOST, host);
        editor.putInt(KEY_PORT, port);
        if (token != null) {
            editor.putString(KEY_TOKEN, token);
        } else {
            editor.remove(KEY_TOKEN);
        }
        if (devId != null) {
            editor.putString(KEY_DEVICE_ID, devId);
        } else {
            editor.remove(KEY_DEVICE_ID);
        }
        editor.apply();

        dexClient.setServerUrl("http://" + host + ":" + port);
        dexClient.setAuthToken(token);
        dexClient.setDeviceId(devId);
        updateEndpointDisplay();
    }

    private void updateEndpointDisplay() {
        String info = dexClient.getServerUrl() + (dexClient.isPaired() ? " (Paired)" : " (Unpaired)");
        if (tvEndpoint != null) tvEndpoint.setText(info);
    }

    private void setupNavigation() {
        navHome.setOnClickListener(v -> switchTab(0));
        navChat.setOnClickListener(v -> switchTab(1));
        navExplore.setOnClickListener(v -> switchTab(2));
        navSettings.setOnClickListener(v -> switchTab(3));

        btnExploreBack.setOnClickListener(v -> switchTab(0));
        btnChatBack.setOnClickListener(v -> switchTab(0));

        findViewById(R.id.btnProfileAvatar).setOnClickListener(v -> switchTab(3));
        findViewById(R.id.btnHomeSeeAllTopics).setOnClickListener(v -> switchTab(2));
        findViewById(R.id.btnRecentSeeAll).setOnClickListener(v -> switchTab(2));
    }

    public void switchTab(int index) {
        activePageIndex = index;

        pageHome.setVisibility(index == 0 ? View.VISIBLE : View.GONE);
        pageChat.setVisibility(index == 1 ? View.VISIBLE : View.GONE);
        pageExplore.setVisibility(index == 2 ? View.VISIBLE : View.GONE);
        pageSettings.setVisibility(index == 3 ? View.VISIBLE : View.GONE);

        int activeColor = ContextCompat.getColor(this, R.color.nav_active);
        int inactiveColor = ContextCompat.getColor(this, R.color.nav_inactive);

        // Update Nav Icon Tints
        ivNavHome.setImageTintList(ColorStateList.valueOf(index == 0 ? activeColor : inactiveColor));
        ivNavChat.setImageTintList(ColorStateList.valueOf(index == 1 ? activeColor : inactiveColor));
        ivNavExplore.setImageTintList(ColorStateList.valueOf(index == 2 ? activeColor : inactiveColor));
        ivNavSettings.setImageTintList(ColorStateList.valueOf(index == 3 ? activeColor : inactiveColor));

        // Update Nav Text Colors
        tvNavHome.setTextColor(index == 0 ? activeColor : inactiveColor);
        tvNavChat.setTextColor(index == 1 ? activeColor : inactiveColor);
        tvNavExplore.setTextColor(index == 2 ? activeColor : inactiveColor);
        tvNavSettings.setTextColor(index == 3 ? activeColor : inactiveColor);
    }

    // ==================== SCREEN 1: HOME ====================

    private void setupHomeInteractions() {
        // Feature Cards
        cardHomeChat.setOnClickListener(v -> switchTab(1));
        cardHomeVoice.setOnClickListener(v -> {
            switchTab(1);
            Toast.makeText(this, "Voice mode activated. Listening...", Toast.LENGTH_SHORT).show();
        });
        cardHomeImage.setOnClickListener(v -> {
            switchTab(1);
            dispatchUserMessage("Generate an image of a futuristic minimalist workspace");
        });
        cardHomeFiles.setOnClickListener(v -> {
            switchTab(1);
            dispatchUserMessage("Analyze workspace documents and show summary");
        });

        // Quick Topic Tags
        setupTopicTag(tagDailyLife, "Daily Life");
        setupTopicTag(tagStudy, "Study");
        setupTopicTag(tagBusiness, "Business");
        setupTopicTag(tagHealth, "Health");
        setupTopicTag(tagDevelopment, "Development");

        // Home Bottom Input
        btnHomeSend.setOnClickListener(v -> {
            String msg = etHomeMessage.getText().toString().trim();
            if (msg.isEmpty()) return;
            etHomeMessage.setText("");
            switchTab(1);
            dispatchUserMessage(msg);
        });

        btnHomeMic.setOnClickListener(v -> {
            Toast.makeText(this, "Listening for voice prompt...", Toast.LENGTH_SHORT).show();
        });

        btnHomePlus.setOnClickListener(v -> {
            Toast.makeText(this, "Attach file or image", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupTopicTag(TextView tagView, String topicName) {
        if (tagView == null) return;
        tagView.setOnClickListener(v -> {
            resetTopicTags();
            tagView.setBackgroundResource(R.drawable.bg_tag_active);
            tagView.setTextColor(Color.WHITE);
            switchTab(1);
            dispatchUserMessage("Explore insights on " + topicName);
        });
    }

    private void resetTopicTags() {
        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);
        TextView[] tags = {tagDailyLife, tagStudy, tagBusiness, tagHealth, tagDevelopment};
        for (TextView tag : tags) {
            if (tag != null) {
                tag.setBackgroundResource(R.drawable.bg_tag_inactive);
                tag.setTextColor(inactiveColor);
            }
        }
    }

    // ==================== SCREEN 2: EXPLORE ====================

    private void setupExploreInteractions() {
        cardExploreConcept.setOnClickListener(v -> {
            switchTab(1);
            dispatchUserMessage("Explain a concept: Quantum computing explained simply");
        });

        cardExploreIdeas.setOnClickListener(v -> {
            switchTab(1);
            dispatchUserMessage("Give me ideas for productivity and creative coding workflows");
        });

        cardExploreCode.setOnClickListener(v -> {
            switchTab(1);
            dispatchUserMessage("Write code: Build a REST API in Python / Node.js");
        });

        cardExploreImage.setOnClickListener(v -> {
            switchTab(1);
            dispatchUserMessage("Generate an image: Elegant rose gold crystal orb");
        });

        btnExploreSend.setOnClickListener(v -> {
            String msg = etExploreMessage.getText().toString().trim();
            if (msg.isEmpty()) return;
            etExploreMessage.setText("");
            switchTab(1);
            dispatchUserMessage(msg);
        });

        btnExploreMic.setOnClickListener(v -> {
            Toast.makeText(this, "Listening for prompt...", Toast.LENGTH_SHORT).show();
        });

        btnExploreMenu.setOnClickListener(v -> switchTab(3));
    }

    // ==================== SCREEN 3: CHAT ====================

    private void setupChatInteractions() {
        btnSend.setOnClickListener(v -> {
            String msg = etMessage.getText().toString().trim();
            if (msg.isEmpty()) return;
            etMessage.setText("");
            dispatchUserMessage(msg);
        });

        btnChatMic.setOnClickListener(v -> {
            Toast.makeText(this, "Microphone enabled", Toast.LENGTH_SHORT).show();
        });

        btnChatMenu.setOnClickListener(v -> switchTab(3));

        // Sample copy button
        View copyBtn = findViewById(R.id.btnCopySample);
        if (copyBtn != null) {
            copyBtn.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Copied", "Web3 is a decentralized internet built on blockchain technology...");
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            });
        }

        // Recent Chats Clicks
        setupRecentChat(R.id.recentChat1, "Study Plan for Maths");
        setupRecentChat(R.id.recentChat2, "Create a Product Image");
        setupRecentChat(R.id.recentChat3, "YouTube Tags");
        setupRecentChat(R.id.recentChat4, "Web App Deployment");
        setupRecentChat(R.id.recentChat5, "Gaming Setup Help");
    }

    private void setupRecentChat(int viewId, String promptTitle) {
        View view = findViewById(viewId);
        if (view != null) {
            view.setOnClickListener(v -> dispatchUserMessage(promptTitle));
        }
    }

    private void dispatchUserMessage(String message) {
        String currentTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
        addUserMessageBubble(message, currentTime);

        if (!dexClient.isPaired()) {
            addAssistantMessageBubble("Device is not paired yet.\n\nPlease open the Settings tab and enter the 6-digit PIN displayed on your DEX Windows PC to connect.", currentTime);
            return;
        }

        executor.execute(() -> {
            try {
                String reply = dexClient.sendChat(message);
                mainHandler.post(() -> {
                    String replyTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
                    addAssistantMessageBubble(reply, replyTime);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    String replyTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
                    addAssistantMessageBubble("Error connecting to DEX: " + e.getMessage() + "\n\nTip: Ensure DEX is running on your Windows PC and both devices are on the same Wi-Fi.", replyTime);
                });
            }
        });
    }

    private void addUserMessageBubble(String text, String time) {
        LinearLayout userBubbleLayout = new LinearLayout(this);
        userBubbleLayout.setOrientation(LinearLayout.VERTICAL);
        userBubbleLayout.setBackgroundResource(R.drawable.bg_chat_user);
        int padH = dpToPx(16);
        int padV = dpToPx(12);
        userBubbleLayout.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.gravity = Gravity.END;
        params.topMargin = dpToPx(12);
        userBubbleLayout.setLayoutParams(params);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(text);
        tvMsg.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        tvMsg.setTextSize(15);
        userBubbleLayout.addView(tvMsg);

        // Timestamp row with double checkmark
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams timeRowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        timeRowParams.gravity = Gravity.END;
        timeRowParams.topMargin = dpToPx(4);
        timeRow.setLayoutParams(timeRowParams);

        TextView tvTime = new TextView(this);
        tvTime.setText(time);
        tvTime.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        tvTime.setTextSize(11);
        timeRow.addView(tvTime);

        ImageView ivCheck = new ImageView(this);
        ivCheck.setImageResource(R.drawable.ic_check_double);
        LinearLayout.LayoutParams checkParams = new LinearLayout.LayoutParams(dpToPx(14), dpToPx(14));
        checkParams.leftMargin = dpToPx(4);
        ivCheck.setLayoutParams(checkParams);
        timeRow.addView(ivCheck);

        userBubbleLayout.addView(timeRow);

        chatMessagesContainer.addView(userBubbleLayout);
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void addAssistantMessageBubble(String text, String time) {
        LinearLayout assistantRow = new LinearLayout(this);
        assistantRow.setOrientation(LinearLayout.HORIZONTAL);
        assistantRow.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = dpToPx(16);
        assistantRow.setLayoutParams(rowParams);

        // Assistant Logo Avatar
        ImageView ivLogo = new ImageView(this);
        ivLogo.setImageResource(R.drawable.ic_assistant_logo);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dpToPx(32), dpToPx(32));
        logoParams.topMargin = dpToPx(4);
        ivLogo.setLayoutParams(logoParams);
        assistantRow.addView(ivLogo);

        // Bubble Card
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_chat_assistant);
        int pad = dpToPx(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1.0f
        );
        cardParams.leftMargin = dpToPx(10);
        card.setLayoutParams(cardParams);

        TextView tvText = new TextView(this);
        tvText.setText(text);
        tvText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        tvText.setTextSize(14);
        tvText.setLineSpacing(dpToPx(3), 1.0f);
        card.addView(tvText);

        // Bottom Row with Timestamp and Copy Button
        RelativeLayout bottomBar = new RelativeLayout(this);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        barParams.topMargin = dpToPx(10);
        bottomBar.setLayoutParams(barParams);

        TextView tvTime = new TextView(this);
        tvTime.setText(time);
        tvTime.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        tvTime.setTextSize(11);
        RelativeLayout.LayoutParams timeParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        timeParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        timeParams.addRule(RelativeLayout.CENTER_VERTICAL);
        tvTime.setLayoutParams(timeParams);
        bottomBar.addView(tvTime);

        ImageView btnCopy = new ImageView(this);
        btnCopy.setImageResource(R.drawable.ic_copy);
        RelativeLayout.LayoutParams copyParams = new RelativeLayout.LayoutParams(dpToPx(20), dpToPx(20));
        copyParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        copyParams.addRule(RelativeLayout.CENTER_VERTICAL);
        btnCopy.setLayoutParams(copyParams);
        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied", text);
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        bottomBar.addView(btnCopy);

        card.addView(bottomBar);
        assistantRow.addView(card);

        chatMessagesContainer.addView(assistantRow);
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ==================== SCREEN 4: SETTINGS, REMOTE & VISION ====================

    private void setupSettingsAndRemote() {
        btnPair.setOnClickListener(v -> {
            String host = etHostIp.getText().toString().trim();
            int port = 31416;
            try {
                port = Integer.parseInt(etPort.getText().toString().trim());
            } catch (Exception ignored) {}

            String pin = etPairingPin.getText().toString().trim();
            if (pin.isEmpty()) {
                Toast.makeText(this, "Please enter 6-digit PIN from DEX PC", Toast.LENGTH_SHORT).show();
                return;
            }

            tvPairingStatus.setText("Pairing device with DEX PC...");
            String serverUrl = "http://" + host + ":" + port;
            dexClient.setServerUrl(serverUrl);

            int finalPort = port;
            executor.execute(() -> {
                try {
                    String deviceName = "Android " + Build.MODEL;
                    JSONObject resp = dexClient.pairWithPin(pin, deviceName);
                    String token = resp.getString("token");
                    String devId = resp.getString("deviceId");

                    mainHandler.post(() -> {
                        saveConfig(host, finalPort, token, devId);
                        tvPairingStatus.setText("Successfully paired! Device ID: " + devId);
                        setOnlineStatus(true);
                        Toast.makeText(this, "Paired successfully with DEX PC!", Toast.LENGTH_LONG).show();
                        etPairingPin.setText("");
                        switchTab(1); // Switch to Chat
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        String errMsg = "Pairing failed: " + e.getMessage();
                        if (host.equals("192.168.1.100") || (e.getMessage() != null && e.getMessage().contains("Failed to connect"))) {
                            errMsg += "\nTip: Check DEX Settings on PC for your exact IP (e.g. Wi-Fi 192.168.x.x or Tailscale).";
                        }
                        tvPairingStatus.setText(errMsg);
                        setOnlineStatus(false);
                    });
                }
            });
        });

        btnCheckConnection.setOnClickListener(v -> testConnection(true));

        btnUnpair.setOnClickListener(v -> {
            String host = etHostIp.getText().toString().trim();
            int port = 31416;
            try { port = Integer.parseInt(etPort.getText().toString().trim()); } catch (Exception ignored) {}
            saveConfig(host, port, null, null);
            setOnlineStatus(false);
            tvPairingStatus.setText("Device unpaired. Re-enter PIN to pair again.");
            Toast.makeText(this, "Device unpaired.", Toast.LENGTH_SHORT).show();
        });

        // Windows Remote Launchers
        findViewById(R.id.btnLaunchChrome).setOnClickListener(v -> triggerRemoteLaunch("chrome"));
        findViewById(R.id.btnLaunchYouTube).setOnClickListener(v -> triggerRemoteLaunch("youtube"));
        findViewById(R.id.btnLaunchGitHub).setOnClickListener(v -> triggerRemoteLaunch("github"));
        findViewById(R.id.btnLaunchVsCode).setOnClickListener(v -> triggerRemoteLaunch("vscode"));
        findViewById(R.id.btnLaunchNotepad).setOnClickListener(v -> triggerRemoteLaunch("notepad"));
        findViewById(R.id.btnLaunchCalc).setOnClickListener(v -> triggerRemoteLaunch("calculator"));
        findViewById(R.id.btnLaunchExplorer).setOnClickListener(v -> triggerRemoteLaunch("file explorer"));
        findViewById(R.id.btnLaunchSettings).setOnClickListener(v -> triggerRemoteLaunch("settings"));

        // Screen Vision
        btnTakeScreenshot.setOnClickListener(v -> {
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

        btnAnalyzeScreen.setOnClickListener(v -> {
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

    private void triggerRemoteLaunch(String appName) {
        tvWindowsActionStatus.setText("Launching " + appName + " on PC...");
        executor.execute(() -> {
            try {
                String res = dexClient.launchApp(appName);
                mainHandler.post(() -> {
                    tvWindowsActionStatus.setText("Status: " + res);
                    Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> tvWindowsActionStatus.setText("Launch failed: " + e.getMessage()));
            }
        });
    }

    private void testConnection(boolean notifyUser) {
        executor.execute(() -> {
            try {
                JSONObject status = dexClient.checkStatus();
                boolean online = "ONLINE".equalsIgnoreCase(status.optString("status"));
                mainHandler.post(() -> {
                    setOnlineStatus(online);
                    if (notifyUser) {
                        String msg = "Connected to " + status.optString("assistant", "AI Assistant")
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
        String text = online ? (dexClient.isPaired() ? "ONLINE" : "UNPAIRED") : "OFFLINE";

        if (tvStatusBadge != null) {
            tvStatusBadge.setText(text);
            tvStatusBadge.setTextColor(color);
        }
        if (tvHomeStatusBadge != null) {
            tvHomeStatusBadge.setText(text);
            tvHomeStatusBadge.setTextColor(color);
        }
    }
}

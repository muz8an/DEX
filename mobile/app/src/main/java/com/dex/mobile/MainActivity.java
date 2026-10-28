package com.dex.mobile;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
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
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DEX Mobile Companion.
 * Rich voice chat, text messaging, and remote controls.
 */
public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "DEX_COMPANION_PREFS";
    private static final String KEY_HOST = "dex_host";
    private static final String KEY_PORT = "dex_port";
    private static final String KEY_TOKEN = "dex_auth_token";
    private static final String KEY_DEVICE_ID = "dex_device_id";
    private static final String KEY_USER_NAME = "dex_user_name";
    private static final String KEY_TTS_ENABLED = "dex_tts_enabled";

    private static final int REQUEST_RECORD_AUDIO = 3001;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final DexClient dexClient = new DexClient();

    // Voice & Speech Components
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean isTtsInitialized = false;
    private boolean isTtsEnabled = true;
    private boolean isLastInputVoice = false;
    private boolean isListening = false;
    private String currentSpeakingText = null;
    private ActivityResultLauncher<Intent> voiceFallbackLauncher;

    // Pages
    private View pageHome, pageChat, pageExplore, pageSettings;

    // Navigation Tabs
    private View navHome, navChat, navExplore, navSettings;
    private ImageView ivNavHome, ivNavChat, ivNavExplore, ivNavSettings;
    private TextView tvNavHome, tvNavChat, tvNavExplore, tvNavSettings;

    // Status Badges
    private TextView tvHomeStatusBadge, tvStatusBadge, tvEndpoint;

    // Home Views
    private TextView tvHomeUserName;
    private EditText etHomeSearch, etHomeMessage;
    private ImageView btnHomePlus, btnHomeMic, btnHomeSend;
    private View cardHomeChat, cardHomeVoice, cardHomeImage, cardHomeFiles;
    private TextView tagDailyLife, tagStudy, tagBusiness, tagHealth, tagDevelopment;

    // Explore Views
    private TextView tvExploreUserName;
    private EditText etExploreMessage;
    private ImageView btnExploreBack, btnExploreMenu, btnExploreMic, btnExploreSend;
    private View cardExploreConcept, cardExploreIdeas, cardExploreCode, cardExploreImage;

    // Chat Views
    private EditText etMessage;
    private ImageView btnChatBack, btnChatMenu, btnChatMic, btnSend, btnChatTtsToggle;
    private ScrollView chatScrollView;
    private LinearLayout chatMessagesContainer;

    // Voice Active Banner
    private View layoutVoiceActive;
    private ImageView ivVoicePulse;
    private TextView tvVoiceStatus, btnCancelVoice;

    // Settings Views
    private EditText etSettingsUserName;
    private Button btnSaveUserName;
    private EditText etHostIp, etPort, etPairingPin, etVisionPrompt;
    private Button btnPair, btnCheckConnection, btnUnpair, btnTakeScreenshot, btnAnalyzeScreen;
    private TextView tvPairingStatus, tvWindowsActionStatus, tvVisionResult;

    private int activePageIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initVoiceFallbackLauncher();
        initTts();
        initSpeechRecognition();

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

    private void initVoiceFallbackLauncher() {
        voiceFallbackLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    stopVoiceListening();
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            String spokenText = matches.get(0);
                            onVoiceInputReceived(spokenText);
                        }
                    }
                }
        );
    }

    private void initTts() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                isTtsInitialized = true;
                tts.setLanguage(Locale.getDefault());
                tts.setPitch(1.0f);
                tts.setSpeechRate(1.05f); // Natural conversational speed
            }
        });
    }

    private void initSpeechRecognition() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                    if (tvVoiceStatus != null) tvVoiceStatus.setText("Listening... Speak now");
                }

                @Override
                public void onBeginningOfSpeech() {
                    if (tvVoiceStatus != null) tvVoiceStatus.setText("Hearing you...");
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    if (ivVoicePulse != null) {
                        float scale = 1.0f + Math.max(0f, rmsdB / 12f);
                        ivVoicePulse.setScaleX(scale);
                        ivVoicePulse.setScaleY(scale);
                    }
                }

                @Override
                public void onBufferReceived(byte[] buffer) {}

                @Override
                public void onEndOfSpeech() {
                    if (tvVoiceStatus != null) tvVoiceStatus.setText("Processing speech...");
                    if (ivVoicePulse != null) {
                        ivVoicePulse.setScaleX(1.0f);
                        ivVoicePulse.setScaleY(1.0f);
                    }
                }

                @Override
                public void onError(int error) {
                    if (ivVoicePulse != null) {
                        ivVoicePulse.setScaleX(1.0f);
                        ivVoicePulse.setScaleY(1.0f);
                    }
                    if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                        if (tvVoiceStatus != null) tvVoiceStatus.setText("Didn't catch that. Tap mic to retry.");
                        mainHandler.postDelayed(() -> stopVoiceListening(), 1200);
                    } else {
                        // Fallback to system voice dialog
                        stopVoiceListening();
                        launchVoiceFallbackIntent();
                    }
                }

                @Override
                public void onResults(Bundle results) {
                    stopVoiceListening();
                    if (results != null) {
                        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (matches != null && !matches.isEmpty()) {
                            onVoiceInputReceived(matches.get(0));
                        }
                    }
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                    if (partialResults != null && tvVoiceStatus != null) {
                        ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                        if (partial != null && !partial.isEmpty()) {
                            tvVoiceStatus.setText(partial.get(0));
                        }
                    }
                }

                @Override
                public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void onVoiceInputReceived(String spokenText) {
        if (spokenText == null || spokenText.trim().isEmpty()) return;
        String text = spokenText.trim();
        if (etMessage != null) etMessage.setText(text);
        isLastInputVoice = true; // Flag so DEX speaks response aloud
        dispatchUserMessage(text);
    }

    private void startVoiceListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }

        // Stop TTS if speaking
        stopTts();

        isListening = true;
        if (layoutVoiceActive != null) layoutVoiceActive.setVisibility(View.VISIBLE);
        if (tvVoiceStatus != null) tvVoiceStatus.setText("Initializing mic...");
        if (btnChatMic != null) btnChatMic.setImageTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.status_online)));

        if (speechRecognizer != null && SpeechRecognizer.isRecognitionAvailable(this)) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            try {
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                launchVoiceFallbackIntent();
            }
        } else {
            launchVoiceFallbackIntent();
        }
    }

    private void stopVoiceListening() {
        isListening = false;
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) {}
        }
        if (layoutVoiceActive != null) layoutVoiceActive.setVisibility(View.GONE);
        if (btnChatMic != null) btnChatMic.setImageTintList(null);
        if (ivVoicePulse != null) {
            ivVoicePulse.setScaleX(1.0f);
            ivVoicePulse.setScaleY(1.0f);
        }
    }

    private void toggleVoiceListening() {
        if (isListening) {
            stopVoiceListening();
        } else {
            startVoiceListening();
        }
    }

    private void launchVoiceFallbackIntent() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to DEX...");
            voiceFallbackLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Voice recognition not available on this device.", Toast.LENGTH_SHORT).show();
            stopVoiceListening();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceListening();
            } else {
                Toast.makeText(this, "Microphone permission is required for Voice Chat.", Toast.LENGTH_SHORT).show();
                stopVoiceListening();
            }
        }
    }

    private void speakAssistantReply(String text) {
        if (!isTtsInitialized || tts == null) return;
        if (!isTtsEnabled && !isLastInputVoice) return;

        String clean = cleanTextForSpeech(text);
        if (clean.isEmpty()) return;

        currentSpeakingText = clean;
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "DEX_REPLY");
    }

    private void stopTts() {
        if (tts != null) {
            try { tts.stop(); } catch (Exception ignored) {}
        }
        currentSpeakingText = null;
    }

    /**
     * Cleans assistant reply for smooth natural speech.
     * Skips code blocks, complex brackets, and URLs.
     */
    private String cleanTextForSpeech(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String text = raw;

        // Strip multi-step headers and PID markers for conversational speech
        text = text.replaceAll("=== Multi-Step Task: [^=]+ ===", "")
                   .replaceAll("\\(PID: \\d+\\)", "")
                   .replaceAll("\\[SUCCESS\\]", "Successfully completed: ")
                   .replaceAll("\\[FAILED\\]", "Failed: ")
                   .replaceAll("(?i)\\bresult:\\s*", "");

        // Replace code fences with pleasant speech note
        text = text.replaceAll("(?s)```.*?```", " I have generated the code snippet for you. ");

        // Remove URLs
        text = text.replaceAll("https?://\\S+", "link");

        // Clean markdown markers and symbols
        text = text.replaceAll("[*#`_~>•]", "");
        text = text.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");

        return text.trim().replaceAll("\\s+", " ");
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
        tvHomeUserName = findViewById(R.id.tvHomeUserName);
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
        tvExploreUserName = findViewById(R.id.tvExploreUserName);
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
        btnChatTtsToggle = findViewById(R.id.btnChatTtsToggle);
        chatScrollView = findViewById(R.id.chatScrollView);
        chatMessagesContainer = findViewById(R.id.chatMessagesContainer);

        // Voice Banner
        layoutVoiceActive = findViewById(R.id.layoutVoiceActive);
        ivVoicePulse = findViewById(R.id.ivVoicePulse);
        tvVoiceStatus = findViewById(R.id.tvVoiceStatus);
        btnCancelVoice = findViewById(R.id.btnCancelVoice);

        // Settings
        etSettingsUserName = findViewById(R.id.etSettingsUserName);
        btnSaveUserName = findViewById(R.id.btnSaveUserName);
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
        String defaultName = getString(R.string.user_name);
        String userName = prefs.getString(KEY_USER_NAME, defaultName);
        updateUserNameDisplay(userName);

        isTtsEnabled = prefs.getBoolean(KEY_TTS_ENABLED, true);
        updateTtsIcon();

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
        checkFirstTimeNameSetup();
    }

    private void checkFirstTimeNameSetup() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean firstTimeDone = prefs.getBoolean("first_launch_done", false);
        if (!firstTimeDone) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("What is your name?");

            final EditText input = new EditText(this);
            input.setHint("Your name");
            input.setText(prefs.getString(KEY_USER_NAME, ""));
            input.setSingleLine(true);
            int pad = dpToPx(20);
            FrameLayout container = new FrameLayout(this);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = pad;
            lp.rightMargin = pad;
            input.setLayoutParams(lp);
            container.addView(input);
            builder.setView(container);

            builder.setPositiveButton("Get Started", (dialog, which) -> {
                String entered = input.getText().toString().trim();
                if (!entered.isEmpty()) {
                    saveUserName(entered);
                }
                prefs.edit().putBoolean("first_launch_done", true).apply();
            });
            builder.setNegativeButton("Skip", (dialog, which) -> {
                prefs.edit().putBoolean("first_launch_done", true).apply();
            });
            builder.setCancelable(false);
            builder.show();
        }
    }

    private void updateTtsIcon() {
        if (btnChatTtsToggle != null) {
            int activeColor = ContextCompat.getColor(this, R.color.primary_maroon);
            int inactiveColor = ContextCompat.getColor(this, R.color.text_muted);
            btnChatTtsToggle.setImageTintList(ColorStateList.valueOf(isTtsEnabled ? activeColor : inactiveColor));
            btnChatTtsToggle.setAlpha(isTtsEnabled ? 1.0f : 0.45f);
        }
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
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putString(KEY_USER_NAME, trimmed);
        editor.apply();

        updateUserNameDisplay(trimmed);
        Toast.makeText(this, "Profile name saved: " + trimmed, Toast.LENGTH_SHORT).show();

        // Sync with DEX Desktop
        executor.execute(() -> {
            try {
                dexClient.updateProfile(trimmed);
            } catch (Exception ignored) {}
        });
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

        View profileAvatar = findViewById(R.id.btnProfileAvatar);
        if (profileAvatar != null) profileAvatar.setOnClickListener(v -> switchTab(3));

        View seeAllTopics = findViewById(R.id.btnHomeSeeAllTopics);
        if (seeAllTopics != null) seeAllTopics.setOnClickListener(v -> switchTab(2));
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

        // Voice Card: activates Voice Chat immediately
        cardHomeVoice.setOnClickListener(v -> {
            switchTab(1);
            mainHandler.postDelayed(() -> startVoiceListening(), 250);
        });

        cardHomeImage.setOnClickListener(v -> {
            switchTab(1);
            dispatchUserMessage("Capture desktop screen and analyze active window");
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

        // Home Search Bar Input
        if (etHomeSearch != null) {
            etHomeSearch.setOnEditorActionListener((v, actionId, event) -> {
                String query = etHomeSearch.getText().toString().trim();
                if (!query.isEmpty()) {
                    etHomeSearch.setText("");
                    switchTab(1);
                    dispatchUserMessage(query);
                    return true;
                }
                return false;
            });
        }

        // Home Mic Button
        btnHomeMic.setOnClickListener(v -> {
            switchTab(1);
            mainHandler.postDelayed(() -> startVoiceListening(), 250);
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
            dispatchUserMessage("Capture desktop screen and analyze active window");
        });

        btnExploreSend.setOnClickListener(v -> {
            String msg = etExploreMessage.getText().toString().trim();
            if (msg.isEmpty()) return;
            etExploreMessage.setText("");
            switchTab(1);
            dispatchUserMessage(msg);
        });

        btnExploreMic.setOnClickListener(v -> {
            switchTab(1);
            mainHandler.postDelayed(() -> startVoiceListening(), 250);
        });

        btnExploreMenu.setOnClickListener(v -> switchTab(3));
    }

    // ==================== SCREEN 3: CHAT ====================

    private void setupChatInteractions() {
        // Send button click
        btnSend.setOnClickListener(v -> sendCurrentChatMessage());

        // Keyboard Enter and IME Action Send handling
        etMessage.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND ||
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                sendCurrentChatMessage();
                return true;
            }
            return false;
        });

        etMessage.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN && !event.isShiftPressed()) {
                sendCurrentChatMessage();
                return true;
            }
            return false;
        });

        // Mic Button: triggers voice chat listening
        btnChatMic.setOnClickListener(v -> toggleVoiceListening());

        // Cancel Voice Button
        if (btnCancelVoice != null) {
            btnCancelVoice.setOnClickListener(v -> stopVoiceListening());
        }

        // Speaker TTS Toggle: turns voice spoken replies on/off
        if (btnChatTtsToggle != null) {
            btnChatTtsToggle.setOnClickListener(v -> {
                isTtsEnabled = !isTtsEnabled;
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putBoolean(KEY_TTS_ENABLED, isTtsEnabled).apply();
                updateTtsIcon();
                if (!isTtsEnabled) {
                    stopTts();
                    Toast.makeText(this, "Voice responses muted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Voice responses enabled", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnChatMenu.setOnClickListener(v -> switchTab(3));

        // Initial welcoming message if chat is fresh
        if (chatMessagesContainer != null && chatMessagesContainer.getChildCount() <= 1) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String userName = prefs.getString(KEY_USER_NAME, getString(R.string.user_name));
            String initialGreeting = "Hello " + userName + "! I am X, your personal AI assistant.\n\n"
                    + "I can launch PC applications, inspect your screen, write complete programs to disk, and open them in VS Code.\n\n"
                    + "Tap the mic below anytime to speak with me!";
            String initialTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
            addAssistantMessageBubble(initialGreeting, initialTime);
        }
    }

    private void sendCurrentChatMessage() {
        if (etMessage == null) return;
        Editable editable = etMessage.getText();
        if (editable == null) return;
        // Ensure any active IME composing word is committed so no text is lost
        BaseInputConnection.setComposingSpans(editable);
        String msg = editable.toString().trim();
        if (msg.isEmpty()) return;
        etMessage.setText("");
        isLastInputVoice = false; // Typed query
        dispatchUserMessage(msg);
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
                    speakAssistantReply(reply);
                    isLastInputVoice = false;
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    String replyTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
                    String err = "Error connecting to DEX: " + e.getMessage() + "\n\nTip: Ensure DEX is running on your Windows PC and both devices are on the same Wi-Fi.";
                    addAssistantMessageBubble(err, replyTime);
                    speakAssistantReply("I could not connect to your Windows PC. Please check your connection.");
                    isLastInputVoice = false;
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

        int maxWidth = (int) (getResources().getDisplayMetrics().widthPixels * 0.78);

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
        tvMsg.setMaxWidth(maxWidth);
        tvMsg.setSingleLine(false);
        tvMsg.setLineSpacing(dpToPx(2), 1.0f);
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

    private void addAssistantMessageBubble(String rawText, String time) {
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
        CharSequence formatted = formatAssistantMessage(rawText);
        tvText.setText(formatted);
        tvText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        tvText.setTextSize(14);
        tvText.setLineSpacing(dpToPx(3), 1.0f);
        card.addView(tvText);

        // Bottom Row with Timestamp, Speaker and Copy Button
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

        // Actions container (Speaker + Copy)
        LinearLayout actionsLayout = new LinearLayout(this);
        actionsLayout.setOrientation(LinearLayout.HORIZONTAL);
        actionsLayout.setGravity(Gravity.CENTER_VERTICAL);
        RelativeLayout.LayoutParams actionsParams = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        actionsParams.addRule(RelativeLayout.CENTER_VERTICAL);
        actionsLayout.setLayoutParams(actionsParams);

        // Speaker Button
        ImageView btnSpeak = new ImageView(this);
        btnSpeak.setImageResource(R.drawable.ic_speaker);
        LinearLayout.LayoutParams speakParams = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(20));
        speakParams.rightMargin = dpToPx(12);
        btnSpeak.setLayoutParams(speakParams);
        btnSpeak.setContentDescription("Listen Aloud");
        String speechClean = cleanTextForSpeech(rawText);
        btnSpeak.setOnClickListener(v -> {
            if (tts != null && tts.isSpeaking() && speechClean.equals(currentSpeakingText)) {
                stopTts();
                Toast.makeText(this, "Speech stopped", Toast.LENGTH_SHORT).show();
            } else {
                currentSpeakingText = speechClean;
                tts.speak(speechClean, TextToSpeech.QUEUE_FLUSH, null, "DEX_PLAY_BUBBLE");
                Toast.makeText(this, "Speaking aloud...", Toast.LENGTH_SHORT).show();
            }
        });
        actionsLayout.addView(btnSpeak);

        // Copy Button
        ImageView btnCopy = new ImageView(this);
        btnCopy.setImageResource(R.drawable.ic_copy);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(dpToPx(20), dpToPx(20));
        btnCopy.setLayoutParams(copyParams);
        btnCopy.setContentDescription("Copy");
        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("Copied", formatted.toString());
            clipboard.setPrimaryClip(clip);
            Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
        });
        actionsLayout.addView(btnCopy);

        bottomBar.addView(actionsLayout);

        card.addView(bottomBar);
        assistantRow.addView(card);

        chatMessagesContainer.addView(assistantRow);
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    /**
     * Cleans up raw JSON tool blocks, escaped newlines, and transforms markdown markers
     * into clean, readable typography without raw **, ##, or //.
     */
    private CharSequence formatAssistantMessage(String raw) {
        if (raw == null || raw.isEmpty()) return "";

        // 1. Strip raw tool invocation JSON blocks
        String text = raw.replaceAll("(?s)```(?:json)?\\s*\\{[^}]*\"tool\"[^}]*\\}\\s*```", "").trim();

        // 2. Unescape common JSON escape sequences
        text = text.replace("\\n", "\n")
                   .replace("\\\"", "\"")
                   .replace("\\\\", "\\")
                   .replaceAll("(?m)^//.*$", ""); // remove raw standalone comments

        // 3. Process line-by-line for headers and list bullets
        String[] lines = text.split("\n");
        StringBuilder cleanBuilder = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Strip markdown heading hashes: ### 1. Header -> 1. Header
            line = line.replaceAll("^#{1,6}\\s*", "");

            // Convert bullet markers: * or - at start of line -> •
            line = line.replaceAll("^[*-]\\s+", "• ");

            cleanBuilder.append(line);
            if (i < lines.length - 1) {
                cleanBuilder.append("\n");
            }
        }

        String processed = cleanBuilder.toString();

        // 4. Use SpannableStringBuilder to render bold **text** and `code` spans cleanly
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        Pattern pattern = Pattern.compile("(\\*\\*(.+?)\\*\\*)|(`([^`]+?)`)");
        Matcher matcher = pattern.matcher(processed);
        int lastEnd = 0;

        while (matcher.find()) {
            ssb.append(processed.substring(lastEnd, matcher.start()));
            if (matcher.group(1) != null) {
                // Bold **text**
                String boldText = matcher.group(2);
                int startSpan = ssb.length();
                ssb.append(boldText);
                ssb.setSpan(new StyleSpan(Typeface.BOLD), startSpan, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (matcher.group(3) != null) {
                // Inline `code`
                String codeText = matcher.group(4);
                int startSpan = ssb.length();
                ssb.append(codeText);
                ssb.setSpan(new TypefaceSpan("monospace"), startSpan, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < processed.length()) {
            ssb.append(processed.substring(lastEnd));
        }

        return ssb;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ==================== SCREEN 4: SETTINGS, REMOTE & VISION ====================

    private void setupSettingsAndRemote() {
        // User profile name save
        if (btnSaveUserName != null) {
            btnSaveUserName.setOnClickListener(v -> {
                String name = etSettingsUserName.getText().toString().trim();
                if (!name.isEmpty()) {
                    saveUserName(name);
                } else {
                    Toast.makeText(this, "Please enter your name", Toast.LENGTH_SHORT).show();
                }
            });
        }

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
                        testConnection(false);
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
                String userName = status.optString("userName", "");
                mainHandler.post(() -> {
                    setOnlineStatus(online);
                    if (!userName.isEmpty()) {
                        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                        editor.putString(KEY_USER_NAME, userName);
                        editor.apply();
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

    @Override
    protected void onPause() {
        super.onPause();
        stopVoiceListening();
        stopTts();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVoiceListening();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        executor.shutdown();
    }
}

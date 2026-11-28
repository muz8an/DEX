package com.dex.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Encapsulates Android SpeechRecognizer input, fallback voice intent, and TextToSpeech output.
 */
public class VoiceController {

    public static final int REQUEST_RECORD_AUDIO = 3001;

    public interface Callback {
        void onVoiceRecognized(String spokenText);
    }

    private final AppCompatActivity activity;
    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean ttsEnabled = true;
    private boolean lastInputVoice = false;
    private boolean listening = false;
    private String currentSpeakingText;
    private ActivityResultLauncher<Intent> fallbackLauncher;

    private View voiceBanner;
    private ImageView pulseView;
    private ImageView chatMicView;
    private TextView statusView;

    public VoiceController(AppCompatActivity activity, Callback callback) {
        this.activity = activity;
        this.callback = callback;
        initFallbackLauncher();
        initTts();
        initSpeechRecognizer();
    }

    public void bindUi(View voiceBanner, ImageView pulseView, TextView statusView,
                       View cancelVoiceBtn, ImageView chatMicView) {
        this.voiceBanner = voiceBanner;
        this.pulseView = pulseView;
        this.statusView = statusView;
        this.chatMicView = chatMicView;
        if (cancelVoiceBtn != null) {
            cancelVoiceBtn.setOnClickListener(v -> stopListening());
        }
    }

    public void setTtsEnabled(boolean enabled) {
        this.ttsEnabled = enabled;
        if (!enabled) {
            stopTts();
        }
    }

    public boolean isTtsEnabled() {
        return ttsEnabled;
    }

    public void markNextInputTyped() {
        this.lastInputVoice = false;
    }

    public void toggleListening() {
        if (listening) {
            stopListening();
        } else {
            startListening();
        }
    }

    public void startListening() {
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
            return;
        }
        stopTts();
        listening = true;
        if (voiceBanner != null) voiceBanner.setVisibility(View.VISIBLE);
        if (statusView != null) statusView.setText("Initializing mic...");
        if (chatMicView != null) {
            chatMicView.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(activity, R.color.status_online)));
        }

        if (speechRecognizer != null && SpeechRecognizer.isRecognitionAvailable(activity)) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            try {
                speechRecognizer.startListening(intent);
            } catch (Exception e) {
                launchFallbackIntent();
            }
        } else {
            launchFallbackIntent();
        }
    }

    public void stopListening() {
        listening = false;
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception ignored) {
            }
        }
        if (voiceBanner != null) voiceBanner.setVisibility(View.GONE);
        if (chatMicView != null) chatMicView.setImageTintList(null);
        resetPulse();
    }

    public void onPermissionResult(int requestCode, int[] grantResults) {
        if (requestCode != REQUEST_RECORD_AUDIO) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            Toast.makeText(activity, "Microphone permission is required for Voice Chat.", Toast.LENGTH_SHORT).show();
            stopListening();
        }
    }

    public void speakReply(String rawText) {
        boolean shouldSpeak = ttsEnabled || lastInputVoice;
        lastInputVoice = false;
        if (!shouldSpeak) return;
        speakNow(cleanTextForSpeech(rawText), "DEX_REPLY");
    }

    public void toggleBubbleSpeech(String rawText) {
        String clean = cleanTextForSpeech(rawText);
        if (clean.isEmpty()) return;
        if (tts != null && tts.isSpeaking() && clean.equals(currentSpeakingText)) {
            stopTts();
            Toast.makeText(activity, "Speech stopped", Toast.LENGTH_SHORT).show();
        } else {
            speakNow(clean, "DEX_PLAY_BUBBLE");
            Toast.makeText(activity, "Speaking aloud...", Toast.LENGTH_SHORT).show();
        }
    }

    public void stopTts() {
        if (tts != null) {
            try {
                tts.stop();
            } catch (Exception ignored) {
            }
        }
        currentSpeakingText = null;
    }

    public void destroy() {
        stopListening();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    /** Strips markdown syntax, tool traces, and URLs so TTS reads naturally. */
    public static String cleanTextForSpeech(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String text = raw
                .replaceAll("=== Multi-Step Task: [^=]+ ===", "")
                .replaceAll("\\(PID: \\d+\\)", "")
                .replaceAll("\\[SUCCESS\\]", "Successfully completed: ")
                .replaceAll("\\[FAILED\\]", "Failed: ")
                .replaceAll("(?i)\\bresult:\\s*", "")
                .replaceAll("(?s)```.*?```", " I have generated the code snippet for you. ")
                .replaceAll("https?://\\S+", "link")
                .replaceAll("[*#`_~>•]", "")
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
        return text.trim().replaceAll("\\s+", " ");
    }

    private void speakNow(String clean, String utteranceId) {
        if (!ttsReady || tts == null || clean == null || clean.isEmpty()) return;
        currentSpeakingText = clean;
        tts.speak(clean, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
    }

    private void initFallbackLauncher() {
        fallbackLauncher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    stopListening();
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        ArrayList<String> matches = result.getData()
                                .getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                        if (matches != null && !matches.isEmpty()) {
                            deliverRecognizedText(matches.get(0));
                        }
                    }
                }
        );
    }

    private void initTts() {
        tts = new TextToSpeech(activity, status -> {
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(Locale.getDefault());
                tts.setPitch(1.0f);
                tts.setSpeechRate(1.05f);
            }
        });
    }

    private void initSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) return;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(activity);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                if (statusView != null) statusView.setText("Listening... Speak now");
            }

            @Override
            public void onBeginningOfSpeech() {
                if (statusView != null) statusView.setText("Hearing you...");
            }

            @Override
            public void onRmsChanged(float rmsdB) {
                if (pulseView != null) {
                    float scale = 1.0f + Math.max(0f, rmsdB / 12f);
                    pulseView.setScaleX(scale);
                    pulseView.setScaleY(scale);
                }
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                if (statusView != null) statusView.setText("Processing speech...");
                resetPulse();
            }

            @Override
            public void onError(int error) {
                resetPulse();
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    if (statusView != null) statusView.setText("Didn't catch that. Tap mic to retry.");
                    mainHandler.postDelayed(VoiceController.this::stopListening, 1200);
                } else {
                    stopListening();
                    launchFallbackIntent();
                }
            }

            @Override
            public void onResults(Bundle results) {
                stopListening();
                if (results != null) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        deliverRecognizedText(matches.get(0));
                    }
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                if (partialResults != null && statusView != null) {
                    ArrayList<String> partial = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (partial != null && !partial.isEmpty()) {
                        statusView.setText(partial.get(0));
                    }
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void deliverRecognizedText(String spokenText) {
        if (spokenText == null || spokenText.trim().isEmpty()) return;
        lastInputVoice = true;
        callback.onVoiceRecognized(spokenText.trim());
    }

    private void launchFallbackIntent() {
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to DEX...");
            fallbackLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Voice recognition not available on this device.", Toast.LENGTH_SHORT).show();
            stopListening();
        }
    }

    private void resetPulse() {
        if (pulseView != null) {
            pulseView.setScaleX(1.0f);
            pulseView.setScaleY(1.0f);
        }
    }
}

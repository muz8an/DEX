package com.dex.voice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Voice Interaction Controller for DEX.
 * Provides in-app microphone listening, Windows speech recognition,
 * text-to-speech synthesis, and Windows Voice Typing (Win + H).
 */
public class VoiceController {

    private static Process activeSpeechProcess = null;
    private static Process activeListeningProcess = null;
    private static final AtomicBoolean isListening = new AtomicBoolean(false);
    private static final AtomicBoolean isSpeaking = new AtomicBoolean(false);

    private VoiceController() {}

    /**
     * Checks if a microphone input device is available on the system.
     */
    public static boolean isMicrophoneAvailable() {
        try {
            AudioFormat format = new AudioFormat(16000.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            return AudioSystem.isLineSupported(info);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Speaks text aloud using Windows PowerShell System.Speech.Synthesis.
     * Automatically halts any previous speech.
     */
    public static synchronized String speak(String text) {
        if (text == null || text.isBlank()) {
            return "No text provided to speak.";
        }

        stopSpeaking();

        // Sanitize text for PowerShell command line
        String cleanText = text.replaceAll("[\"'`]", " ").replaceAll("\\s+", " ").trim();
        if (cleanText.length() > 600) {
            cleanText = cleanText.substring(0, 600);
        }

        try {
            String psCommand = "Add-Type -AssemblyName System.Speech; "
                    + "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    + "$synth.Rate = 1; "
                    + "$synth.Speak('" + cleanText + "');";

            activeSpeechProcess = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCommand).start();
            isSpeaking.set(true);
            return "DEX Voice: Speaking response aloud.";
        } catch (IOException e) {
            return "Voice synthesis failed: " + e.getMessage();
        }
    }

    /**
     * Immediately interrupts and cancels any in-progress speech.
     */
    public static synchronized void stopSpeaking() {
        if (activeSpeechProcess != null && activeSpeechProcess.isAlive()) {
            try {
                activeSpeechProcess.destroyForcibly();
            } catch (Exception ignored) {
            }
            activeSpeechProcess = null;
        }
        isSpeaking.set(false);
    }

    /**
     * Listens to the computer's microphone and transcribes speech using
     * Windows native System.Speech.Recognition engine.
     * Synchronous blocking call up to timeoutSeconds. Call from a background SwingWorker or thread.
     */
    public static String listenFromMicrophone(int timeoutSeconds) {
        cancelListening();
        isListening.set(true);

        StringBuilder recognized = new StringBuilder();
        try {
            String psScript = "Add-Type -AssemblyName System.Speech; "
                    + "$engine = New-Object System.Speech.Recognition.SpeechRecognitionEngine; "
                    + "$dictation = New-Object System.Speech.Recognition.DictationGrammar; "
                    + "$engine.LoadGrammar($dictation); "
                    + "$engine.SetInputToDefaultAudioDevice(); "
                    + "$res = $engine.Recognize([TimeSpan]::FromSeconds(" + timeoutSeconds + ")); "
                    + "if ($res -and $res.Text) { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8; Write-Output $res.Text }";

            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psScript);
            synchronized (VoiceController.class) {
                activeListeningProcess = pb.start();
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(activeListeningProcess.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) {
                        recognized.append(line.trim()).append(" ");
                    }
                }
            }

            activeListeningProcess.waitFor();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Error during recognition
        } finally {
            isListening.set(false);
            synchronized (VoiceController.class) {
                activeListeningProcess = null;
            }
        }

        return recognized.toString().trim();
    }

    /**
     * Immediately cancels active microphone listening.
     */
    public static synchronized void cancelListening() {
        if (activeListeningProcess != null && activeListeningProcess.isAlive()) {
            try {
                activeListeningProcess.destroyForcibly();
            } catch (Exception ignored) {
            }
            activeListeningProcess = null;
        }
        isListening.set(false);
    }

    /**
     * Activates Windows native Voice Typing via Virtual Keystroke (Win + H).
     */
    public static String triggerWindowsVoiceTyping() {
        try {
            String triggerScript = "Add-Type -MemberDefinition '[DllImport(\"user32.dll\")] public static extern void keybd_event(byte bVk, byte bScan, uint dwFlags, int dwExtraInfo);' -Name Win32Key -Namespace Win32; "
                    + "[Win32.Win32Key]::keybd_event(0x5B, 0, 0, 0); "  // Win down
                    + "[Win32.Win32Key]::keybd_event(0x48, 0, 0, 0); "  // H down
                    + "[Win32.Win32Key]::keybd_event(0x48, 0, 2, 0); "  // H up
                    + "[Win32.Win32Key]::keybd_event(0x5B, 0, 2, 0);"; // Win up

            new ProcessBuilder("powershell", "-NoProfile", "-Command", triggerScript).start();
            isListening.set(true);
            return "Windows Voice Typing activated (Win + H). Speak clearly into your microphone.";
        } catch (Exception e) {
            return "Could not activate Voice Typing: " + e.getMessage();
        }
    }

    public static boolean isListening() {
        return isListening.get();
    }

    public static void setListening(boolean listening) {
        isListening.set(listening);
    }

    public static boolean isSpeaking() {
        return isSpeaking.get();
    }
}

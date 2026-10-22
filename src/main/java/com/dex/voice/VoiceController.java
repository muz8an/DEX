package com.dex.voice;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Voice Interaction Controller for DEX.
 * Manages Windows text-to-speech with cancellation, and provides hooks
 * for Windows Voice Typing (Win + H) and microphone input.
 */
public class VoiceController {

    private static Process activeSpeechProcess = null;
    private static final AtomicBoolean isListening = new AtomicBoolean(false);

    private VoiceController() {}

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
        if (cleanText.length() > 500) {
            cleanText = cleanText.substring(0, 500);
        }

        try {
            String psCommand = "Add-Type -AssemblyName System.Speech; "
                    + "$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                    + "$synth.Rate = 1; "
                    + "$synth.Speak('" + cleanText + "');";

            activeSpeechProcess = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", psCommand).start();
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
    }

    /**
     * Activates Windows 11 / Windows 10 native Voice Typing via Virtual Keystroke (Win + H).
     */
    public static String triggerWindowsVoiceTyping() {
        try {
            // Invokes PowerShell to trigger Win + H key combination
            String script = "$wshell = New-Object -ComObject wscript.shell; "
                    + "$wshell.SendKeys('^{ESC}'); Start-Sleep -Milliseconds 100; "
                    + "$wshell.SendKeys('^{ESC}');";

            // Direct PowerShell script to trigger Voice Typing UI
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
}

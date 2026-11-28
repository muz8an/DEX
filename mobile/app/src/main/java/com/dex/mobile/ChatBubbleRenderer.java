package com.dex.mobile;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders formatted user and DEX assistant chat bubbles inside the conversation timeline.
 */
public class ChatBubbleRenderer {

    public interface SpeakHandler {
        void onToggleSpeak(String rawMessage);
    }

    private final Context context;
    private final LinearLayout container;
    private final ScrollView scrollView;
    private final SpeakHandler speakHandler;

    public ChatBubbleRenderer(Context context, LinearLayout container,
                              ScrollView scrollView, SpeakHandler speakHandler) {
        this.context = context;
        this.container = container;
        this.scrollView = scrollView;
        this.speakHandler = speakHandler;
    }

    public static String nowTimeLabel() {
        return new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
    }

    public void addUserBubble(String text, String time) {
        LinearLayout bubble = new LinearLayout(context);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setBackgroundResource(R.drawable.bg_chat_user);
        int padH = dp(16);
        int padV = dp(12);
        bubble.setPadding(padH, padV, padH, padV);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END;
        params.topMargin = dp(12);
        bubble.setLayoutParams(params);

        TextView tvMsg = new TextView(context);
        tvMsg.setText(text);
        tvMsg.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        tvMsg.setTextSize(15);
        tvMsg.setMaxWidth((int) (context.getResources().getDisplayMetrics().widthPixels * 0.78));
        tvMsg.setLineSpacing(dp(2), 1.0f);
        bubble.addView(tvMsg);

        LinearLayout timeRow = new LinearLayout(context);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        timeRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        LinearLayout.LayoutParams trp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        trp.gravity = Gravity.END;
        trp.topMargin = dp(4);
        timeRow.setLayoutParams(trp);

        TextView tvTime = new TextView(context);
        tvTime.setText(time);
        tvTime.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        tvTime.setTextSize(11);
        timeRow.addView(tvTime);

        ImageView ivCheck = new ImageView(context);
        ivCheck.setImageResource(R.drawable.ic_check_double);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(14), dp(14));
        cp.leftMargin = dp(4);
        ivCheck.setLayoutParams(cp);
        timeRow.addView(ivCheck);

        bubble.addView(timeRow);
        container.addView(bubble);
        scrollToBottom();
    }

    public void addAssistantBubble(String rawText, String time) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = dp(16);
        row.setLayoutParams(rowParams);

        ImageView ivLogo = new ImageView(context);
        ivLogo.setImageResource(R.drawable.ic_assistant_logo);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        logoParams.topMargin = dp(4);
        ivLogo.setLayoutParams(logoParams);
        row.addView(ivLogo);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_chat_assistant);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        cardParams.leftMargin = dp(10);
        card.setLayoutParams(cardParams);

        CharSequence formatted = formatAssistantMessage(rawText);
        TextView tvText = new TextView(context);
        tvText.setText(formatted);
        tvText.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        tvText.setTextSize(14);
        tvText.setLineSpacing(dp(3), 1.0f);
        card.addView(tvText);

        card.addView(buildFooterBar(time, rawText, formatted));
        row.addView(card);

        container.addView(row);
        scrollToBottom();
    }

    public void clear() {
        container.removeAllViews();
    }

    /** Strips raw tool invocation fences, unescapes newlines/quotes, and normalizes headings/bullets. */
    public static String cleanMarkdownPlainText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String text = raw.replaceAll("(?s)```(?:json)?\\s*\\{[^}]*\"tool\"[^}]*\\}\\s*```", "").trim();
        text = text.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replaceAll("(?m)^//.*$", "");

        String[] lines = text.split("\n");
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i]
                    .replaceAll("^#{1,6}\\s*", "")
                    .replaceAll("^[*-]\\s+", "• ");
            out.append(line);
            if (i < lines.length - 1) out.append("\n");
        }
        return out.toString();
    }

    private CharSequence formatAssistantMessage(String raw) {
        String processed = cleanMarkdownPlainText(raw);
        if (processed.isEmpty()) return "";

        SpannableStringBuilder ssb = new SpannableStringBuilder();
        Pattern pattern = Pattern.compile("(\\*\\*(.+?)\\*\\*)|(`([^`]+?)`)");
        Matcher matcher = pattern.matcher(processed);
        int lastEnd = 0;

        while (matcher.find()) {
            ssb.append(processed.substring(lastEnd, matcher.start()));
            if (matcher.group(1) != null) {
                int startSpan = ssb.length();
                ssb.append(matcher.group(2));
                ssb.setSpan(new StyleSpan(Typeface.BOLD), startSpan, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else if (matcher.group(3) != null) {
                int startSpan = ssb.length();
                ssb.append(matcher.group(4));
                ssb.setSpan(new TypefaceSpan("monospace"), startSpan, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            lastEnd = matcher.end();
        }
        if (lastEnd < processed.length()) {
            ssb.append(processed.substring(lastEnd));
        }
        return ssb;
    }

    private RelativeLayout buildFooterBar(String time, String rawText, CharSequence formatted) {
        RelativeLayout bottomBar = new RelativeLayout(context);
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.topMargin = dp(10);
        bottomBar.setLayoutParams(barParams);

        TextView tvTime = new TextView(context);
        tvTime.setText(time);
        tvTime.setTextColor(ContextCompat.getColor(context, R.color.text_muted));
        tvTime.setTextSize(11);
        RelativeLayout.LayoutParams tp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.addRule(RelativeLayout.ALIGN_PARENT_START);
        tp.addRule(RelativeLayout.CENTER_VERTICAL);
        tvTime.setLayoutParams(tp);
        bottomBar.addView(tvTime);

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        RelativeLayout.LayoutParams ap = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ap.addRule(RelativeLayout.ALIGN_PARENT_END);
        ap.addRule(RelativeLayout.CENTER_VERTICAL);
        actions.setLayoutParams(ap);

        ImageView btnSpeak = new ImageView(context);
        btnSpeak.setImageResource(R.drawable.ic_speaker);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(20), dp(20));
        sp.rightMargin = dp(12);
        btnSpeak.setLayoutParams(sp);
        btnSpeak.setContentDescription("Listen Aloud");
        btnSpeak.setOnClickListener(v -> {
            if (speakHandler != null) speakHandler.onToggleSpeak(rawText);
        });
        actions.addView(btnSpeak);

        ImageView btnCopy = new ImageView(context);
        btnCopy.setImageResource(R.drawable.ic_copy);
        btnCopy.setLayoutParams(new LinearLayout.LayoutParams(dp(20), dp(20)));
        btnCopy.setContentDescription("Copy");
        btnCopy.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("DEX Reply", formatted.toString()));
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
        actions.addView(btnCopy);

        bottomBar.addView(actions);
        return bottomBar;
    }

    private void scrollToBottom() {
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private int dp(int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}

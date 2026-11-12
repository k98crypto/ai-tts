package com.storyvoice.overlaydemo;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

public final class ReplyAccessibilityService extends AccessibilityService {
    private static final String TARGET_PACKAGE = "com.deepseek.chat";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Object END_OF_AUDIO = new Object();

    private final ExecutorService synthIo = Executors.newSingleThreadExecutor();
    private final ExecutorService playbackIo = Executors.newSingleThreadExecutor();
    private WindowManager windowManager;
    private View overlay;
    private TextView status;
    private String lastCandidate = "";
    private String targetPackage = TARGET_PACKAGE;
    private boolean deepSeekForeground;
    private MediaPlayer player;
    private File currentAudioFile;
    private volatile CountDownLatch activeSegmentLatch;
    private long lastCaptureAt;
    private volatile int synthesisRunId;

    public static boolean isEnabled(android.content.Context context) {
        String enabledServices = android.provider.Settings.Secure.getString(
                context.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(context.getPackageName() + "/" + ReplyAccessibilityService.class.getName());
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        File[] cachedAudio = getCacheDir().listFiles((dir, name) -> name.startsWith("reply-") && name.endsWith(".wav"));
        if (cachedAudio != null) {
            for (File file : cachedAudio) file.delete();
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        String eventPackage = event.getPackageName().toString();

        if (!TARGET_PACKAGE.equals(eventPackage)) {
            // Other apps contribute only a package transition so the overlay can be dismissed.
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && !getPackageName().equals(eventPackage)) {
                deepSeekForeground = false;
                lastCandidate = "";
                hideOverlay();
            }
            return;
        }

        deepSeekForeground = true;
        showOverlayIfAllowed();

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                || event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            AccessibilityNodeInfo source = event.getSource();
            if (source != null && source.getPackageName() != null
                    && targetPackage.equals(source.getPackageName().toString())) {
                captureVisibleReply(findTextContainer(source));
            }
        }
    }

    @Override
    public void onInterrupt() {
        synthesisRunId++;
        stopPlayback();
        hideOverlay();
    }

    @Override
    public void onDestroy() {
        synthesisRunId++;
        stopPlayback();
        hideOverlay();
        synthIo.shutdownNow();
        playbackIo.shutdownNow();
        File[] cachedAudio = getCacheDir().listFiles((dir, name) -> name.startsWith("reply-") && name.endsWith(".wav"));
        if (cachedAudio != null) {
            for (File file : cachedAudio) file.delete();
        }
        super.onDestroy();
    }

    private void captureVisibleReply(AccessibilityNodeInfo root) {
        long now = System.currentTimeMillis();
        if (now - lastCaptureAt < 300) return;
        lastCaptureAt = now;
        if (root.getPackageName() == null || !targetPackage.equals(root.getPackageName().toString())) return;

        ArrayList<String> texts = new ArrayList<>();
        collectText(root, texts, 0);
        Collections.reverse(texts);
        for (String value : texts) {
            String candidate = value.trim();
            if (candidate.length() >= 24 && !candidate.equals(lastCandidate)) {
                lastCandidate = candidate;
                updateOverlayStatus("已识别 · " + candidate.length() + " 字");
                return;
            }
        }
    }

    private AccessibilityNodeInfo findTextContainer(AccessibilityNodeInfo source) {
        AccessibilityNodeInfo node = source;
        for (int depth = 0; node != null && depth < 12; depth++) {
            if (node.getPackageName() == null || !targetPackage.equals(node.getPackageName().toString())) break;
            CharSequence text = node.getText();
            if (!node.isEditable() && !node.isPassword() && text != null && text.length() >= 24) return node;
            node = node.getParent();
        }
        return source;
    }

    private void collectText(AccessibilityNodeInfo node, List<String> out, int depth) {
        if (node == null || depth > 30) return;
        if (node.getPackageName() == null || !targetPackage.equals(node.getPackageName().toString())) return;
        if (node.isEditable() || node.isPassword()) return;
        CharSequence text = node.getText();
        if (text != null && text.length() >= 24) {
            String value = text.toString().trim();
            if (!value.isEmpty() && !out.contains(value)) out.add(value);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectText(node.getChild(i), out, depth + 1);
        }
    }

    private void showOverlayIfAllowed() {
        if (!Settings.canDrawOverlays(this) || overlay != null || !deepSeekForeground) return;
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(10), dp(7), dp(10), dp(7));
        shell.setBackgroundColor(0xF51B2926);

        status = new TextView(this);
        status.setText(lastCandidate.isEmpty() ? "等待 DeepSeek 回复" : "已识别 · " + lastCandidate.length() + " 字");
        status.setTextColor(0xFFE5F2EE);
        status.setTextSize(10);
        shell.addView(status);

        Button play = new Button(this);
        play.setText("检查并配音");
        play.setAllCaps(false);
        play.setTextSize(13);
        play.setMinHeight(dp(38));
        play.setOnClickListener(v -> confirmAndSynthesize());
        shell.addView(play);

        Button close = new Button(this);
        close.setText("关闭浮窗");
        close.setAllCaps(false);
        close.setTextSize(11);
        close.setOnClickListener(v -> {
            deepSeekForeground = false;
            hideOverlay();
        });
        shell.addView(close);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                dp(154), WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(10);
        params.y = dp(220);
        overlay = shell;
        try {
            windowManager.addView(overlay, params);
        } catch (RuntimeException e) {
            overlay = null;
        }
    }

    private void confirmAndSynthesize() {
        if (lastCandidate.isEmpty()) {
            updateOverlayStatus("未识别到回复文本");
            return;
        }
        String candidate = lastCandidate;
        LinearLayout confirm = new LinearLayout(this);
        confirm.setOrientation(LinearLayout.VERTICAL);
        confirm.setPadding(dp(12), dp(10), dp(12), dp(10));
        confirm.setBackgroundColor(0xF51B2926);
        TextView preview = new TextView(this);
        preview.setText(candidate.length() > 520 ? candidate.substring(0, 520) + "…" : candidate);
        preview.setTextColor(0xFFFFFFFF);
        preview.setTextSize(12);
        confirm.addView(preview);
        Button submit = new Button(this);
        submit.setText("确认生成并播放");
        submit.setAllCaps(false);
        submit.setOnClickListener(v -> synthesize(candidate));
        confirm.addView(submit);
        Button cancel = new Button(this);
        cancel.setText("返回");
        cancel.setAllCaps(false);
        cancel.setOnClickListener(v -> replaceOverlayWithControls());
        confirm.addView(cancel);
        status = new TextView(this);
        status.setText("请先核对文本；确认后才会发送给 TTS 服务器");
        status.setTextColor(0xFFE5F2EE);
        status.setTextSize(10);
        confirm.addView(status);
        replaceOverlay(confirm);
    }

    private void replaceOverlayWithControls() {
        hideOverlay();
        showOverlayIfAllowed();
    }

    private void replaceOverlay(View view) {
        if (overlay == null) return;
        WindowManager.LayoutParams params = (WindowManager.LayoutParams) overlay.getLayoutParams();
        try {
            windowManager.removeView(overlay);
            overlay = view;
            windowManager.addView(overlay, params);
        } catch (RuntimeException ignored) {
            overlay = null;
        }
    }

    private void synthesize(String text) {
        int runId = ++synthesisRunId;
        stopPlayback();
        updateOverlayStatus("正在连接 TTS…");
        LinkedBlockingQueue<Object> ready = new LinkedBlockingQueue<>(2);
        List<String> parts = splitForSpeech(text, 36);
        String base = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
                .getString(MainActivity.KEY_SERVER, "http://192.168.10.10:8766");

        synthIo.execute(() -> {
            try {
                for (int i = 0; i < parts.size() && runId == synthesisRunId; i++) {
                    File file = requestAudio(base, parts.get(i), i);
                    if (runId != synthesisRunId) {
                        file.delete();
                        break;
                    }
                    ready.put(file);
                    MAIN.post(() -> updateOverlayStatus("已生成首句，继续准备后续片段…"));
                }
            } catch (Exception e) {
                MAIN.post(() -> updateOverlayStatus("合成失败：" + e.getMessage()));
            } finally {
                ready.offer(END_OF_AUDIO);
            }
        });

        playbackIo.execute(() -> {
            try {
                while (runId == synthesisRunId) {
                    Object item = ready.take();
                    if (item == END_OF_AUDIO) break;
                    CountDownLatch completed = new CountDownLatch(1);
                    activeSegmentLatch = completed;
                    File file = (File) item;
                    MAIN.post(() -> playFile(file, () -> {
                        file.delete();
                        completed.countDown();
                    }));
                    completed.await();
                }
                MAIN.post(() -> updateOverlayStatus("播放完成"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private File requestAudio(String base, String text, int segment) throws Exception {
        HttpURLConnection connection = null;
        try {
            JSONObject payload = new JSONObject();
            payload.put("text", text);
            payload.put("speaker", "Dylan");
            payload.put("emotion", "neutral");
            payload.put("direction", "");

            connection = (HttpURLConnection) new URL(base + "/synthesize").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(120000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
            try (java.io.OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("TTS HTTP " + code);

            ByteArrayOutputStream audio = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            try (java.io.InputStream input = connection.getInputStream()) {
                while ((count = input.read(buffer)) != -1) audio.write(buffer, 0, count);
            }
            File file = new File(getCacheDir(), "reply-" + segment + ".wav");
            try (FileOutputStream output = new FileOutputStream(file)) {
                audio.writeTo(output);
            }
            return file;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private List<String> splitForSpeech(String text, int limit) {
        ArrayList<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) continue;
            current.append(ch);
            boolean punctuation = "。！？!?；;\n".indexOf(ch) >= 0;
            if (punctuation || current.length() >= limit) {
                addSpeechPart(parts, current);
            }
        }
        addSpeechPart(parts, current);
        return parts;
    }

    private void addSpeechPart(List<String> parts, StringBuilder current) {
        String value = current.toString().trim();
        if (!value.isEmpty()) parts.add(value);
        current.setLength(0);
    }

    private void playFile(File file, Runnable onComplete) {
        stopPlayback();
        try {
            currentAudioFile = file;
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build());
            player.setDataSource(file.getAbsolutePath());
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnCompletionListener(mp -> {
                stopPlayback();
                onComplete.run();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                updateOverlayStatus("音频播放失败");
                stopPlayback();
                onComplete.run();
                return true;
            });
            player.prepareAsync();
            updateOverlayStatus("音频已生成，准备播放");
        } catch (Exception e) {
            updateOverlayStatus("播放失败：" + e.getMessage());
            stopPlayback();
            onComplete.run();
        }
    }

    private void stopPlayback() {
        CountDownLatch latch = activeSegmentLatch;
        activeSegmentLatch = null;
        if (latch != null) latch.countDown();
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
                player.release();
            } catch (RuntimeException ignored) {
            }
            player = null;
        }
        if (currentAudioFile != null) currentAudioFile.delete();
        currentAudioFile = null;
    }

    private void updateOverlayStatus(String text) {
        MAIN.post(() -> {
            if (status != null) status.setText(text);
        });
    }

    private void hideOverlay() {
        if (overlay == null || windowManager == null) return;
        try {
            windowManager.removeView(overlay);
        } catch (RuntimeException ignored) {
        }
        overlay = null;
        status = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}

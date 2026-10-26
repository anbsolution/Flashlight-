package com.anb.flashlight;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private CameraManager cameraManager;
    private String cameraId;
    private boolean torchOn = false;
    private int strength = 100;
    private int maxStrength = 1;
    private int defaultStrength = 1;
    private TextView status, percent, ledButton;
    private SeekBar slider;
    private View screenFlash;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AnimatorSet pulseAnimator;
    private boolean strobeRunning = false;
    private Runnable strobeTask;

    private int dp(float value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraManager = (CameraManager)getSystemService(CAMERA_SERVICE);
        buildUi();
        findFlashCamera();
    }

    private void buildUi() {
        final int bg = Color.rgb(5, 12, 27);
        final int panel = Color.rgb(10, 25, 43);
        final int blue = Color.rgb(44, 190, 255);
        final int lime = Color.rgb(163, 230, 53);
        final int muted = Color.rgb(177, 198, 220);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(bg);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(26));
        root.setBackgroundColor(bg);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("ANB", 22, Color.WHITE, true);
        toolbar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button pro = button("✦ ULTRA");
        pro.setTextColor(Color.rgb(255, 202, 74));
        toolbar.addView(pro, new LinearLayout.LayoutParams(-2, dp(42)));
        Button settings = button("⚙");
        settings.setTextSize(20);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(48), dp(46));
        sp.leftMargin = dp(8);
        toolbar.addView(settings, sp);
        settings.setOnClickListener(v -> showSettings());
        root.addView(toolbar);

        TextView title = text("FLASHLIGHT ULTRA", 25, Color.WHITE, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(14);
        root.addView(title, tp);
        TextView subtitle = text("PRO LIGHT CONTROL  •  SMART HARDWARE DETECTION", 10, blue, true);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subp = new LinearLayout.LayoutParams(-1, -2);
        subp.topMargin = dp(5);
        subp.bottomMargin = dp(18);
        root.addView(subtitle, subp);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER);
        hero.setPadding(dp(12), dp(20), dp(12), dp(18));
        hero.setBackground(roundBg(panel, dp(26)));
        hero.setElevation(dp(4));

        ledButton = text("⚡", 56, lime, true);
        ledButton.setGravity(Gravity.CENTER);
        ledButton.setBackground(roundBg(Color.rgb(13, 39, 57), dp(100)));
        ledButton.setElevation(dp(8));
        LinearLayout.LayoutParams orb = new LinearLayout.LayoutParams(dp(142), dp(142));
        orb.bottomMargin = dp(12);
        hero.addView(ledButton, orb);
        ledButton.setOnClickListener(v -> toggleTorch());

        TextView heroLabel = text("TAP TO TOGGLE LED TORCH", 11, muted, true);
        heroLabel.setGravity(Gravity.CENTER);
        hero.addView(heroLabel);

        status = text("Checking LED hardware…", 13, blue, false);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusP = new LinearLayout.LayoutParams(-1, -2);
        statusP.topMargin = dp(12);
        hero.addView(status, statusP);
        root.addView(hero);

        LinearLayout lightChoices = new LinearLayout(this);
        lightChoices.setOrientation(LinearLayout.HORIZONTAL);
        lightChoices.setPadding(0, dp(12), 0, dp(8));
        Button led = button("●  LED Torch");
        Button screen = button("▣  Screen Light");
        led.setTextColor(lime);
        screen.setTextColor(blue);
        led.setOnClickListener(v -> toggleTorch());
        screen.setOnClickListener(v -> showScreenFlash());
        lightChoices.addView(led, weighted());
        lightChoices.addView(screen, weighted());
        root.addView(lightChoices);

        LinearLayout sliderCard = new LinearLayout(this);
        sliderCard.setOrientation(LinearLayout.VERTICAL);
        sliderCard.setPadding(dp(16), dp(15), dp(16), dp(14));
        sliderCard.setBackground(roundBg(panel, dp(22)));

        LinearLayout sliderHeader = new LinearLayout(this);
        sliderHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView bolt = text("ϟ", 25, lime, true);
        sliderHeader.addView(bolt);
        percent = text("LED request: 100%", 17, Color.WHITE, true);
        LinearLayout.LayoutParams percentP = new LinearLayout.LayoutParams(0, -2, 1);
        percentP.leftMargin = dp(8);
        sliderHeader.addView(percent, percentP);
        TextView maxTag = text("MAX", 12, Color.rgb(255, 92, 166), true);
        sliderHeader.addView(maxTag);
        sliderCard.addView(sliderHeader);

        slider = new SeekBar(this);
        slider.setMax(200);
        slider.setProgress(100);
        if (Build.VERSION.SDK_INT >= 26) {
            slider.setProgressTintList(android.content.res.ColorStateList.valueOf(lime));
            slider.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        }
        LinearLayout.LayoutParams seekP = new LinearLayout.LayoutParams(-1, -2);
        seekP.topMargin = dp(8);
        sliderCard.addView(slider, seekP);

        LinearLayout marks = new LinearLayout(this);
        marks.setOrientation(LinearLayout.HORIZONTAL);
        String[] labels = {"0%", "25%", "50%", "100%", "150%", "200%"};
        for (String label : labels) {
            TextView mark = text(label, 10, muted, false);
            mark.setGravity(Gravity.CENTER);
            marks.addView(mark, new LinearLayout.LayoutParams(0, -2, 1));
        }
        sliderCard.addView(marks);

        TextView note = text("Hardware-aware control • Actual LED output depends on your phone. Android 12 typically supports LED ON/OFF only; unsupported boost levels do not increase physical brightness.", 11, muted, false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams noteP = new LinearLayout.LayoutParams(-1, -2);
        noteP.topMargin = dp(10);
        sliderCard.addView(note, noteP);
        root.addView(sliderCard);

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                strength = progress;
                percent.setText("LED request: " + strength + "%");
                if (!fromUser) return;
                boolean adjustable = Build.VERSION.SDK_INT >= 33 && maxStrength > defaultStrength;
                if (strength == 0) {
                    turnTorchOff();
                } else if (torchOn) {
                    applyStrength();
                } else if (!adjustable && strength > 100) {
                    status.setText("Boost unsupported by this phone • normal LED output only");
                } else if (!adjustable && strength < 100) {
                    status.setText("LED is ON/OFF only • this value does not physically dim the LED");
                } else {
                    status.setText("Selected " + strength + "% request • tap LED to switch on");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        TextView modesTitle = text("QUICK MODES", 15, Color.WHITE, true);
        LinearLayout.LayoutParams mt = new LinearLayout.LayoutParams(-1, -2);
        mt.topMargin = dp(18);
        mt.bottomMargin = dp(8);
        root.addView(modesTitle, mt);

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        String[] modeNames = {"Normal", "High", "Ultra", "Max"};
        String[] modeValues = {"100", "125", "150", "200"};
        int[] modeColors = {Color.rgb(44, 190, 255), Color.rgb(161, 91, 255),
                Color.rgb(255, 55, 143), Color.rgb(255, 178, 55)};
        for (int i = 0; i < modeNames.length; i++) {
            final int value = Integer.parseInt(modeValues[i]);
            Button mode = button(modeNames[i]);
            mode.setTextSize(12);
            mode.setTextColor(modeColors[i]);
            final int target = value;
            mode.setOnClickListener(v -> {
                strength = target;
                slider.setProgress(target);
                percent.setText("LED request: " + target + "%");
                if (target > 100 && !(Build.VERSION.SDK_INT >= 33 && maxStrength > defaultStrength)) {
                    status.setText("Mode selected • hardware boost unavailable; LED remains at normal output");
                } else {
                    status.setText(modeNamesForValue(target) + " mode selected");
                    if (torchOn) applyStrength();
                }
            });
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(0, dp(48), 1);
            mp.setMargins(dp(3), 0, dp(3), 0);
            modes.addView(mode, mp);
        }
        root.addView(modes);

        LinearLayout utilityRow = new LinearLayout(this);
        utilityRow.setOrientation(LinearLayout.HORIZONTAL);
        utilityRow.setPadding(0, dp(8), 0, 0);
        Button sos = button("⚠ SOS");
        Button strobe = button("〰 Strobe");
        sos.setTextColor(Color.rgb(255, 88, 105));
        strobe.setTextColor(Color.rgb(170, 115, 255));
        sos.setOnClickListener(v -> startSos());
        strobe.setOnClickListener(v -> toggleStrobe());
        utilityRow.addView(sos, weighted());
        utilityRow.addView(strobe, weighted());
        root.addView(utilityRow);

        TextView footer = text("ANB FLASHLIGHT ULTRA  •  Safety first — stop if your phone gets hot", 10, Color.GRAY, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(22);
        root.addView(footer, fp);

        scroll.addView(root);
        setContentView(scroll);
        animateEntrance(toolbar, 0);
        animateEntrance(title, 70);
        animateEntrance(hero, 130);
        animateEntrance(lightChoices, 190);
        animateEntrance(sliderCard, 250);
        animateEntrance(modes, 310);
        animateEntrance(utilityRow, 370);
    }

    private void animateEntrance(View view, long delay) {
        view.setAlpha(0f);
        view.setTranslationY(dp(10));
        view.animate().alpha(1f).translationY(0f).setStartDelay(delay)
            .setDuration(320).setInterpolator(new DecelerateInterpolator()).start();
    }

    private void startTorchPulse() {
        stopTorchPulse();
        if (ledButton == null) return;
        ObjectAnimator sx = ObjectAnimator.ofFloat(ledButton, "scaleX", 1f, 1.045f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(ledButton, "scaleY", 1f, 1.045f, 1f);
        sx.setRepeatCount(ValueAnimator.INFINITE);
        sy.setRepeatCount(ValueAnimator.INFINITE);
        sx.setRepeatMode(ValueAnimator.RESTART);
        sy.setRepeatMode(ValueAnimator.RESTART);
        sx.setDuration(1050);
        sy.setDuration(1050);
        pulseAnimator = new AnimatorSet();
        pulseAnimator.playTogether(sx, sy);
        pulseAnimator.setInterpolator(new DecelerateInterpolator());
        pulseAnimator.start();
    }

    private void stopTorchPulse() {
        if (pulseAnimator != null) {
            pulseAnimator.cancel();
            pulseAnimator = null;
        }
        if (ledButton != null) ledButton.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
    }

    private String modeNamesForValue(int value) {
        if (value >= 200) return "Max";
        if (value >= 150) return "Ultra";
        if (value >= 125) return "High";
        return "Normal";
    }

    private void showSettings() {
        final Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(20));
        panel.setBackground(roundBg(Color.rgb(8, 20, 37), dp(24)));

        TextView heading = text("TOOLS & SETTINGS", 20, Color.WHITE, true);
        panel.addView(heading);
        TextView detail = text("ANB Flashlight Ultra", 13, Color.rgb(44, 190, 255), false);
        LinearLayout.LayoutParams dp1 = new LinearLayout.LayoutParams(-1, -2);
        dp1.topMargin = dp(5);
        panel.addView(detail, dp1);

        String[] items = {
            "•  LED torch: hardware controlled",
            "•  Screen Light: maximum display brightness",
            "•  SOS and Strobe: use responsibly",
            "•  Device: " + Build.MANUFACTURER + " " + Build.MODEL,
            "•  Android: " + Build.VERSION.RELEASE,
            "•  Brightness boost requires compatible Android 13+ hardware"
        };
        for (String item : items) {
            TextView row = text(item, 13, Color.rgb(205, 220, 238), false);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
            rp.topMargin = dp(13);
            panel.addView(row, rp);
        }
        Button close = button("Close");
        close.setTextColor(Color.rgb(163, 230, 53));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(48));
        cp.topMargin = dp(18);
        panel.addView(close, cp);
        close.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(panel);
        dialog.show();
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(420)), -2);
        }
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(52), 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value); t.setTextSize(size); t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private android.graphics.drawable.GradientDrawable roundBg(int color, int radius) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(radius); d.setStroke(dp(1), Color.rgb(65,80,75)); return d;
    }

    private Button button(String value) {
        Button b = new Button(this); b.setText(value); b.setTextColor(Color.rgb(163,230,53));
        b.setAllCaps(false); b.setBackground(roundBg(Color.rgb(26,32,39), dp(16))); return b;
    }

    private void findFlashCamera() {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics c = cameraManager.getCameraCharacteristics(id);
                Boolean flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(flash) && (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK)) {
                    cameraId = id;
                    if (Build.VERSION.SDK_INT >= 33) {
                        Integer m = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
                        Integer d = c.get(CameraCharacteristics.FLASH_INFO_STRENGTH_DEFAULT_LEVEL);
                        if (m != null) maxStrength = Math.max(1, m);
                        if (d != null) defaultStrength = Math.max(1, Math.min(maxStrength, d));
                    }
                    status.setText(Build.VERSION.SDK_INT >= 33 && maxStrength > 1
                        ? "LED ready • intensity control supported"
                        : "LED ready • phone controls hardware intensity");
                    return;
                }
            }
            status.setText("No compatible LED flash detected");
            ledButton.setAlpha(0.45f);
        } catch (CameraAccessException e) {
            status.setText("Camera/flash unavailable");
        }
    }

    private void toggleTorch() {
        if (!torchOn && strength <= 0) {
            toast("Set LED request above 0% to turn the torch on.");
            return;
        }
        if (cameraId == null) { toast("This phone does not report a usable LED flash."); return; }
        if (!torchOn && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 10); return;
        }
        try {
            if (torchOn) {
                cameraManager.setTorchMode(cameraId, false);
                torchOn = false; stopTorchPulse(); ledButton.setText("⚡"); ledButton.setTextColor(Color.rgb(163,230,53)); ledButton.setBackground(roundBg(Color.rgb(13,39,57), dp(100))); status.setText("LED torch OFF");
            } else {
                if (Build.VERSION.SDK_INT >= 33 && maxStrength > 1) {
                    int level;
                    if (strength <= 100) {
                        level = Math.max(1, Math.min(defaultStrength,
                            (int)Math.round(Math.max(1, strength) / 100.0 * defaultStrength)));
                    } else {
                        level = defaultStrength + (int)Math.round((strength - 100) / 100.0 * (maxStrength - defaultStrength));
                    }
                    level = Math.max(1, Math.min(maxStrength, level));
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, level);
                } else cameraManager.setTorchMode(cameraId, true);
                torchOn = true; ledButton.setText("⚡"); ledButton.setTextColor(Color.WHITE); ledButton.setBackground(roundBg(Color.rgb(24,75,55), dp(100))); startTorchPulse(); status.setText("LED torch ON • hardware limit applies");
            }
        } catch (Exception e) {
            toast("Torch could not start. Close other camera apps and try again.");
            torchOn = false;
        }
    }

    private void turnTorchOff() {
        try {
            if (cameraId != null && torchOn) cameraManager.setTorchMode(cameraId, false);
        } catch (Exception ignored) {}
        torchOn = false;
        stopTorchPulse();
        if (ledButton != null) {
            ledButton.setText("⚡");
            ledButton.setTextColor(Color.rgb(163,230,53));
            ledButton.setBackground(roundBg(Color.rgb(13,39,57), dp(100)));
        }
        if (status != null) status.setText("LED torch OFF");
    }

    private void applyStrength() {
        if (Build.VERSION.SDK_INT < 33 || maxStrength <= 1 || cameraId == null) return;
        try {
            int level;
            if (strength <= 100) {
                level = Math.max(1, Math.min(defaultStrength,
                    (int)Math.round(Math.max(1, strength) / 100.0 * defaultStrength)));
            } else {
                level = defaultStrength + (int)Math.round((strength - 100) / 100.0 * (maxStrength - defaultStrength));
            }
            level = Math.max(1, Math.min(maxStrength, level));
            cameraManager.turnOnTorchWithStrengthLevel(cameraId, level);
        } catch (Exception e) { status.setText("Intensity adjustment unsupported; using torch mode"); }
    }

    private void showScreenFlash() {
        final android.app.Dialog d = new android.app.Dialog(this);
        TextView white = new TextView(this);
        white.setBackgroundColor(Color.WHITE); white.setText("Tap anywhere to close");
        white.setTextColor(Color.DKGRAY); white.setTextSize(16); white.setGravity(Gravity.CENTER);
        d.setContentView(white);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.white);
            w.setLayout(-1, -1);
            w.setFlags(0x00000400 | 0x00000200, 0x00000400 | 0x00000200);
            w.setStatusBarColor(Color.WHITE); w.setNavigationBarColor(Color.WHITE);
        }
        white.setOnClickListener(v -> d.dismiss());
        d.show();
        if (d.getWindow() != null) d.getWindow().setLayout(-1, -1);
    }

    private void toggleStrobe() {
        if (strobeRunning) { strobeRunning = false; if (strobeTask != null) handler.removeCallbacks(strobeTask); if (torchOn) toggleTorch(); return; }
        if (cameraId == null) { toast("LED flash unavailable"); return; }
        strobeRunning = true;
        strobeTask = new Runnable() {
            public void run() {
                if (!strobeRunning) return;
                try { torchOn = !torchOn; cameraManager.setTorchMode(cameraId, torchOn); }
                catch (Exception e) { strobeRunning = false; return; }
                handler.postDelayed(this, 450);
            }
        };
        handler.post(strobeTask);
    }

    private void startSos() {
        if (cameraId == null) { toast("LED flash unavailable"); return; }
        if (strobeRunning) { strobeRunning = false; if (strobeTask != null) handler.removeCallbacks(strobeTask); }
        strobeRunning = true;
        strobeTask = new Runnable() {
            int[] pattern = {180,180,180,180,180,500,500,180,500,180,500,500,180,180,180,180,180,900};
            int i = 0;
            public void run() {
                if (!strobeRunning) return;
                try { torchOn = !torchOn; cameraManager.setTorchMode(cameraId, torchOn); }
                catch (Exception e) { strobeRunning = false; return; }
                int delay = pattern[i++ % pattern.length];
                handler.postDelayed(this, delay);
            }
        };
        handler.post(strobeTask);
        toast("SOS started. Press Strobe Start/Stop to stop.");
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == 10 && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) toggleTorch();
        else if (requestCode == 10) toast("Camera permission is required for LED torch access.");
    }

    @Override protected void onPause() {
        super.onPause();
        strobeRunning = false;
        if (strobeTask != null) handler.removeCallbacks(strobeTask);
        try { if (torchOn && cameraId != null) cameraManager.setTorchMode(cameraId, false); } catch (Exception ignored) {}
        torchOn = false;
        stopTorchPulse();
        if (status != null) status.setText("LED torch OFF • app paused");
    }
}

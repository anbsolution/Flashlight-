package com.anb.flashlight;

import android.Manifest;
import android.app.Activity;
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
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private CameraManager cameraManager;
    private String cameraId;
    private boolean torchOn = false;
    private int strength = 100;
    private int maxStrength = 1;
    private TextView status, percent, ledButton;
    private SeekBar slider;
    private View screenFlash;
    private final Handler handler = new Handler(Looper.getMainLooper());
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(26), dp(24), dp(20));
        root.setBackgroundColor(Color.rgb(12, 15, 22));

        TextView brand = text("ANB", 15, Color.rgb(163,230,53), true);
        root.addView(brand);
        TextView title = text("FLASHLIGHT ULTRA", 24, Color.WHITE, true);
        LinearLayout.LayoutParams titleP = new LinearLayout.LayoutParams(-1, -2);
        titleP.topMargin = dp(8);
        title.setGravity(Gravity.CENTER);
        root.addView(title, titleP);
        TextView sub = text("Premium light control", 14, Color.LTGRAY, false);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        ledButton = text("⚡", 54, Color.rgb(163,230,53), true);
        ledButton.setGravity(Gravity.CENTER);
        ledButton.setBackground(roundBg(Color.rgb(30, 38, 43), dp(100)));
        LinearLayout.LayoutParams orb = new LinearLayout.LayoutParams(dp(176), dp(176));
        orb.topMargin = dp(30);
        orb.bottomMargin = dp(16);
        root.addView(ledButton, orb);
        ledButton.setOnClickListener(v -> toggleTorch());

        status = text("LED torch ready", 14, Color.LTGRAY, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        percent = text("Brightness target: 100%", 17, Color.WHITE, true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(28);
        root.addView(percent, p);

        slider = new SeekBar(this);
        slider.setMax(100);
        slider.setProgress(0);
        if (Build.VERSION.SDK_INT >= 26) slider.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.rgb(163,230,53)));
        root.addView(slider, new LinearLayout.LayoutParams(-1, -2));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int progress, boolean user) {
                strength = 100 + progress;
                percent.setText("Brightness target: " + strength + "%");
                if (user && torchOn) applyStrength();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        TextView note = text("200% is a target label, not a guarantee of double brightness. Actual LED output is limited by your phone.", 12, Color.rgb(180,190,200), false);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(8);
        root.addView(note, np);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setPadding(0, dp(22), 0, dp(16));
        Button screen = button("Screen Light");
        screen.setOnClickListener(v -> showScreenFlash());
        Button sos = button("SOS");
        sos.setOnClickListener(v -> startSos());
        row.addView(screen, weighted());
        row.addView(sos, weighted());
        root.addView(row);

        Button strobe = button("Strobe: Start / Stop");
        strobe.setOnClickListener(v -> toggleStrobe());
        root.addView(strobe, new LinearLayout.LayoutParams(-1, dp(52)));

        TextView footer = text("Safety first • Stop if the phone gets hot", 12, Color.GRAY, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(-1, -2);
        fp.topMargin = dp(18);
        root.addView(footer, fp);

        screenFlash = new View(this);
        screenFlash.setVisibility(View.GONE);
        screenFlash.setBackgroundColor(Color.WHITE);
        screenFlash.setOnClickListener(v -> screenFlash.setVisibility(View.GONE));
        setContentView(root);
        // Overlay screen light view is created on demand as a separate full-screen window.
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
                        if (m != null) maxStrength = Math.max(1, m);
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
        if (cameraId == null) { toast("This phone does not report a usable LED flash."); return; }
        if (!torchOn && checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 10); return;
        }
        try {
            if (torchOn) {
                cameraManager.setTorchMode(cameraId, false);
                torchOn = false; ledButton.setText("⚡"); status.setText("LED torch OFF");
            } else {
                if (Build.VERSION.SDK_INT >= 33 && maxStrength > 1) {
                    int level = Math.max(1, Math.min(maxStrength, (int)Math.round((strength - 100) / 100.0 * (maxStrength - 1)) + 1));
                    cameraManager.turnOnTorchWithStrengthLevel(cameraId, level);
                } else cameraManager.setTorchMode(cameraId, true);
                torchOn = true; ledButton.setText("●"); status.setText("LED torch ON • hardware limit applies");
            }
        } catch (Exception e) {
            toast("Torch could not start. Close other camera apps and try again.");
            torchOn = false;
        }
    }

    private void applyStrength() {
        if (Build.VERSION.SDK_INT < 33 || maxStrength <= 1 || cameraId == null) return;
        try {
            int level = Math.max(1, Math.min(maxStrength, (int)Math.round((strength - 100) / 100.0 * (maxStrength - 1)) + 1));
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
    }
}

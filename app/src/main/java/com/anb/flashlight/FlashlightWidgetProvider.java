package com.anb.flashlight;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.widget.RemoteViews;

public class FlashlightWidgetProvider extends AppWidgetProvider {
    public static final String ACTION_TOGGLE = "com.anb.flashlight.WIDGET_TOGGLE";
    public static final String ACTION_OPEN = "com.anb.flashlight.WIDGET_OPEN_APP";
    private static boolean torchOn = false;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateWidget(context, manager, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_TOGGLE.equals(action)) {
            if (android.os.Build.VERSION.SDK_INT >= 23 &&
                context.checkSelfPermission(android.Manifest.permission.CAMERA)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Intent open = new Intent(context, MainActivity.class);
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(open);
                return;
            }
            toggleTorch(context);
            updateAll(context);
        } else if (ACTION_OPEN.equals(action)) {
            Intent open = new Intent(context, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(open);
        }
    }

    private void toggleTorch(Context context) {
        CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        if (cm == null) return;
        try {
            String chosen = null;
            for (String id : cm.getCameraIdList()) {
                CameraCharacteristics c = cm.getCameraCharacteristics(id);
                Boolean hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(hasFlash) &&
                    (facing == null || facing == CameraCharacteristics.LENS_FACING_BACK)) {
                    chosen = id;
                    break;
                }
            }
            if (chosen == null) return;
            torchOn = !torchOn;
            cm.setTorchMode(chosen, torchOn);
        } catch (CameraAccessException | SecurityException | IllegalArgumentException e) {
            torchOn = false;
        }
    }

    private void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, FlashlightWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateWidget(context, manager, id);
    }

    private void updateWidget(Context context, AppWidgetManager manager, int id) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.flashlight_widget);
        views.setTextViewText(R.id.widget_status, torchOn ? "TORCH ON" : "TORCH OFF");
        views.setTextViewText(R.id.widget_power, torchOn ? "●" : "ϟ");

        Intent toggle = new Intent(context, FlashlightWidgetProvider.class);
        toggle.setAction(ACTION_TOGGLE);
        toggle.setData(android.net.Uri.parse("anbflashlight://toggle/" + id));
        PendingIntent togglePi = PendingIntent.getBroadcast(
            context, id, toggle,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_power, togglePi);

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(
            context, id + 5000, open,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_open, openPi);

        manager.updateAppWidget(id, views);
    }
}

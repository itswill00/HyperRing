package com.hyperring;

import android.content.Context;
import android.hardware.camera2.CameraManager;

public class TorchMonitor {

    public static void init(Context context) {
        try {
            CameraManager cm = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) {
                cm.registerTorchCallback(new CameraManager.TorchCallback() {
                    @Override
                    public void onTorchModeChanged(String cameraId, boolean enabled) {
                        IslandState.isTorchActive = enabled;
                        if (enabled && IslandConfig.enableTorch) {
                            HyperRingOverlay.showIsland(IslandState.STATE_TORCH, 2600);
                        } else if (!enabled && IslandState.currentIsland == IslandState.STATE_TORCH) {
                            HyperRingOverlay.startCollapse();
                        }
                    }
                }, HyperRingOverlay.handler);
            }
        } catch (Throwable ignored) {}
    }
}

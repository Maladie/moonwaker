package com.limelight.utils;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.drawable.Icon;
import android.os.Build;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

public class ShortcutHelper {

    private final ShortcutManager sm;
    private final Activity context;

    public ShortcutHelper(Activity context) {
        this.context = context;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            sm = context.getSystemService(ShortcutManager.class);
        }
        else {
            sm = null;
        }
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private List<ShortcutInfo> getAllShortcuts() {
        LinkedList<ShortcutInfo> list = new LinkedList<>();
        list.addAll(sm.getDynamicShortcuts());
        list.addAll(sm.getPinnedShortcuts());
        return list;
    }

    @TargetApi(Build.VERSION_CODES.N_MR1)
    private ShortcutInfo getInfoForId(String id) {
        List<ShortcutInfo> shortcuts = getAllShortcuts();

        for (ShortcutInfo info : shortcuts) {
            if (info.getId().equals(id)) {
                return info;
            }
        }

        return null;
    }

    private String getShortcutIdForGame(ComputerDetails computer, NvApp app) {
        return computer.uuid + app.getAppId();
    }

    @TargetApi(Build.VERSION_CODES.O)
    public boolean createPinnedGameShortcut(ComputerDetails computer, NvApp app, Bitmap iconBits) {
        if (sm.isRequestPinShortcutSupported()) {
            Icon appIcon;

            if (iconBits != null) {
                appIcon = Icon.createWithAdaptiveBitmap(iconBits);
            } else {
                appIcon = Icon.createWithResource(context, R.mipmap.ic_pc_scut);
            }

            ShortcutInfo sInfo = new ShortcutInfo.Builder(context, getShortcutIdForGame(computer, app))
                .setIntent(ServerHelper.createAppShortcutIntent(context, computer, app))
                .setShortLabel(app.getAppName() + " (" + computer.name + ")")
                .setIcon(appIcon)
                .build();

            return sm.requestPinShortcut(sInfo, null);
        } else {
            return false;
        }
    }

    public void disableComputerShortcut(ComputerDetails computer, CharSequence reason) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            // Delete the computer shortcut itself
            if (getInfoForId(computer.uuid) != null) {
                sm.disableShortcuts(Collections.singletonList(computer.uuid), reason);
            }

            // Delete all associated app shortcuts too
            List<ShortcutInfo> shortcuts = getAllShortcuts();
            LinkedList<String> appShortcutIds = new LinkedList<>();
            for (ShortcutInfo info : shortcuts) {
                if (info.getId().startsWith(computer.uuid)) {
                    appShortcutIds.add(info.getId());
                }
            }
            sm.disableShortcuts(appShortcutIds, reason);
        }
    }
}

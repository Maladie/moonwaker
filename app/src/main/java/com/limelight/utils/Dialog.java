package com.limelight.utils;

import java.util.ArrayList;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Button;

/** A message the user dismisses with OK or Back; it never ends the Activity. */
public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;

    private AlertDialog alert;

    private static final ArrayList<Dialog> rundownDialogs = new ArrayList<>();

    private Dialog(Activity activity, String title, String message)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
    }

    public static void closeDialogs()
    {
        synchronized (rundownDialogs) {
            // A dismissal removes its dialog from the list: iterate a copy.
            for (Dialog d : new ArrayList<>(rundownDialogs)) {
                if (d.alert.isShowing()) {
                    d.alert.dismiss();
                }
            }

            rundownDialogs.clear();
        }
    }

    public static void displayDialog(Activity activity, String title, String message)
    {
        activity.runOnUiThread(new Dialog(activity, title, message));
    }

    @Override
    public void run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing())
            return;

        alert = new AlertDialog.Builder(activity).create();

        alert.setTitle(title);
        alert.setMessage(message);
        alert.setOnDismissListener(dialog -> {
            synchronized (rundownDialogs) {
                rundownDialogs.remove(Dialog.this);
            }
        });
        alert.setButton(AlertDialog.BUTTON_POSITIVE, activity.getResources().getText(android.R.string.ok),
                (DialogInterface.OnClickListener) null);
        alert.setOnShowListener(new DialogInterface.OnShowListener(){

            @Override
            public void onShow(DialogInterface dialog) {
                // Set focus to the OK button by default
                Button button = alert.getButton(AlertDialog.BUTTON_POSITIVE);
                button.setFocusable(true);
                button.setFocusableInTouchMode(true);
                button.requestFocus();
            }
        });

        synchronized (rundownDialogs) {
            rundownDialogs.add(this);
            alert.show();
        }
    }

}

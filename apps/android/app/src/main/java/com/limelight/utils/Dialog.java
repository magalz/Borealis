package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.Button;

public class Dialog implements Runnable {
    private final String title;
    private final String message;
    private final Activity activity;
    private final Runnable runOnDismiss;

    private AlertDialog alert;

    private Dialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        this.activity = activity;
        this.title = title;
        this.message = message;
        this.runOnDismiss = runOnDismiss;
    }

    public static void displayDialog(Activity activity, String title, String message, Runnable runOnDismiss)
    {
        activity.runOnUiThread(new Dialog(activity, title, message, runOnDismiss));
    }

    @Override
    public void run() {
        // If we're dying, don't bother creating a dialog
        if (activity.isFinishing())
            return;

        alert = new AlertDialog.Builder(activity).create();

        alert.setTitle(title);
        alert.setMessage(message);
        alert.setCancelable(false);
        alert.setCanceledOnTouchOutside(false);
 
        alert.setButton(AlertDialog.BUTTON_POSITIVE, activity.getResources().getText(android.R.string.ok), new DialogInterface.OnClickListener() {
              public void onClick(DialogInterface dialog, int which) {
                  alert.dismiss();

                  runOnDismiss.run();
              }
        });
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

        alert.show();
    }

}

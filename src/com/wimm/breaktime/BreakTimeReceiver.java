/*
  * Copyright (C) 2012 WIMM Labs Incorporated
 */
package com.wimm.breaktime;

import static com.wimm.framework.service.SyncService.Intent.ACTION_NOTIFY_PREFS_MOD;
import static com.wimm.framework.service.SyncService.Intent.EXTRA_MODIFIED_PREFS;

import java.util.ArrayList;
import com.wimm.framework.app.Notification;
import com.wimm.framework.app.NotificationAgent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/*
 * This is the receiver used to listen for sync preference changes (ACTION_NOTIFY_PREFS_MOD)
 * while the application is running. It is also used to listen for notifications request coming
 * from the application.This receiver also help to start off BreakTimeService when
 * device boot.
 */
public class BreakTimeReceiver extends BroadcastReceiver {
    // For debugging purposes.
    private static final String TAG = BreakTimeReceiver.class.getSimpleName();

    // Broadcast intents
    public static final String ACTION_POST_NOTIFICATION = "com.wimm.breaktime.action.POST_NOTIFICATION";
    public static final String EXTRA_NOTIFICATION_MESSAGE = "com.wimm.breaktime.extra.NOTIFICATION_MESSAGE";

    // ID used for notifications
    private static final int ID = 1;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action.equals(ACTION_NOTIFY_PREFS_MOD)) {
            ArrayList<String> modifiedPrefs = intent.getStringArrayListExtra(EXTRA_MODIFIED_PREFS);
            if(modifiedPrefs != null) {
                Intent updateIntent = new Intent(BreakTimeActivity.ACTION_UPDATE_PREFS);
                updateIntent.putStringArrayListExtra(EXTRA_MODIFIED_PREFS, modifiedPrefs);
                context.sendBroadcast(updateIntent);

                // Run service to re-register notification.
                Intent serviceIntent = new Intent(BreakTimeService.SCHEDULE_NOTIFICATION);
                context.startService(serviceIntent);
            }
            else {
                Log.e(TAG, "onReceive~ received modified prefs intent but no prefs");
            }
        }
        else if (action.equals(ACTION_POST_NOTIFICATION)) {
            // Preparing the notification strings.
            String title = context.getString(R.string.notification_title);
            String body = intent.getStringExtra(EXTRA_NOTIFICATION_MESSAGE);

            // Post the notification.
            NotificationAgent na = new NotificationAgent(context);
            na.notify(ID, new Notification(title, body, true));

        }
        else if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            // Run service to re-register notification.
            Intent serviceIntent = new Intent(BreakTimeService.SCHEDULE_NOTIFICATION);
            context.startService(serviceIntent);
        }
    }

}

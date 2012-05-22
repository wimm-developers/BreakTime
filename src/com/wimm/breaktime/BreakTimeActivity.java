/*
  * Copyright (C) 2012 WIMM Labs Incorporated
 */
package com.wimm.breaktime;

import static com.wimm.framework.service.SyncService.Intent.EXTRA_MODIFIED_PREFS;

import java.util.ArrayList;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wimm.framework.app.LauncherActivity;
import com.wimm.framework.provider.SyncPreference;
import com.wimm.framework.widget.ToggleButton;

/*
 * BreakTime is an app that remind user to periodically take a
 * break(i.e take their eyes off the screen, get off their chair) during
 * their work day through prompt/reminder(Notifications).
 * Users will able configure some settings of this app through the
 * setting panel in the application. They are also able to make these changes
 * via the web preference panel in their account on www.wimm.com.
 *
 * This applications is created to demonstrate the usage of WIMM sync preference
 * settings.
 *
 */
public class BreakTimeActivity extends LauncherActivity implements OnClickListener{
    // For debugging purposes.
    public static final String TAG = BreakTimeActivity.class.getSimpleName();

    // Broadcast intents.
    public static final String ACTION_UPDATE_PREFS = "com.wimm.breaktime.action.UPDATE_PREFS";

    // Preferences default values.
    public static final boolean DEFAULT_ENABLE_NOTIFICATIONS = true;
    public static final int DEFAULT_START_WORK_TIME = 540;
    public static final int DEFAULT_END_WORK_TIME = 1020;
    public static final int DEFAULT_WORK_DAYS_CODE = 1; // 1(Mon - Fri), 2(Mon - Sat), 3(Mon - Sun)
    public static final int DEFAULT_BREAK_INTERVAL = 60;

    // Preferences Key strings.
    public static final String PREF_ENABLE_NOTIFICATIONS = "enable_notifications";
    public static final String PREF_START_WORK_TIME = "start_work_time";
    public static final String PREF_END_WORK_TIME = "end_work_time";
    public static final String PREF_WORK_DAYS_CODE = "work_days_code";
    public static final String PREF_BREAK_INTERVAL = "break_interval";

    // Layout Views
    private ScrollView mScrollView;
    private ToggleButton mNotificationsToggleButton;
    private TextView mWorkHours;
    private TextView mWorkDays;
    private TextView mBreakInterval;

    // Preferences local values.
    private boolean mEnableNotifications;
    private int mStartWorkTimeInMinsSinceMidnight;
    private int mEndWorkTimeInMinsSinceMidnight;
    private int mWorkDaysCode; // 1(Mon - Fri), 2(Mon - Sat), 3(Mon - Sun)
    private int mBreakIntervalValueInMins;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mScrollView = (ScrollView) findViewById(R.id.scroll_view);
        mNotificationsToggleButton = (ToggleButton) findViewById(R.id.notifications_toggle);
        // Register for onClick
        mNotificationsToggleButton.setOnClickListener(this);
        mWorkHours = (TextView) findViewById(R.id.work_hours);
        mWorkDays = (TextView) findViewById(R.id.work_days);
        mBreakInterval = (TextView) findViewById(R.id.break_interval);

        // Register for broadcast intents.
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_UPDATE_PREFS);
        this.registerReceiver(mReceiver, filter);

        // Getting sync preference values
        mEnableNotifications = SyncPreference.getBoolean(this,PREF_ENABLE_NOTIFICATIONS, DEFAULT_ENABLE_NOTIFICATIONS);
        mStartWorkTimeInMinsSinceMidnight = SyncPreference.getInt(this, PREF_START_WORK_TIME, DEFAULT_START_WORK_TIME);
        mEndWorkTimeInMinsSinceMidnight = SyncPreference.getInt(this, PREF_END_WORK_TIME, DEFAULT_END_WORK_TIME);
        mWorkDaysCode = SyncPreference.getInt(this, PREF_WORK_DAYS_CODE, DEFAULT_WORK_DAYS_CODE);
        mBreakIntervalValueInMins = SyncPreference.getInt(this, PREF_BREAK_INTERVAL, DEFAULT_BREAK_INTERVAL);

        // Update UI
        updateSettingsDisplay();

        // Schedule notifications
        scheduleNotification();
    }

    void updateSettingsDisplay() {
        mNotificationsToggleButton.setChecked(mEnableNotifications);

        mWorkHours.setText(getWorkHoursString());
        mWorkDays.setText(getWorkDaysString());

        mBreakInterval.setText(this.getString(R.string.break_interval)+" "+formatTimeInterval(mBreakIntervalValueInMins));
    }

    void scheduleNotification() {
        // Run service to schedule notification.
        Intent serviceIntent = new Intent(BreakTimeService.SCHEDULE_NOTIFICATION);
        this.startService(serviceIntent);
    }

    private String getWorkHoursString() {
        return getStartWorkTimeString()+" - "+getEndWorkTimeString()+",";
    }

    private String getWorkDaysString() {
        if (mWorkDaysCode == 1){
            return "Monday - Friday";
        }
        else if (mWorkDaysCode == 2){
            return "Monday - Saturday";
        }
        else{
            return "Monday - Sunday";
        }
    }

    private String getStartWorkTimeString() {
        return formatTimeFromMinsPastMidnight(mStartWorkTimeInMinsSinceMidnight);
    }

    private String getEndWorkTimeString() {
        return formatTimeFromMinsPastMidnight(mEndWorkTimeInMinsSinceMidnight);
    }

    private String formatTimeFromMinsPastMidnight(int minutesPastMidnight) {
        int hours =  (int)(minutesPastMidnight/60);
        int mins = minutesPastMidnight%60;
        if(minutesPastMidnight >= 720) { //pm
            if (hours > 12) {
                hours = hours - 12;
            }
            return hours+"."+padTwoDigits(mins)+" pm";
        }
        else { // am
            if (hours == 0) {
                hours = 12;
            }
            return hours+"."+padTwoDigits(mins)+" am";
        }
    }

    private String padTwoDigits(int i) {
        if (i < 10)
            return "0"+i;
        return String.valueOf(i);
    }

    private String formatTimeInterval(int minutes) {
        if (minutes < 60) {
            return minutes + " mins";
        }
        else if (minutes == 60) {
            return "hour";
        }
        else if (minutes == 90) {
            return "1 hr 30 mins";
        }
        else if ((minutes%60)==0) {
            return minutes + " hrs";
        }
        else {
            return ((int) (minutes/60))+ " hrs "+(minutes % 60)+" mins";
        }
    }

    // Saving preference to SyncPreference which will be push to
    // the cloud during the next sync.
    private void setPreference(String preference, boolean value) {
        SyncPreference.putBoolean(getApplicationContext(), preference, value);
    }

    @Override
    public void onClick(View v) {
        if (mNotificationsToggleButton.equals(v)) {
            setPreference(PREF_ENABLE_NOTIFICATIONS, mNotificationsToggleButton.isChecked());
            mEnableNotifications = mNotificationsToggleButton.isChecked();

            // Schedule notifications
            scheduleNotification();
        }
        else {
            Log.e(TAG, "Invalid onclick event");
        }

    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_UPDATE_PREFS)) {
                ArrayList<String> modifiedPrefs = intent.getStringArrayListExtra(EXTRA_MODIFIED_PREFS);
                for (String key : modifiedPrefs){
                    if (key.equals(PREF_ENABLE_NOTIFICATIONS)) {
                        mEnableNotifications = SyncPreference.getBoolean(context, PREF_ENABLE_NOTIFICATIONS, DEFAULT_ENABLE_NOTIFICATIONS);
                    }
                    else if (key.equals(PREF_START_WORK_TIME)) {
                        mStartWorkTimeInMinsSinceMidnight = SyncPreference.getInt(context, PREF_START_WORK_TIME, DEFAULT_START_WORK_TIME);
                    }
                    else if (key.equals(PREF_END_WORK_TIME)) {
                        mEndWorkTimeInMinsSinceMidnight = SyncPreference.getInt(context, PREF_END_WORK_TIME, DEFAULT_END_WORK_TIME);
                    }
                    else if (key.equals(PREF_WORK_DAYS_CODE)) {
                        mWorkDaysCode = SyncPreference.getInt(context, PREF_WORK_DAYS_CODE, DEFAULT_WORK_DAYS_CODE);
                    }
                    else if (key.equals(PREF_BREAK_INTERVAL)) {
                        mBreakIntervalValueInMins = SyncPreference.getInt(context, PREF_BREAK_INTERVAL, DEFAULT_BREAK_INTERVAL);
                    }
                    else {
                        Log.e (TAG, "unknown prefs detected");
                    }
                }
                updateSettingsDisplay();
            }
            else {

            }
        }
    };

    @Override
    public boolean dragCanExit() {
        return (mScrollView.getScrollY() == 0);
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.unregisterReceiver(mReceiver);
    }

}
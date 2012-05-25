/*
  * Copyright (C) 2012 WIMM Labs Incorporated
 */
package com.wimm.breaktime;

import static com.wimm.breaktime.BreakTimeActivity.DEFAULT_BREAK_INTERVAL;
import static com.wimm.breaktime.BreakTimeActivity.DEFAULT_ENABLE_NOTIFICATIONS;
import static com.wimm.breaktime.BreakTimeActivity.DEFAULT_END_WORK_TIME;
import static com.wimm.breaktime.BreakTimeActivity.DEFAULT_START_WORK_TIME;
import static com.wimm.breaktime.BreakTimeActivity.DEFAULT_WORK_DAYS_CODE;

import static com.wimm.breaktime.BreakTimeActivity.PREF_BREAK_INTERVAL;
import static com.wimm.breaktime.BreakTimeActivity.PREF_ENABLE_NOTIFICATIONS;
import static com.wimm.breaktime.BreakTimeActivity.PREF_END_WORK_TIME;
import static com.wimm.breaktime.BreakTimeActivity.PREF_START_WORK_TIME;
import static com.wimm.breaktime.BreakTimeActivity.PREF_WORK_DAYS_CODE;

import java.util.Calendar;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;

import com.wimm.framework.provider.SyncPreference;

/*
 * This is the service class that is responsible for calculating and
 * schedule the next notification.
 */
public class BreakTimeService extends IntentService {
    // For debugging purposes.
    public static final String TAG = BreakTimeService.class.getSimpleName();

    // Broadcast Intent.
    public static final String SCHEDULE_NOTIFICATION = "com.wimm.breaktime.action.SCHEDULE_NOTIFICATION";

    // Preferences local values.
    private boolean mEnableNotifications;
    private int mStartWorkTimeInMinsSinceMidnight;
    private int mEndWorkTimeInMinsSinceMidnight;
    private int mWorkDaysCode; // 1(Mon - Fri), 2(Mon - Sat), 3(Mon - Sun)
    private int mBreakIntervalValueInMins;

    // Calendar instance for date time calculation.
    private Calendar mCalendar = Calendar.getInstance();

    public BreakTimeService() {
        super(TAG);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Getting sync preference values
        mEnableNotifications = SyncPreference.getBoolean(this,PREF_ENABLE_NOTIFICATIONS, DEFAULT_ENABLE_NOTIFICATIONS);
        mStartWorkTimeInMinsSinceMidnight = SyncPreference.getInt(this, PREF_START_WORK_TIME, DEFAULT_START_WORK_TIME);
        mEndWorkTimeInMinsSinceMidnight = SyncPreference.getInt(this, PREF_END_WORK_TIME, DEFAULT_END_WORK_TIME);
        mWorkDaysCode = SyncPreference.getInt(this, PREF_WORK_DAYS_CODE, DEFAULT_WORK_DAYS_CODE);
        mBreakIntervalValueInMins = SyncPreference.getInt(this, PREF_BREAK_INTERVAL, DEFAULT_BREAK_INTERVAL);

    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (action.equals(SCHEDULE_NOTIFICATION)) {
            // Reschedule notification
            cancelScheduledNotification();
            scheduleNotification();
        }
    }

    // Schedule a notification in the future according to preference settings.
    private void scheduleNotification() {
        if (mEnableNotifications) {
            long timeOffsetFromNowInMS = (long) (SystemClock.elapsedRealtime() + minsToNextNotifications()*60000);
            schedulePendingIntent(timeOffsetFromNowInMS, newIntentForAlarm(BreakTimeReceiver.ACTION_POST_NOTIFICATION, getString(R.string.notification_break_msg)));
        }
    }

    // Cancel the last scheduled notification.
    private void cancelScheduledNotification() {
        AlarmManager am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        am.cancel(newIntentForAlarm(BreakTimeReceiver.ACTION_POST_NOTIFICATION, getString(R.string.notification_break_msg)));
    }

    private void schedulePendingIntent(long timeOffsetFromNowInMS, PendingIntent pi) {
        // Schedule the intent with the AlarmManager
        AlarmManager am = (AlarmManager)getSystemService(Context.ALARM_SERVICE);
        am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, timeOffsetFromNowInMS, pi);
    }

    private PendingIntent newIntentForAlarm(String intent, String message) {
        Intent i = new Intent(intent);
        i.setClass(this, BreakTimeReceiver.class);
        i.addCategory(Intent.CATEGORY_ALTERNATIVE);

        i.putExtra(BreakTimeReceiver.EXTRA_NOTIFICATION_MESSAGE, message);

        return PendingIntent.getBroadcast(getApplicationContext(), 0, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }


    /*
     *  All math related functions for notification calculations.
     */
    private int minsToNextWorkDay() {
        return daysToNextWorkDay() * 24 * 60;
    }

    // Return the no of days to the next workday, 0 if tmr is a workday.
    private int daysToNextWorkDay() {
        int day = mCalendar.get(Calendar.DAY_OF_WEEK);
        if (day == 6) { // fri
            if (mWorkDaysCode == 1) {
                return 2;
            }
        }
        else if (day == 7) {// sat
            if (mWorkDaysCode == 1 || mWorkDaysCode == 2) {
                return 1;
            }
        }
        return 0;
    }

    // Return true if mStartWorkTimeInMinsSinceMidnight < mEndWorkTimeInMinsSinceMidnight
    private boolean isDayShift() {
        return mStartWorkTimeInMinsSinceMidnight < mEndWorkTimeInMinsSinceMidnight;
    }

    private boolean isWorkHour() {
        int minsSinceMidNight = minsSinceMidnight();
        if (isDayShift()) {
            if (minsSinceMidNight >= mStartWorkTimeInMinsSinceMidnight && minsSinceMidNight <= mEndWorkTimeInMinsSinceMidnight) {
                return true;
            }
        }
        else {
            if (minsSinceMidNight >= mStartWorkTimeInMinsSinceMidnight || minsSinceMidNight <= mEndWorkTimeInMinsSinceMidnight) {
                return true;
            }
        }
        return false;
    }

    private int minsSinceMidnight() {
        return mCalendar.get(Calendar.HOUR_OF_DAY) * 60 + mCalendar.get(Calendar.MINUTE);
    }

    private int getLastWorkDayNotificationInMinsSinceMidnight() {
        if (isDayShift()) {
            return mEndWorkTimeInMinsSinceMidnight - ((mEndWorkTimeInMinsSinceMidnight - mStartWorkTimeInMinsSinceMidnight) % mBreakIntervalValueInMins);
        }
        else {
            return mEndWorkTimeInMinsSinceMidnight - ((mEndWorkTimeInMinsSinceMidnight + ((24*60) - mStartWorkTimeInMinsSinceMidnight)) % mBreakIntervalValueInMins);
        }
    }

    private int getFirstWorkDayNotificationInMinsSinceMidnight() {
        return mStartWorkTimeInMinsSinceMidnight + mBreakIntervalValueInMins;
    }

    private int minsToNextBreakMinute() {
        int minsSinceMidnight = minsSinceMidnight();
        if (isWorkHour()) {
            // handle edge cases where next notification will be in the next work day (after last notification, before end work time)
            if ((minsSinceMidnight > getLastWorkDayNotificationInMinsSinceMidnight()) && (minsSinceMidnight < mEndWorkTimeInMinsSinceMidnight)) {
                int firstWorkDayNotificationInMinsSinceMidnight = getFirstWorkDayNotificationInMinsSinceMidnight();
                if (isDayShift()) {
                    return firstWorkDayNotificationInMinsSinceMidnight + ((24*60)-minsSinceMidnight);
                }
                else {
                    return firstWorkDayNotificationInMinsSinceMidnight - minsSinceMidnight;
                }
            }

            if (isDayShift()) {
                return mBreakIntervalValueInMins - ((minsSinceMidnight - mStartWorkTimeInMinsSinceMidnight) % mBreakIntervalValueInMins);
            }
            else {
                return mBreakIntervalValueInMins - ((minsSinceMidnight + ((24*60)-mStartWorkTimeInMinsSinceMidnight)) % mBreakIntervalValueInMins);
            }
        }
        else {
            // day shift and after end work hours
            if (isDayShift() && minsSinceMidnight > mEndWorkTimeInMinsSinceMidnight) {
                return ((24*60)-minsSinceMidnight) + mStartWorkTimeInMinsSinceMidnight + mBreakIntervalValueInMins;
            }
            else {
                // day shift and night shift before start hours.
                return (mStartWorkTimeInMinsSinceMidnight - minsSinceMidnight + mBreakIntervalValueInMins);
            }
        }
    }

    private int minsToNextNotifications() {
        return minsToNextBreakMinute() + minsToNextWorkDay();
    }

}

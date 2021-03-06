/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.policy.impl.keyguard;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Slog;
import android.view.View;
import android.widget.GridLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.util.Lunar;

import com.android.internal.R;
import com.android.internal.widget.LockPatternUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import libcore.icu.ICU;

public class KeyguardStatusView extends GridLayout {
    private static final boolean DEBUG = KeyguardViewMediator.DEBUG;
    private static final String TAG = "KeyguardStatusView";

    public static final int LOCK_ICON = 0; // R.drawable.ic_lock_idle_lock;
    public static final int ALARM_ICON = com.android.internal.R.drawable.ic_lock_idle_alarm;
    public static final int CHARGING_ICON = 0; //R.drawable.ic_lock_idle_charging;
    public static final int DISCHARGING_ICON = 0; // no icon used in ics+ currently
    public static final int BATTERY_LOW_ICON = 0; //R.drawable.ic_lock_idle_low_battery;

    private SimpleDateFormat mDateFormat;
    private CharSequence mDateFormatString;
    private CharSequence mDateFormatString1;
    private LockPatternUtils mLockPatternUtils;

    private TextView mDateView;
    private TextView mLunarDateView;
    private TextView mAlarmStatusView;
    private ClockView mClockView;

    private KeyguardUpdateMonitorCallback mInfoCallback = new KeyguardUpdateMonitorCallback() {

        @Override
        public void onTimeChanged() {
            refresh();
        }

        @Override
        void onKeyguardVisibilityChanged(boolean showing) {
            if (showing) {
                if (DEBUG) Slog.v(TAG, "refresh statusview showing:" + showing);
                refresh();
            }
        };
    };

    public KeyguardStatusView(Context context) {
        this(context, null, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public KeyguardStatusView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        Resources res = getContext().getResources();
        final Locale locale = Locale.getDefault();
        final String datePattern =
                res.getString(com.android.internal.R.string.system_ui_date_pattern);
        final String bestFormat = ICU.getBestDateTimePattern(datePattern, locale.toString());
        mDateFormat = new SimpleDateFormat(bestFormat, locale);
        mDateFormatString = res.getText(com.android.internal.R.string.abbrev_wday_month_day_no_year);
        mDateFormatString1 = res.getText(com.android.internal.R.string.abbrev_wday_month_day_year);
        mDateView = (TextView) findViewById(R.id.date);
        mLunarDateView = (TextView) findViewById(R.id.lunar_date);
        mAlarmStatusView = (TextView) findViewById(R.id.alarm_status);
        mClockView = (ClockView) findViewById(R.id.clock_view);
        mLockPatternUtils = new LockPatternUtils(getContext());

        // Use custom font in mDateView
        mDateView.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);
        if(mLunarDateView!=null)
        mLunarDateView.setTypeface(Typeface.SANS_SERIF, Typeface.BOLD);

        // Required to get Marquee to work.
        final View marqueeViews[] = { mDateView, mAlarmStatusView };
        for (int i = 0; i < marqueeViews.length; i++) {
            View v = marqueeViews[i];
            if (v == null) {
                throw new RuntimeException("Can't find widget at index " + i);
            }
            v.setSelected(true);
        }
        refresh();
    }

    protected void refresh() {
        mClockView.updateTime();
        refreshDate();
        refreshAlarmStatus(); // might as well
    }

    void refreshAlarmStatus() {
        // Update Alarm status
        String nextAlarm = mLockPatternUtils.getNextAlarm();
        if (!TextUtils.isEmpty(nextAlarm)) {
            maybeSetUpperCaseText(mAlarmStatusView, nextAlarm);
            mAlarmStatusView.setCompoundDrawablesWithIntrinsicBounds(ALARM_ICON, 0, 0, 0);
            mAlarmStatusView.setVisibility(View.VISIBLE);
        } else {
            mAlarmStatusView.setVisibility(View.GONE);
        }
    }

    void refreshDate() {
        maybeSetUpperCaseText(mDateView, DateFormat.format(mDateFormatString, new Date()));
        Resources res = getContext().getResources();
        String strCountry = res.getConfiguration().locale.getCountry();
        if(strCountry.equals("CN") && mLunarDateView != null || strCountry.equals("TW") && mLunarDateView != null){
            mLunarDateView.setText(buildLunarDate(DateFormat.format(mDateFormatString1, new Date()).toString()));
        }
    }

    /**
     * Get lunar date
     */
	private String buildLunarDate(String date){
        List<String> list = new ArrayList<String>();
        Calendar cal = Calendar.getInstance();    
        Pattern p = Pattern.compile("[\\u4e00-\\u9fa5]+|\\d+");
        Matcher m = p.matcher(date);
        while(m.find()){
            list.add(m.group());
        }
        cal.set(Integer.parseInt(list.get(0)), Integer.parseInt(list.get(2)) - 1,Integer.parseInt(list.get(4)));
        Lunar lunar = new Lunar(cal, getContext());
        return lunar.toString().substring(4,lunar.toString().length());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        KeyguardUpdateMonitor.getInstance(mContext).registerCallback(mInfoCallback);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        KeyguardUpdateMonitor.getInstance(mContext).removeCallback(mInfoCallback);
    }

    public int getAppWidgetId() {
        return LockPatternUtils.ID_DEFAULT_STATUS_WIDGET;
    }

    private void maybeSetUpperCaseText(TextView textView, CharSequence text) {
        if (KeyguardViewManager.USE_UPPER_CASE) {
            textView.setText(text != null ? text.toString().toUpperCase() : null);
        } else {
            textView.setText(text);
        }
    }
}

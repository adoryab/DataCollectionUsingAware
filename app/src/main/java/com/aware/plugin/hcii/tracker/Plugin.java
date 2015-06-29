package com.aware.plugin.hcii.tracker;

import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;

/**
 * Created by denzil on 29/06/15.
 */
public class Plugin extends Aware_Plugin {

    private static Uri ACTIVITY_URI; //only set once we detect plugin is installed!

    //Called once, when your plugin is started
    @Override
    public void onCreate() {
        super.onCreate();

        TAG = "HCII::TRACKER";
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        //Start plugins first, before setting settings for them. If the phone doesn't have them, download will be requested. Once installed, AWARE will automatically start them.
        Aware.startPlugin(this, "com.aware.plugin.google.fused_location");
        Aware.startPlugin(this, "com.aware.plugin.google.activity_recognition");

        //Activate installations sensor
        Aware.setSetting(this, Aware_Preferences.STATUS_INSTALLATIONS, true);

        //Activate Accelerometer
        Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER, true);

        //Set sampling frequency
        Aware.setSetting(this, Aware_Preferences.FREQUENCY_ACCELEROMETER, 200000); //this is too fast! This is a millisecond delay between samples. See documentation of what values it expects!

        //Set Google Activity Recognition settings - see online documentation for setting values
        Aware.setSetting(this, "status_plugin_google_activity_recognition", true, "com.aware.plugin.google.activity_recognition");
        Aware.setSetting(this, "frequency_plugin_google_activity_recognition", 60, "com.aware.plugin.google.activity_recognition");

        //Set Google Fused Location settings - See online documentation for settings values
        Aware.setSetting(this, "status_google_fused_location", true, "com.aware.plugin.google.fused_location");
        Aware.setSetting(this, "frequency_google_fused_location", 60, "com.aware.plugin.google.fused_location");
        Aware.setSetting(this, "max_frequency_google_fused_location", 60, "com.aware.plugin.google.fused_location");
        Aware.setSetting(this, "accuracy_google_fused_location", 102, "com.aware.plugin.google.fused_location");

        //Apply settings again
        Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.fused_location");
        Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.activity_recognition");

        ACTIVITY_URI = Uri.parse("content://com.aware.plugin.google.activity_recognition.provider/plugin_google_activity_recognition");
        activityObs = new ActivityRecognitionObserver(new Handler());
        getContentResolver().registerContentObserver(ACTIVITY_URI, true, activityObs);

        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
    }

    //Method called automatically by AWARE every 5 minutes to make sure the plugin is running
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //If you change the debug setting on the client, update it immediately on the plugin too
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        //Ask AWARE to make sure plugins are still running
        Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.fused_location");
        Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.activity_recognition");

        return super.onStartCommand(intent, flags, startId);
    }

    //Called when plugin is turned off
    @Override
    public void onDestroy() {
        super.onDestroy();

        Aware.stopPlugin(this, "com.aware.plugin.google.fused_location");
        Aware.stopPlugin(this, "com.aware.plugin.google.activity_recognition");
        Aware.setSetting(this, "status_plugin_google_activity_recognition", false, "com.aware.plugin.google.activity_recognition");
        Aware.setSetting(this, "status_google_fused_location", false, "com.aware.plugin.google.fused_location");
        Aware.setSetting(this, Aware_Preferences.STATUS_INSTALLATIONS, false);
        Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER, false);

        if( activityObs != null ) {
            getContentResolver().unregisterContentObserver(activityObs);
        }

        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
    }

    private static ActivityRecognitionObserver activityObs;
    public class ActivityRecognitionObserver extends ContentObserver {
        public ActivityRecognitionObserver(Handler handler) {
            super(handler);
        }

        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            // Get the latest recorded value
            Log.d("OBSERVER", "Change in activity data detected");
            Cursor activity = getContentResolver().query(Plugin.ACTIVITY_URI, null, null, null, "activity_name DESC LIMIT 1"); //space issue with SQL here...
            if( activity != null && activity.moveToFirst() ) {
                // Here we read the value
                String activity_name = activity.getString(activity.getColumnIndex("activity_name"));
                if ( activity_name.equals("in_vehicle") ){
                    Aware.setSetting(getApplicationContext(), "frequency_google_fused_location", 60, "com.aware.plugin.google.activity_recognition");
                    Log.d("OBSERVER", "Recognized in vehicle");
                    Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.activity_recognition");
                } else {
                    Aware.setSetting(getApplicationContext(), "frequency_google_fused_location", 180, "com.aware.plugin.google.activity_recognition");
                    Log.d("OBSERVER", "Recognized on foot");
                    Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.activity_recognition");
                }
            }
            if( activity != null && ! activity.isClosed() ) activity.close();
        }
    }
}

package com.aware.plugin.hcii.tracker;

import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Aware_Provider;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.WebserviceHelper;

import java.util.UUID;

/**
 * Created by denzil on 29/06/15.
 */
public class Plugin extends Aware_Plugin {

    private static Uri ACTIVITY_URI; //only set once we detect plugin is installed!

    private static final String pkg_google_fused = "com.aware.plugin.google.fused_location";
    private static final String pkg_google_activity_recog = "com.aware.plugin.google.activity_recognition";

    private SharedPreferences prefs;

    //Called once, when your plugin is started
    @Override
    public void onCreate() {
        super.onCreate();

        prefs = getSharedPreferences(getPackageName(), MODE_PRIVATE);

        Intent aware = new Intent(this, Aware.class);
        startService(aware);

        if( ! prefs.contains("device_id") ) {
            UUID uuid = UUID.randomUUID();

            ContentValues newId = new ContentValues();
            newId.put(Aware_Provider.Aware_Settings.SETTING_KEY, Aware_Preferences.DEVICE_ID);
            newId.put(Aware_Provider.Aware_Settings.SETTING_VALUE, uuid.toString());
            newId.put(Aware_Provider.Aware_Settings.SETTING_PACKAGE_NAME, "com.aware");
            getContentResolver().insert(Aware_Provider.Aware_Settings.CONTENT_URI, newId);

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("device_id", uuid.toString());
            editor.apply();
        }

        //Turn debugging messages on
        Aware.setSetting(this, Aware_Preferences.DEBUG_FLAG, true);

        TAG = "HCII::TRACKER";
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        //Start plugins first, before setting settings for them. If the phone doesn't have them, download will be requested. Once installed, AWARE will automatically start them.
        Aware.startPlugin(this, pkg_google_fused);
        Aware.startPlugin(this, pkg_google_activity_recog);

        //Activate installations sensor
        Aware.setSetting(this, Aware_Preferences.STATUS_INSTALLATIONS, true);

        //Activate Accelerometer
      //  Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER, true);

        //Set sampling frequency
      //  Aware.setSetting(this, Aware_Preferences.FREQUENCY_ACCELEROMETER, 200000); //this is too fast! This is a millisecond delay between samples. See documentation of what values it expects!

        //Set Google Activity Recognition settings - see online documentation for setting values
        Aware.setSetting(this, "status_plugin_google_activity_recognition", true, pkg_google_activity_recog);
        Aware.setSetting(this, "frequency_plugin_google_activity_recognition", 60, pkg_google_activity_recog);

        //Set Google Fused Location settings - See online documentation for settings values
        Aware.setSetting(this, "status_google_fused_location", true, pkg_google_fused);
        Aware.setSetting(this, "frequency_google_fused_location", 60, pkg_google_fused);
        Aware.setSetting(this, "max_frequency_google_fused_location", 60, pkg_google_fused);
        Aware.setSetting(this, "accuracy_google_fused_location", 102, pkg_google_fused);
      //  Aware.setSetting(this, Aware_Preferences.STATUS_WEBSERVICE, true);
     //   Aware.setSetting(this, Aware_Preferences.WEBSERVICE_SERVER., “127.0.0.1”);

        //Apply settings again
        Aware.startPlugin(getApplicationContext(), pkg_google_fused);
        Aware.startPlugin(getApplicationContext(), pkg_google_activity_recog);

        ACTIVITY_URI = Uri.parse("content://com.aware.plugin.google.activity_recognition.provider/plugin_google_activity_recognition");
        activityObs = new ActivityRecognitionObserver(new Handler());
        getContentResolver().registerContentObserver(ACTIVITY_URI, true, activityObs);

        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
        /*
        if( DATABASE_TABLES != null && TABLES_FIELDS != null && CONTEXT_URIS != null) {
            for (int i = 0; i < DATABASE_TABLES.length; i++) {
                Intent webserviceHelper = new Intent(this, WebserviceHelper.class);
                webserviceHelper.setAction(WebserviceHelper.ACTION_AWARE_WEBSERVICE_SYNC_TABLE);
                webserviceHelper.putExtra(WebserviceHelper.EXTRA_TABLE, DATABASE_TABLES[i]);
                webserviceHelper.putExtra(WebserviceHelper.EXTRA_FIELDS, TABLES_FIELDS[i]);
                webserviceHelper.putExtra(WebserviceHelper.EXTRA_CONTENT_URI, CONTEXT_URIS[i].toString());
                this.startService(webserviceHelper);
            }
        }else {
            if( Aware.DEBUG ) Log.d(TAG,"No database to backup!");
        }
        */

    }

    //Method called automatically by AWARE every 5 minutes to make sure the plugin is running
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //If you change the debug setting on the client, update it immediately on the plugin too
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        //Ask AWARE to make sure plugins are still running
        Aware.startPlugin(getApplicationContext(), pkg_google_fused);
        Aware.startPlugin(getApplicationContext(), pkg_google_activity_recog);

        return super.onStartCommand(intent, flags, startId);
    }

    //Called when plugin is turned off
    @Override
    public void onDestroy() {
        super.onDestroy();

        Aware.stopPlugin(this, pkg_google_fused);
        Aware.stopPlugin(this, pkg_google_activity_recog);
        Aware.setSetting(this, "status_plugin_google_activity_recognition", false, pkg_google_activity_recog);
        Aware.setSetting(this, "status_google_fused_location", false, pkg_google_fused);
        Aware.setSetting(this, Aware_Preferences.STATUS_INSTALLATIONS, false);
     //   Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER, false);

        if( activityObs != null ) {
            getContentResolver().unregisterContentObserver(activityObs);
        }

        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
    }

    private ActivityRecognitionObserver activityObs;
    public class ActivityRecognitionObserver extends ContentObserver {
        public ActivityRecognitionObserver(Handler handler) {
            super(handler);
        }

        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            // Get the latest recorded value
            Log.d(TAG, "Change in activity data detected");
            Cursor activity = getContentResolver().query(Plugin.ACTIVITY_URI, null, null, null, "activity_name DESC LIMIT 1"); //space issue with SQL here...
            if( activity != null && activity.moveToFirst() ) {
                // Here we read the value
                String activity_name = activity.getString(activity.getColumnIndex("activity_name"));
                if ( activity_name.equals("in_vehicle") ){
                    Aware.setSetting(getApplicationContext(), "frequency_google_fused_location", 60, pkg_google_activity_recog);
                    Log.d(TAG, "Recognized in vehicle");
                    Aware.startPlugin(getApplicationContext(), pkg_google_activity_recog);
                } else {
                    Aware.setSetting(getApplicationContext(), "frequency_google_fused_location", 180, pkg_google_activity_recog);
                    Log.d(TAG, "Recognized on foot");
                    Aware.startPlugin(getApplicationContext(), pkg_google_activity_recog);
                }
            }
            if( activity != null && ! activity.isClosed() ) activity.close();
        }
    }
}

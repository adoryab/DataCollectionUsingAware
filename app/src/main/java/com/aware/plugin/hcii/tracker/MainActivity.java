package com.aware.plugin.hcii.tracker;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.Locations;
import com.aware.plugin.google.activity_recognition.Google_AR_Provider;
import com.aware.plugin.google.fused_location.Algorithm;
import com.aware.providers.Aware_Provider;
import com.aware.providers.Locations_Provider;
import com.aware.ui.Aware_Activity;
import com.google.android.gms.location.ActivityRecognition;

import java.util.ArrayList;

/**
 * Created by denzil on 30/06/15.
 */
public class MainActivity extends Activity {
    //
    public static final String lastLocationDbUpload = "last_location_database_upload";
    public static final String lastActivityDbUpload = "last_activity_database_upload";
    public static SharedPreferences sp;
    private static final String MY_REFERENCES = "MyRefs";
    //private int significantTime = 60; //recorded in seconds

    private IntentFilter batteryFilter;
    private int status;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerBatteryReceiver();


        //used to initialize the database time tracker, use sp to keep in persistent memory
        sp=getSharedPreferences(MY_REFERENCES, MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putLong(lastLocationDbUpload, (Long) (System.currentTimeMillis()));
        editor.putLong(lastActivityDbUpload, (Long) (System.currentTimeMillis()));
        editor.commit();
        setContentView(R.layout.main_activity);

        Intent startAware = new Intent(this, Aware.class);
        startService(startAware);

        Aware.startPlugin(this, getPackageName()); //start our plugin
    }



    BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
            if (status == BatteryManager.BATTERY_STATUS_FULL || status == BatteryManager.BATTERY_STATUS_CHARGING) {
                Log.i("READ ME", "Battery is charging");
            } else {
                Log.i("READ ME", "Battery is not charging");
            }
            //checks the time internet connection to know if data upload is possible
            ConnectivityManager connMgr = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                Log.i("READ ME", "An internet connection is detected; Data upload will commence");
                //upload data to the WS from the databases
                sp=getSharedPreferences(MY_REFERENCES, MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                Log.i("READ ME 1", "last upload for locations was at: " + sp.getLong(lastLocationDbUpload, 0));
                locationDatabaseEntriesSinceTime(sp.getLong(lastLocationDbUpload, 0));
                Log.i("READ ME 1", "last upload for activities was at: " + sp.getLong(lastActivityDbUpload, 0));
                activityDatabaseEntriesSinceTime(sp.getLong(lastActivityDbUpload, 0));

            }
        }
    };



    private void registerBatteryReceiver() {
        batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver, batteryFilter);
    }



    public void locationDatabaseEntriesSinceTime(Long timestamp) {
        int count = 0;
        String deviceID = Algorithm.base64Encoder(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        String[] projection = {
                Locations_Provider.Locations_Data._ID,
                Locations_Provider.Locations_Data.DEVICE_ID,
                Locations_Provider.Locations_Data.TIMESTAMP,
                Locations_Provider.Locations_Data.LATITUDE,
                Locations_Provider.Locations_Data.LONGITUDE
        };
        String selection = Locations_Provider.Locations_Data.TIMESTAMP + ">?";
        String[] selectionArgs = {
                String.valueOf(timestamp)
        };
        try {
            Cursor locationCursor = getContentResolver().query(Locations_Provider.Locations_Data.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null);
            locationCursor.moveToFirst();
            String uploadUrl = "";
            while (!locationCursor.isAfterLast()) {
                count++;
                Double timestampDouble = locationCursor.getDouble(locationCursor.getColumnIndexOrThrow(Locations_Provider.Locations_Data.TIMESTAMP))/1000;

                String latitudeString = String.valueOf(locationCursor.getDouble(locationCursor.getColumnIndexOrThrow(Locations_Provider.Locations_Data.LATITUDE)));
                String longitudeString = String.valueOf(locationCursor.getDouble(locationCursor.getColumnIndexOrThrow(Locations_Provider.Locations_Data.LONGITUDE)));
                uploadUrl = batchBuilderForLocation(uploadUrl, deviceID, scientificNotationConverter(timestampDouble), latitudeString, longitudeString);

                locationCursor.moveToNext();
                if (count%10==0 && !uploadUrl.isEmpty()) {
                    Algorithm.invokeWS(uploadUrl);
                    uploadUrl = "";
                }
            }

            //updates the time of last upload
            sp=getSharedPreferences(MY_REFERENCES, MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putLong(lastLocationDbUpload, (Long) (System.currentTimeMillis()));
            editor.commit();
            Log.i("READ ME ", "Location data last uploaded "+sp.getLong(lastLocationDbUpload, 0));

        } catch (Exception e) {
            Log.i("READ ME", "ERROR DETECTED: " + e);
        }
        Log.i("READ ME", "there are " + count + " location instances that have been recorded since our last data upload");
    }



    public void activityDatabaseEntriesSinceTime(Long timestamp) {
        int count = 0;
        Log.i("READ ME", "get here fine (1)");
        String deviceID = Algorithm.base64Encoder(Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
        String[] projection = {
                Google_AR_Provider.Google_Activity_Recognition_Data._ID,
                Google_AR_Provider.Google_Activity_Recognition_Data.DEVICE_ID,
                Google_AR_Provider.Google_Activity_Recognition_Data.TIMESTAMP,
                Google_AR_Provider.Google_Activity_Recognition_Data.ACTIVITY_NAME,
                Google_AR_Provider.Google_Activity_Recognition_Data.CONFIDENCE,
        };
        String selection = Locations_Provider.Locations_Data.TIMESTAMP + ">?";
        String[] selectionArgs = {
                String.valueOf(timestamp)
        };

        Log.i("READ ME", "get here fine (2)");
        try {
            Cursor locationCursor = getContentResolver().query(Google_AR_Provider.Google_Activity_Recognition_Data.CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    null);
            locationCursor.moveToFirst();
            Log.i("READ ME", "get here fine (3)");
            String uploadUrl = "";
            while (!locationCursor.isAfterLast()) {
                count++;
                Double timestampDouble = locationCursor.getDouble(locationCursor.getColumnIndexOrThrow(Locations_Provider.Locations_Data.TIMESTAMP))/1000;

                String activityString = locationCursor.getString(locationCursor.getColumnIndexOrThrow(Google_AR_Provider.Google_Activity_Recognition_Data.ACTIVITY_NAME));
                Log.i("READ ME", activityString);
                String confidenceString =locationCursor.getString(locationCursor.getColumnIndexOrThrow(Google_AR_Provider.Google_Activity_Recognition_Data.CONFIDENCE));
                uploadUrl = batchBuilderForActivity(uploadUrl, deviceID, scientificNotationConverter(timestampDouble), activityString, confidenceString);

                locationCursor.moveToNext();
                if (count%10==0 && !uploadUrl.isEmpty()) {
                    Algorithm.invokeWS(uploadUrl);
                    uploadUrl = "";
                }
            }

            //updates the time of last upload
            sp=getSharedPreferences(MY_REFERENCES, MODE_PRIVATE);
            SharedPreferences.Editor editor = sp.edit();
            editor.putLong(lastActivityDbUpload, (Long) (System.currentTimeMillis()));
            editor.commit();
            Log.i("READ ME 1", "UPDATING LAST UPLOAD TIME");
            Log.i("READ ME 1 ", "activity data last uploaded "+sp.getLong(lastActivityDbUpload,0));

        } catch (Exception e) {
            Log.i("READ ME", "ERROR DETECTED: " + e);
        }
        Log.i("READ ME", "there are " + count + " activity instances that have been recorded since our last data upload");
    }



    public String batchBuilderForLocation(String uploadString, String deviceID, String timestamp, String latitude, String longitude) {
        //we don't yet have a field for timezone to be stored!
        if (uploadString.isEmpty()) {
            uploadString = "http://ridesharing.cmu-tbank.com/reportActivityForAware.php?userID=1&locations=";
            uploadString = uploadString + deviceID + "@" + timestamp + "@" +
                    Algorithm.base64Encoder(Algorithm.timeZoneBuilder())+ "@" + latitude + "@" + longitude;
        } else {
            uploadString = uploadString + "*" + deviceID + "@" + timestamp + "@" +
                    Algorithm.base64Encoder(Algorithm.timeZoneBuilder())+ "@" + latitude + "@" + longitude;
        }
        Log.i("READ ME", uploadString);
        return uploadString;
    }



    public String batchBuilderForActivity(String uploadString, String deviceID, String timestamp, String activity, String confidence) {
        //we don't yet have a field for timezone to be stored!
        if (uploadString.isEmpty()) {
            uploadString = "http://ridesharing.cmu-tbank.com/reportActivityForAware.php?userID=1&activities=";
            uploadString = uploadString + deviceID + "@" + timestamp + "@" +
                    Algorithm.base64Encoder(Algorithm.timeZoneBuilder())+ "@" + activity + "@" + confidence;
        } else {
            uploadString = uploadString + "*" + deviceID + "@" + timestamp + "@" +
                    Algorithm.base64Encoder(Algorithm.timeZoneBuilder())+ "@" + activity + "@" + confidence;
        }
        Log.i("READ ME", uploadString);
        return uploadString;
    }



    private String scientificNotationConverter(Double timestamp) {
        String timestampString = Double.toString(timestamp);
        String returnValue = "";
        //parse the timestamp into the exponent and the base
        int indexOfE = timestampString.indexOf("E");
        String baseString = "";
        String exponentString = "";
        for (int i = 0; i < timestampString.length(); i++) {
            char c = timestampString.charAt(i);
            if (i < indexOfE) {
                baseString = baseString + c;
            } else if (i > indexOfE) {
                exponentString = exponentString + c;
            }
        }
        int exponentValue = Integer.valueOf(exponentString);
        for (int j = 0; j < exponentValue + 2; j++) {       //exponentValue+2 to account for the 1's digit in sci notation and for '.'
            Character c = timestampString.charAt(j);
            if (Character.isDigit(c)) {
                returnValue = returnValue + c;
            }
        }
        return returnValue;
    }



    @Override
    protected void onDestroy() {
        super.onDestroy();

//        Aware.stopPlugin(this, getPackageName()); //stop plugin if you want it to terminate if you press back button on the activity
    }


}
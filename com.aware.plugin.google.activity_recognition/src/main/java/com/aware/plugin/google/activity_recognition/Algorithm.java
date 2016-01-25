/**
@author: denzil
 */

package com.aware.plugin.google.activity_recognition;

import java.util.List;
import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.util.Date;
import java.util.TimeZone;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.IntentService;
import android.content.ContentValues;
import android.content.Intent;
import android.provider.*;
import android.provider.Settings;
import android.util.Log;
import android.annotation.TargetApi;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.util.Base64;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.plugin.google.activity_recognition.Google_AR_Provider.Google_Activity_Recognition_Data;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;


import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;


public class Algorithm extends IntentService {

    public Algorithm() {
        super(Plugin.TAG);
    }

    public static String offlineUpload = "";

    private static final String SUCCESS = "success";
    private static final String MESSAGE = "message";
    private static final String SERVER_STATUS = "status";

    @Override
    protected void onHandleIntent(Intent intent) {
        // Called when a new intent is registered
        // writes the data locally in a SQLite DB
        // Adds the data to the webservice's DB if there is internet connection


        if (ActivityRecognitionResult.hasResult(intent)) {
            
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

            DetectedActivity mostProbable = result.getMostProbableActivity();
            
            JSONArray activities = new JSONArray();
            List<DetectedActivity> otherActivities = result.getProbableActivities();
            for(DetectedActivity activity : otherActivities ) {
                try {
                    JSONObject item = new JSONObject();
                    item.put("activity", getActivityName(activity.getType()));
                    item.put("confidence", activity.getConfidence());
                    activities.put(item);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            Plugin.current_confidence = mostProbable.getConfidence();
            Plugin.current_activity = mostProbable.getType();
            String activity_name = getActivityName(Plugin.current_activity);

            ContentValues data = new ContentValues();
            data.put(Google_Activity_Recognition_Data.TIMESTAMP, System.currentTimeMillis());
            data.put(Google_Activity_Recognition_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            data.put(Google_Activity_Recognition_Data.ACTIVITY_NAME, activity_name);
            data.put(Google_Activity_Recognition_Data.ACTIVITY_TYPE, Plugin.current_activity);
            data.put(Google_Activity_Recognition_Data.CONFIDENCE, Plugin.current_confidence);
            data.put(Google_Activity_Recognition_Data.ACTIVITIES, activities.toString());

            getContentResolver().insert(Google_Activity_Recognition_Data.CONTENT_URI, data);

            if ( Plugin.DEBUG ) {
            	Log.d(Plugin.TAG, "User is: " + activity_name + " (conf:" + Plugin.current_confidence + ")");
            }

            Intent context = new Intent( Plugin.ACTION_AWARE_GOOGLE_ACTIVITY_RECOGNITION );
            context.putExtra( Plugin.EXTRA_ACTIVITY, Plugin.current_activity );
            context.putExtra( Plugin.EXTRA_CONFIDENCE, Plugin.current_confidence );
            sendBroadcast( context );
            Log.i("README","NEW ACTIVITY DETECTED");
            String deviceName = android.provider.Settings.Secure.getString(getBaseContext().getContentResolver(), Settings.Secure.ANDROID_ID);;
            String stringUrl = "http://ridesharing.cmu-tbank.com/reportActivityForAware.php?userID=1&activities=";
            String instanceInformation = base64Encoder(deviceName)+"@"+System.currentTimeMillis()/1000+"@"+base64Encoder(timeZoneBuilder())+"@"+Plugin.current_activity+"@"+Plugin.current_confidence;
            stringUrl = stringUrl+instanceInformation;
            Log.i("THIS IS FOR ACTIVITIES", stringUrl);

            ConnectivityManager connMgr = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                if (offlineUpload.equals("")) {
                    invokeWS(offlineUpload);
                    offlineUpload = "";
                }
                invokeWS(stringUrl);
            } else {
                //textView.setText("No network connection available.");
                Log.i("READ ME PLEASE", "No connection available");
                if (offlineUpload.length() == 0) {
                    offlineUpload = offlineUpload + stringUrl;
                } else {
                    offlineUpload = offlineUpload +"*"+ instanceInformation;
                }
                Log.i("READ ME PLEASE", offlineUpload);
            }
        }
    }

    public static String getActivityName(int type) {
        //returns the most probable type of activity based on sensor data
        switch (type) {
            case DetectedActivity.IN_VEHICLE:
                return "in_vehicle";
            case DetectedActivity.ON_BICYCLE:
                return "on_bicycle";
            case DetectedActivity.ON_FOOT:
                return "on_foot";
            case DetectedActivity.STILL:
                return "still";
            case DetectedActivity.UNKNOWN:
                return "unknown";
            case DetectedActivity.TILTING:
                return "tilting";
            case DetectedActivity.RUNNING:
                return "running";
            case DetectedActivity.WALKING:
                return "walking";
            default:
                return "unknown";
        }
    }

    public void invokeWS(String addressWS){
        // sends the data to the webservice and shows via toast if the call was successful
        AsyncHttpClient client = new AsyncHttpClient();

        client.get(addressWS ,new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody) {
                try {
                    JSONObject obj = new JSONObject(new String(responseBody));
                    Boolean successStatus = obj.getBoolean(SUCCESS);
                    Log.i("successStatus: ", successStatus.toString());
                    String serverResponse = obj.getJSONObject(MESSAGE).getString(SERVER_STATUS);
                    Toast.makeText(getApplicationContext(), serverResponse, Toast.LENGTH_LONG).show();

                } catch (JSONException e) {
                    Toast.makeText(getApplicationContext(), "Server response might be invalid!", Toast.LENGTH_LONG).show();
                    e.printStackTrace();

                }
            }

            @Override
            public void onFailure(int statusCode, cz.msebera.android.httpclient.Header[] headers, byte[] responseBody, Throwable error) {
                if(statusCode == 404){
                    Toast.makeText(getApplicationContext(), "Requested resource not found", Toast.LENGTH_LONG).show();
                }
                else if(statusCode == 500){
                    Toast.makeText(getApplicationContext(), "Something went wrong at server", Toast.LENGTH_LONG).show();
                }
                else{
                    Toast.makeText(getApplicationContext(), "Device might not be connected to Internet or remote server is not up and running", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    private String timeZoneBuilder() {
        String timeZone = "";
        Date now = new Date();
        int offsetFromUTC = TimeZone.getDefault().getOffset(now.getTime())/3600000;
        if (offsetFromUTC>=0) {
            timeZone = timeZone + "+";
        } else if (offsetFromUTC<0) {
            timeZone = timeZone + "-";
        }
        if (Math.abs(offsetFromUTC)<10) {
            timeZone = timeZone + "0";
        }
        timeZone = timeZone + Integer.toString(Math.abs(offsetFromUTC))+ ":00" ;
        return timeZone;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private String base64Encoder (String stringToEncode) {
        byte[] stringToByte = stringToEncode.getBytes(StandardCharsets.UTF_8);
        String base64 = Base64.encodeToString(stringToByte, Base64.DEFAULT);
        return base64;
    }

}

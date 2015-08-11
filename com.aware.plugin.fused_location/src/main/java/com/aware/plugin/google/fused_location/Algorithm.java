/**
@author: denzil
*/
package com.aware.plugin.google.fused_location;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.*;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Locations_Provider.Locations_Data;
import com.google.android.gms.location.LocationServices;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

import org.apache.http.Header;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.Provider;
import java.util.Date;
import java.util.TimeZone;

public class Algorithm extends IntentService {

    public static String offlineUpload = "";

    private static final String SUCCESS = "success";
    private static final String MESSAGE = "message";
    private static final String SERVER_STATUS = "status";
    public static final String LAST_LOCATION_TIMESTAMP = "lastLocationReport";
    public static final String LAST_ACTIVITY_TIMESTAMP = "lastActivityReport";
    public static final String ACTION_RESULT_RECEIVED = "result_received";
    public static final String EXTRA_RESULT = "result_value";

    public Algorithm() {
        super("Google Fused Location");
    }


    @Override
    protected void onHandleIntent(Intent intent) {

        boolean DEBUG = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEBUG_FLAG).equals("true");

        if (intent != null && intent.hasExtra(LocationServices.FusedLocationApi.KEY_LOCATION_CHANGED)) {

            Location bestLocation = (Location) intent.getExtras().get(LocationServices.FusedLocationApi.KEY_LOCATION_CHANGED);

            if (bestLocation == null) return;

            ContentValues rowData = new ContentValues();
            rowData.put(Locations_Data.TIMESTAMP, System.currentTimeMillis());
            rowData.put(Locations_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
            rowData.put(Locations_Data.LATITUDE, bestLocation.getLatitude());
            rowData.put(Locations_Data.LONGITUDE, bestLocation.getLongitude());
            rowData.put(Locations_Data.BEARING, bestLocation.getBearing());
            rowData.put(Locations_Data.SPEED, bestLocation.getSpeed());
            rowData.put(Locations_Data.ALTITUDE, bestLocation.getAltitude());
            rowData.put(Locations_Data.PROVIDER, bestLocation.getProvider());
            rowData.put(Locations_Data.ACCURACY, bestLocation.getAccuracy());

            getContentResolver().insert(Locations_Data.CONTENT_URI, rowData);

            if (DEBUG) Log.d(Plugin.TAG, "Fused location:" + rowData.toString());
            Log.d(Plugin.TAG, "NEW LOCATION DATA");


            //INSERT CODE HERE

            //check connectivity
           // String deviceName = android.provider.Settings.Secure.getString(getBaseContext().getContentResolver(), Settings.Secure.ANDROID_ID);;
            String deviceName = "testingDataUpload";
            String stringUrl = "http://ridesharing.cmu-tbank.com/reportActivityForAware.php?userID=1&";
            String instanceInformation = "locations="+base64Encoder(deviceName)+"@"+System.currentTimeMillis()/1000+"@"+base64Encoder(timeZoneBuilder())+"@"+bestLocation.getLatitude()+"@"+bestLocation.getLongitude();
            String batchUpload = stringUrl+instanceInformation;
                    Log.i("READ ME PLEASE", batchUpload);
            String singleInstance = "deviceID="+base64Encoder(deviceName)+"&currentTime="+System.currentTimeMillis()/1000+"&timeZone="+base64Encoder(timeZoneBuilder())+"&lat="+bestLocation.getLatitude()+"&lng="+bestLocation.getLongitude();
                Log.i("READ ME PLEASE", "for single " + stringUrl+singleInstance);
            ConnectivityManager connMgr = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            if (networkInfo != null && networkInfo.isConnected()) {
                if (offlineUpload.equals("")) {
                    invokeWS(offlineUpload);
                    offlineUpload = "";
                }
                Log.i("READ ME", "Invoking WS...");
                invokeWS(singleInstance);
            } else {
                //textView.setText("No network connection available.");
                Log.i("THIS IS FOR LOCATIONS", "No connection available");
                if (offlineUpload.length() == 0) {
                    offlineUpload = offlineUpload + batchUpload;
                } else {
                    offlineUpload = offlineUpload +"*"+ instanceInformation;
                }
                Log.i("READ ME PLEASE", offlineUpload);
            }

            Intent locationEvent = new Intent(Plugin.ACTION_AWARE_LOCATIONS);
            sendBroadcast(locationEvent);

        }
    }



    public void invokeWS(String addressWS){
        AsyncHttpClient client = new AsyncHttpClient();

        client.get(addressWS ,new AsyncHttpResponseHandler() {
            @Override
            public void onStart() {
                Log.i("READ ME", "starting WS...");
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                try {
                    JSONObject obj = new JSONObject(new String(responseBody));
                    Boolean successStatus = obj.getBoolean(SUCCESS);
                    Log.i("successStatus: ", successStatus.toString());
                    String serverResponse = obj.getJSONObject(MESSAGE).getString(SERVER_STATUS);
                    Toast.makeText(getApplicationContext(), serverResponse, Toast.LENGTH_LONG).show();
                    Log.i("READ ME", "success!");
                } catch (JSONException e) {
                    Toast.makeText(getApplicationContext(), "Server response might be invalid!", Toast.LENGTH_LONG).show();
                    e.printStackTrace();

                }
            }
            @Override
            public void onFailure(int statusCode, Header [] headers, byte[] responseBody, Throwable error) {
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
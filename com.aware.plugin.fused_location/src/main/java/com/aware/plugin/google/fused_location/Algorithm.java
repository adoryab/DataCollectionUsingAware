/**
@author: denzil
*/
package com.aware.plugin.google.fused_location;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Locations_Provider.Locations_Data;
import com.aware.utils.DatabaseHelper;
import com.google.android.gms.location.LocationServices;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.HttpGet;
import com.loopj.android.http.RequestHandle;


import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

public class Algorithm extends IntentService {
    //bad practice, fix if time
    public static String offlineUpload = "";

    private static final String SUCCESS = "success";
    private static final String MESSAGE = "message";
    private static final String SERVER_STATUS = "status";
    private static final String LAST_LOCATION_REPORT = "lastLocationReport";
    private static final String LAST_REPORT = "lastReport";
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

            //check connectivity
            String deviceName = Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID);
            String stringUrl = "http://ridesharing.cmu-tbank.com/reportActivityForAware.php?userID=1&";
            String singleUpload = "deviceID="+base64Encoder(deviceName)+"&currentTime="+System.currentTimeMillis()/1000+"&timeZone="+base64Encoder(timeZoneBuilder())+"&lat="+bestLocation.getLatitude()+"&lng="+bestLocation.getLongitude();
            String onTheFly = stringUrl+singleUpload;
            String singleInstance = base64Encoder(deviceName)+"@"+System.currentTimeMillis()/1000+"@"+base64Encoder(timeZoneBuilder())+"@"+bestLocation.getLatitude()+"@"+bestLocation.getLongitude();
                    Log.i("READ ME for LOCATIONS", stringUrl);

            ConnectivityManager connMgr = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            // first sends an empty request to the webservice to find the last timestamp
            if (networkInfo != null && networkInfo.isConnected()) {
                if (!offlineUpload.equals("")) {
                    invokeWS(offlineUpload);
                    offlineUpload = "";
                }
                invokeWS(onTheFly);
            } else {
                //textView.setText("No network connection available.");
                Log.i("READ ME", "THIS IS FOR LOCATIONS" + "No connection available");
                if (offlineUpload.length() == 0) {
                    offlineUpload = offlineUpload + stringUrl + "locations" + singleInstance;
                } else {
                    offlineUpload = offlineUpload +"*"+ singleInstance;
                }
                Log.i("READ ME PLEASE", offlineUpload);
            }

            Intent locationEvent = new Intent(Plugin.ACTION_AWARE_LOCATIONS);
            sendBroadcast(locationEvent);

        }
    }


    //Uploads data as well as returning the string of the last timestamp
    public static void invokeWS(String addressWS){
        AsyncHttpClient client = new AsyncHttpClient();

        client.get(addressWS ,new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header [] headers, byte[] responseBody) {
                try {
                    JSONObject obj = new JSONObject(new String(responseBody));
                    Boolean successStatus = obj.getBoolean(SUCCESS);
                    Log.i("READ ME", "successStatus: " + successStatus.toString());
                    String serverResponse = obj.getJSONObject(MESSAGE).getString(SERVER_STATUS);

                    String lastTimestamp = obj.getJSONObject(MESSAGE).getJSONObject(LAST_LOCATION_REPORT).getString(LAST_REPORT);
                    Log.i("READ ME timestamp", lastTimestamp);


                } catch (JSONException e) {
                    Log.i("READ ME", "Server response might be invalid!");
                    e.printStackTrace();

                }
            }
            @Override
            public void onFailure(int statusCode, Header [] headers, byte[] responseBody, Throwable error) {
                if(statusCode == 404){
                    Log.i("READ ME", "Requested resource not found");
                }
                else if(statusCode == 500){
                    Log.i("READ ME", "Something went wrong at server");
                }
                else{
                    Log.i("READ ME", "Device might not be connected to Internet or remote server is not up and running");
                }
            }

        });
    }





    public static String timeZoneBuilder() {
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
    public static String base64Encoder (String stringToEncode) {
        byte[] stringToByte = stringToEncode.getBytes(StandardCharsets.UTF_8);
        return Base64.encodeToString(stringToByte, Base64.DEFAULT);
    }

}
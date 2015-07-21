/**
@author: denzil
*/
package com.aware.plugin.google.fused_location;

import android.annotation.TargetApi;
import android.app.IntentService;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Locations_Provider.Locations_Data;
import com.aware.utils.DatabaseHelper;
import com.google.android.gms.location.LocationServices;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestHandle;


import org.apache.http.Header;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;

public class Algorithm extends IntentService {
    //bad practice, fix if time
    public static String offlineUpload = "";
    public static String lastEntryTimestamp = "0";
    public static Integer uploadCount = 0;

    private static final String SUCCESS = "success";
    private static final String MESSAGE = "message";
    private static final String SERVER_STATUS = "status";
    private static final String LAST_LOCATION_REPORT = "lastLocationReport";
    private static final String LAST_REPORT = "lastReport";

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
            uploadCount ++;

            if (DEBUG) Log.d(Plugin.TAG, "Fused location:" + rowData.toString());
            Log.d(Plugin.TAG, "NEW LOCATION DATA");

            //check connectivity
            String deviceName = android.provider.Settings.Secure.getString(getBaseContext().getContentResolver(), Settings.Secure.ANDROID_ID);
            String stringUrl = "http://ridesharing.cmu-tbank.com/reportActivityForAware.php?userID=1&locations=";
            String instanceInformation = base64Encoder(deviceName)+"@"+System.currentTimeMillis()/1000+"@"+base64Encoder(timeZoneBuilder())+"@"+bestLocation.getLatitude()+"@"+bestLocation.getLongitude();
            stringUrl = stringUrl+instanceInformation;
                    Log.i("THIS IS FOR LOCATIONS", stringUrl);

            ConnectivityManager connMgr = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
            // first sends an empty request to the webservice to find the last timestamp
            if (networkInfo != null && networkInfo.isConnected()) {
                Log.i("THIS IS FOR LOCATIONS", "connected to the internet");
                invokeWS("http://ridesharing.cmu-tbank.com/reportActivityForAware.php?userID=1&locations=base64Encoder(deviceName)");
                Log.i("THIS IS FOR LOCATIONS", "number of upload entries is: "+uploadCount.toString());
                Log.i("THIS IS FOR LOCATIONS","OLD "+lastEntryTimestamp);
                //check if DbEntries > uploadEntries
                /*
                if (databaseEntriesSinceTime(lastEntryTimestamp).size()>uploadCount) {
                    //read the database
                }
                */
                if (!offlineUpload.equals("")) {
                    invokeWS(offlineUpload);
                    offlineUpload = "";
                    uploadCount = 0;
                }
                invokeWS(stringUrl);
                uploadCount = 0;
            } else {
                //textView.setText("No network connection available.");
                Log.i("THIS IS FOR LOCATIONS", "No connection available");
                if (offlineUpload.length() == 0) {
                    offlineUpload = offlineUpload + stringUrl;
                } else {
                    offlineUpload = offlineUpload +"*"+ instanceInformation;
                }
                Log.i("READ ME PLEASE", offlineUpload);
            }

            Intent locationEvent = new Intent(Plugin.ACTION_AWARE_LOCATIONS);
            sendBroadcast(locationEvent);

        }
    }


    //Uploads data as well as returning the string of the last timestamp
    public void invokeWS(String addressWS){;
        AsyncHttpClient client = new AsyncHttpClient();

        RequestHandle requestHandle = client.get(addressWS, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                try {
                    JSONObject obj = new JSONObject(new String(responseBody));
                    Boolean successStatus = obj.getBoolean(SUCCESS);
                    Log.i("THIS IS FOR LOCATIONS: ", successStatus.toString());
                    String serverResponse = obj.getJSONObject(MESSAGE).getString(SERVER_STATUS);
                    Toast.makeText(getApplicationContext(), serverResponse, Toast.LENGTH_LONG).show();
                    lastEntryTimestamp = obj.getJSONObject(MESSAGE).getJSONObject(LAST_LOCATION_REPORT).getString(LAST_REPORT);
                  //  lastEntryTimestamp = lastTimestamp;
                    Log.i("THIS IS FOR LOCATIONS", lastEntryTimestamp);
                } catch (JSONException e) {
                    Log.i("THIS IS FOR LOCATIONS", "fucked up");
                    Toast.makeText(getApplicationContext(), "Server response might be invalid!", Toast.LENGTH_LONG).show();
                    e.printStackTrace();
                }
            }

            @Override
            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                if (statusCode == 404) {
                    Toast.makeText(getApplicationContext(), "Requested resource not found", Toast.LENGTH_LONG).show();
                } else if (statusCode == 500) {
                    Toast.makeText(getApplicationContext(), "Something went wrong at server", Toast.LENGTH_LONG).show();
                } else {
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
        return Base64.encodeToString(stringToByte, Base64.DEFAULT);
    }

    //reading from database as a check

    public class LocationsDataDbHelper extends SQLiteOpenHelper {
        public static final String SQL_CREATE_ENTRIES = "CREATE TABLE locations (_id integer primary key autoincrement,timestamp real default 0,device_id text default '',double_latitude real default 0,double_longitude real default 0,double_bearing real default 0,double_speed real default 0,double_altitude real default 0,provider text default '',accuracy real default 0,label text default '',UNIQUE(timestamp,device_id))";
        private static final String SQL_DELETE_ENTRIES = "DROP TABLE IF EXISTS " + Locations_Data._ID;
        // If you change the database schema, you must increment the database version.
        public static final int DATABASE_VERSION = 1;
        public static final String DATABASE_NAME = "locations.db";

        public LocationsDataDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SQL_CREATE_ENTRIES);
        }
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // This database is only a cache for online data, so its upgrade policy is
            // to simply to discard the data and start over
            db.execSQL(SQL_DELETE_ENTRIES);
            onCreate(db);
        }
        public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            onUpgrade(db, oldVersion, newVersion);
        }
    }

    public ArrayList<String> databaseEntriesSinceTime(String timestamp) {
        ArrayList<String> count = new ArrayList<String>();
        LocationsDataDbHelper mDbHelper = new LocationsDataDbHelper(getApplicationContext());
        String deviceName = android.provider.Settings.Secure.getString(getBaseContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String[] projection = { Locations_Data._ID, Locations_Data.TIMESTAMP};
        String whereClause = "timestamp>?";
        String[] whereArgs = new String[]{timestamp};
        String sortOrder = Locations_Data.TIMESTAMP + " DESC ";
        Cursor c = db.query(
                "locations.db",
                projection,
                whereClause,
                whereArgs,
                null,
                null,
                sortOrder
        );
        c.moveToFirst();
        int indexOfLogID = c.getInt(c.getColumnIndex(Locations_Data._ID));
        while (!c.isAfterLast()) {
            count.add(c.getString(indexOfLogID));

            Log.i("READ ME", c.getString(indexOfLogID));
            c.moveToNext();
        }
        return count;
    }
    /*
    public void uploadFromDatabase(ArrayList<String> primaryKeys) {
        String argumentString = primaryKeys.get(0);
        for (int i=1; i < primaryKeys.size(); i++ ) {

        }
        LocationsDataDbHelper mDbHelper = new LocationsDataDbHelper(getApplicationContext());
        String deviceName = android.provider.Settings.Secure.getString(getBaseContext().getContentResolver(), Settings.Secure.ANDROID_ID);
        SQLiteDatabase db = mDbHelper.getReadableDatabase();
        String whereClause = "_id=?";
        String[] whereArgs = primaryKeys;
        String sortOrder = Locations_Data.TIMESTAMP + " DESC ";
        Cursor c = db.query(
                "locations.db",
                null,
                whereClause,
                whereArgs,
                null,
                null,
                sortOrder
        );
        c.moveToFirst();

    }
    */
}
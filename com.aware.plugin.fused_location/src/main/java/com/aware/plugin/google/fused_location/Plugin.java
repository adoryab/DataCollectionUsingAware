
package com.aware.plugin.google.fused_location;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.providers.Locations_Provider;
import com.aware.providers.Locations_Provider.Locations_Data;
import com.aware.utils.Aware_Plugin;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

/**
 * Fused location service for Aware framework
 * Requires Google Services API available on the device.
 * @author denzil
 */
public class Plugin extends Aware_Plugin implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    
    /**
     * Broadcasted event: new location available
     */
    public static final String ACTION_AWARE_LOCATIONS = "ACTION_AWARE_LOCATIONS";
    
    //holds accuracy and frequency parameters
    private final static LocationRequest mLocationRequest = new LocationRequest();
    private PendingIntent pIntent = null;
    private GoogleApiClient mLocationClient = null;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        TAG = "AWARE::Google Fused Location";
        DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

        DATABASE_TABLES = Locations_Provider.DATABASE_TABLES;
        TABLES_FIELDS = Locations_Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ Locations_Data.CONTENT_URI };
        
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                Intent context = new Intent( ACTION_AWARE_LOCATIONS );
                sendBroadcast(context);
            }
        };
        
        Aware.setSetting(this, Settings.STATUS_GOOGLE_FUSED_LOCATION, true);
        if( Aware.getSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION).length() == 0 ) {
            Aware.setSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION, Settings.update_interval);
        } else {
            Aware.setSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION, Aware.getSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION));
        }
        
        if( Aware.getSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION).length() == 0) {
            Aware.setSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION, Settings.max_update_interval);
        } else {
            Aware.setSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION, Aware.getSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION));
        }
        
        if( Aware.getSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION).length() == 0) {
            Aware.setSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION, Settings.location_accuracy);
        } else {
            Aware.setSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION, Aware.getSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION));
        }
        
        mLocationRequest.setPriority(Integer.parseInt(Aware.getSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION)));
        mLocationRequest.setInterval(Long.parseLong(Aware.getSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION)) * 1000);
        mLocationRequest.setFastestInterval(Long.parseLong(Aware.getSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION)) * 1000);

        if( ! is_google_services_available() ) {
            Log.e(TAG,"Google Services fused location is not available on this device.");
            stopSelf();
        } else {
            Intent locationIntent = new Intent(this, com.aware.plugin.google.fused_location.Algorithm.class);
            pIntent = PendingIntent.getService(this, 0, locationIntent, PendingIntent.FLAG_UPDATE_CURRENT);

            mLocationClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mLocationClient.connect();

        if( mLocationClient.isConnected() ) {
            mLocationRequest.setPriority(Integer.parseInt(Aware.getSetting(this, Settings.ACCURACY_GOOGLE_FUSED_LOCATION)));
            mLocationRequest.setInterval(Long.parseLong(Aware.getSetting(this, Settings.FREQUENCY_GOOGLE_FUSED_LOCATION)) * 1000);
            mLocationRequest.setFastestInterval(Long.parseLong(Aware.getSetting(this, Settings.MAX_FREQUENCY_GOOGLE_FUSED_LOCATION)) * 1000);
            LocationServices.FusedLocationApi.requestLocationUpdates(mLocationClient, mLocationRequest, pIntent);
        }
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Aware.setSetting(this, Settings.STATUS_GOOGLE_FUSED_LOCATION, false);

        if( mLocationClient != null ) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mLocationClient, pIntent);
        }

        Aware.stopPlugin(this, "com.aware.plugin.google.fused_location");
    }
    
    private boolean is_google_services_available() {
        return (ConnectionResult.SUCCESS == GooglePlayServicesUtil.isGooglePlayServicesAvailable(getApplicationContext()));
    }

    @Override
    public void onConnectionFailed(ConnectionResult connection_result ) {
        if( DEBUG ) Log.w(TAG, "Error connecting to Google Fused Location services, will try again in 5 minutes");
    }

    @Override
    public void onConnected(Bundle arg0) {
        Log.i(TAG,"Connected to Google's Location API");
        Aware.startPlugin(this, "com.aware.plugin.google.fused_location");
    }

    @Override
    public void onConnectionSuspended(int i) {
        if( DEBUG ) Log.w(TAG,"Error connecting to Google Fused Location services, will try again in 5 minutes");
    }
}

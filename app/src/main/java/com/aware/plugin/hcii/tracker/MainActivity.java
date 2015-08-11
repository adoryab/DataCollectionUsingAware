package com.aware.plugin.hcii.tracker;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Bundle;
import android.util.Log;

import com.aware.Aware;
import com.aware.plugin.google.fused_location.Algorithm;

/**
 * Created by denzil on 30/06/15.
 */
public class MainActivity extends Activity {
//
    public static final String lastDbUpload = "last_database_upload";
    public static SharedPreferences sp;
    private static final String MY_REFERENCES = "MyRefs";
    private int significantTime = 60; //recorded in seconds

    private IntentFilter batteryFilter;
    private int status;

    BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            status = intent.getIntExtra(BatteryManager.EXTRA_STATUS,-1);
            if (status == BatteryManager.BATTERY_STATUS_FULL || status == BatteryManager.BATTERY_STATUS_CHARGING) {
                Log.i("READ ME", "Battery is charging");
            } else {
                Log.i("READ ME", "Battery is not charging");
            }
            //checks the time to see if sufficient time has elapsed for a complete data upload
            Log.i("READ ME: act after: ", ""+sp.getInt(lastDbUpload, 0));
            if (((System.currentTimeMillis()/1000) - sp.getInt(lastDbUpload,0)) > significantTime) {
                Log.i("READ ME", "A significant amount of time has elapsed since our last upload");
                //upload data to the WS from the databases

                //updates the time of last upload
                sp=getSharedPreferences(MY_REFERENCES, MODE_PRIVATE);
                SharedPreferences.Editor editor = sp.edit();
                editor.putInt(lastDbUpload, (int) (System.currentTimeMillis() / 1000));
                editor.commit();
            }
        }
    };

    private void registerBatteryReceiver() {
        batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(batteryReceiver,batteryFilter);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        registerBatteryReceiver();


        //used to initialize the database time tracker, use sp to keep in persistent memory
        sp=getSharedPreferences(MY_REFERENCES, MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt(lastDbUpload, (int) (System.currentTimeMillis() / 1000));
        editor.commit();
        setContentView(R.layout.main_activity);

        Intent startAware = new Intent(this, Aware.class);
        startService(startAware);

        Aware.startPlugin(this, getPackageName()); //start our plugin
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();

//        Aware.stopPlugin(this, getPackageName()); //stop plugin if you want it to terminate if you press back button on the activity
    }
}

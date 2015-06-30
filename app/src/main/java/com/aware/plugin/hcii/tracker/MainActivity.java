package com.aware.plugin.hcii.tracker;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.aware.Aware;

/**
 * Created by denzil on 30/06/15.
 */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.main_activity);

        Intent startAware = new Intent(this, Aware.class);
        startService(startAware);

        Aware.startPlugin(this, getPackageName()); //set ourselves
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Aware.stopPlugin(this, getPackageName()); //stop plugin if you want it to terminate if you press back button on the activity
    }
}

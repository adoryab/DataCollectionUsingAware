package hcii.tracker;

import android.content.Context;
import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.aware.Aware;
import com.aware.Aware_Preferences;


public class MainActivity extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d("ACTIVITY", "ACT created!");

        Context context = this;
        //Activate Accelerometer
        /*Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER, true);
        //Set sampling frequency
        Aware.setSetting(this, Aware_Preferences.FREQUENCY_ACCELEROMETER, 200000);
        //Apply settings
        sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
        //Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.activity_recognition");
        Aware.startPlugin(context, "com.aware.plugin.google.activity_recognition");
        */
        //MainService main = new MainService();
        //main.startService();

        //Intent i= new Intent(context, MainService.class);
        //context.startService(i);

        //startService(new Intent(this, MainService.class));

        //Intent mServiceIntent = new Intent(this, MainService.class);
        //this.startService(mServiceIntent);

       //Context context = this;
        //Aware.startPlugin(context, "com.aware.plugin.google.activity_recognition");
        Log.d("ACTIVITY", "Got to service");

        startService(new Intent(this, MainService.class));


    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

package hcii.tracker;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;

/**s
 * Created by Connor on 6/25/2015.
 */
public class MainService extends Service {


    public static final Uri ACTIVITY_URI = Uri.parse("content://com.aware.plugin.google.activity_recognition.provider/plugin_google_activity_recognition");


   public void onCreate(){

       Log.d("SERVICE", "Service created!");

       Context context = this;
       //Activate Accelerometer
       Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER, true);
       //Set sampling frequency
       Aware.setSetting(this, Aware_Preferences.FREQUENCY_ACCELEROMETER, 200000);
       //Apply settings
       sendBroadcast(new Intent(Aware.ACTION_AWARE_REFRESH));
       //Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.activity_recognition");
       //Activate programmatically any sensors/plugins you need here
       //e.g., Aware.setSetting(this, Aware_Preferences.STATUS_ACCELEROMETER,true);

       Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.activity_recognition");

       Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.fused_location");

       Aware.setSetting(getApplicationContext(), "frequency_google_fused_location", 60,
               "com.aware.plugin.google.fused_location");

       Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.fused_location");

       ActivityRecognitionObserver so = new ActivityRecognitionObserver(new Handler());
       getContentResolver().registerContentObserver(ACTIVITY_URI, true, so);
       Log.d("SERVICE", "Observer registered");

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class ActivityRecognitionObserver extends ContentObserver {

        public Uri CONTENT_URI = Uri.parse("content://com.aware.plugin.google.activity_recognition.provider/plugin_google_activity_recognition");

        public ActivityRecognitionObserver(Handler handler) {
            super(handler);
        }

        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            // Get the latest recorded value

            Log.d("OBSERVER", "Change in activity data detected");
            Cursor activity = getContentResolver().query(CONTENT_URI, null, null, null,
                    "activity_name" + "DESC LIMIT 1");
            if( activity != null && activity.moveToFirst() ) {
                // Here we read the value
                String activity_name = activity.getString(activity.getColumnIndex("activity_name"));

                if (activity_name.equals("in_vehicle")){
                    Aware.setSetting(getApplicationContext(), "frequency_google_fused_location", 60,
                            "com.aware.plugin.google.activity_recognition");
                    Log.d("OBSERVER", "Recognized in vehicle");
                    Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.activity_recognition");
                }
                else {
                    Aware.setSetting(getApplicationContext(), "frequency_google_fused_location", 180,
                            "com.aware.plugin.google.activity_recognition");
                    Log.d("OBSERVER", "Recognized on foot");
                    Aware.startPlugin(getApplicationContext(), "com.aware.plugin.google.activity_recognition");
                }
            }
            if( activity != null && ! activity.isClosed() ) activity.close();

        }

    }
}

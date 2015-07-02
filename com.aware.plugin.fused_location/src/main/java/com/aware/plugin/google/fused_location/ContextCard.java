package com.aware.plugin.google.fused_location;

import android.content.Context;
import android.database.Cursor;
import android.location.Address;
import android.location.Geocoder;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.aware.providers.Locations_Provider;
import com.aware.utils.IContextCard;

import java.io.IOException;
import java.util.List;

public class ContextCard implements IContextCard {
    public ContextCard(){};

    @Override
    public View getContextCard(Context context) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View card = (View) inflater.inflate(R.layout.card, null);

        TextView address = (TextView) card.findViewById(R.id.address);
        Cursor last_location = context.getContentResolver().query(Locations_Provider.Locations_Data.CONTENT_URI, null, null, null, Locations_Provider.Locations_Data.TIMESTAMP + " DESC LIMIT 1");
        if( last_location != null && last_location.moveToFirst() ) {
            double lat = last_location.getDouble(last_location.getColumnIndex(Locations_Provider.Locations_Data.LATITUDE));
            double lon = last_location.getDouble(last_location.getColumnIndex(Locations_Provider.Locations_Data.LONGITUDE));

            try {
                Geocoder geo = new Geocoder(context);

                String geo_text = "";
                List<Address> addressList = geo.getFromLocation(lat, lon, 1);
                for(int i = 0; i<addressList.size(); i++ ) {
                    Address address1 = addressList.get(i);
                    for( int j = 0; j< address1.getMaxAddressLineIndex(); j++ ) {
                        geo_text += address1.getAddressLine(j) + "\n";
                    }
                }
                address.setText(geo_text);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if( last_location != null && ! last_location.isClosed() ) last_location.close();

        return card;
    }
}

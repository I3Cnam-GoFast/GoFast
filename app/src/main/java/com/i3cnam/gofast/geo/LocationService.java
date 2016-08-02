package com.i3cnam.gofast.geo;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.content.ContextCompat;

import com.google.android.gms.maps.model.LatLng;

import java.util.List;

/**
 * Created by Nestor on 15/07/2016.
 */
public class LocationService {

    public static LatLng getActualLocation(Context context) {
//        return new LatLng(43.6032661, 1.4422609);

        // Get the location manager

        Location loc = null;

        if (ContextCompat.checkSelfPermission( context, android.Manifest.permission.ACCESS_FINE_LOCATION ) == PackageManager.PERMISSION_GRANTED ) {
            System.out.println("=============PERMISSION OK=============");
            LocationManager mgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            List<String> providers = mgr.getProviders(true);
            System.out.println(providers);
            if (providers != null && providers.contains(LocationManager.GPS_PROVIDER)) {
                 loc = mgr.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (loc != null) {
                    System.out.println(loc.getLatitude() + "*" + loc.getLongitude());
                }
            }
        }
        else {
            System.out.println("=============PERMISSION KO =============");
        }


        Double lat,lon;
        try {
            lat = loc.getLatitude ();
            lon = loc.getLongitude ();
            return new LatLng(lat, lon);
        }
        catch (NullPointerException e){
            e.printStackTrace();
            return new LatLng(43.6032661, 1.4422609);
        }

    }

}

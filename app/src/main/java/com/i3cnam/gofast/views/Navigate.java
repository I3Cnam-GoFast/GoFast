package com.i3cnam.gofast.views;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;
import com.i3cnam.gofast.R;
import com.i3cnam.gofast.management.course.CourseManagementService;
import com.i3cnam.gofast.model.Carpooling;
import com.i3cnam.gofast.model.DriverCourse;
import com.i3cnam.gofast.model.Place;
import com.i3cnam.gofast.model.User;

import java.util.List;


public class Navigate extends AppCompatActivity implements OnMapReadyCallback {

    public final static String COURSE = "com.i3cnam.gofast.COURSE";
    CourseManagementService myService;
    boolean isBound = false;
    GoogleMap mMap;
    Polyline pathPolyline;
    Marker homeMarker;
    Marker destinationMarker;
    DriverCourse driverCourse;
    boolean restartByMain = false;

    private final static String TAG_LOG = "Navigate view";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigate);

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // get params from Intent
        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();

        driverCourse = new DriverCourse();
        if (bundle != null) {
            // instanciate driver course
            driverCourse.setOrigin((Place) bundle.getSerializable(DestinationMap.ORIGIN));
            driverCourse.setDestination((Place) bundle.getSerializable(EnterDestination.DESTINATION));
            driverCourse.setDriver(User.getMe(this));
            driverCourse.setEncodedPoints(intent.getStringExtra(DestinationMap.ENCODED_POINTS));
        }

        // launch and bind CourseManagementService
        launchAndBindCourseManagementService(driverCourse);

    }

    @Override
    public void onBackPressed() {
        final Context context = this;

        new AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle(R.string.titleAbortCourseDialog)
            .setMessage(R.string.textAbortCourseDialog)
            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    restartByMain = true;
                    Intent intent = new Intent(context, Main.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                }
            })
            .setNegativeButton(R.string.no, null)
            .show();
    }

    private void launchAndBindCourseManagementService(DriverCourse driverCourse)  {
        // new intent for publication:
        Intent serviceIntent = new Intent(Navigate.this, CourseManagementService.class);
        // new bundle
        Bundle serviceBundle = new Bundle();
        serviceBundle.putSerializable(COURSE, driverCourse);
        serviceIntent.putExtras(serviceBundle);
        // start service with th intent and bind it
        startService(serviceIntent);
        Log.d(TAG_LOG, "Bind Service");
        bindService(serviceIntent, myConnection, Context.BIND_AUTO_CREATE);

    }

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection myConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            CourseManagementService.LocalBinder binder = (CourseManagementService.LocalBinder) service;
            myService = binder.getService();
            isBound = true;
            driverCourse = myService.getDriverCourse();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            isBound = false;
        }
    };

    @Override
    protected void onDestroy() {
        Log.d(TAG_LOG, "DESTROY");

        unbindService(myConnection);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        Log.d(TAG_LOG, "RESUME");

        // for the course changes
        IntentFilter courseFilter = new IntentFilter();
        courseFilter.addAction(CourseManagementService.BROADCAST_UPDATE_COURSE_ACTION);
        registerReceiver(broadcastCourseReceiver, courseFilter);

        // for the carpooling request changes
        IntentFilter carpoolingFilter = new IntentFilter();
        carpoolingFilter.addAction(CourseManagementService.BROADCAST_UPDATE_CARPOOLING_ACTION);
        registerReceiver(broadcastCarpoolingReceiver, carpoolingFilter);

        super.onResume();
    }

    @Override
    protected void onPause() {
        Log.d(TAG_LOG, "PAUSE");
        unregisterReceiver(broadcastCarpoolingReceiver);
        unregisterReceiver(broadcastCourseReceiver);

        // save current activity as last activity opened
        SharedPreferences prefs = getSharedPreferences("X", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        if (restartByMain) {
            editor.remove("lastActivity");
        }
        else {
            editor.putString("lastActivity", getClass().getName());
        }
        editor.commit();

        super.onPause();
    }

    /** Boutons de test */
    public void acceptCarpool(View view) {
        Log.d(TAG_LOG, "acceptCarpool");
        myService.acceptCarpooling(myService.getRequestedCarpoolings().get(0));
    }

    public void refuseCarpool(View view) {
        Log.d(TAG_LOG, "refuseCarpool");
        myService.refuseCarpooling(myService.getRequestedCarpoolings().get(0));
    }

    public void abortCarpooling(View view) {
        Log.d(TAG_LOG, "abortCarpooling");
        myService.abortCarpooling(myService.getRequestedCarpoolings().get(0));
    }



    private BroadcastReceiver broadcastCourseReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO
            Log.d("BroadcastReceiver", "Broadcast received");
            Toast.makeText(getApplicationContext(), "Course received", Toast.LENGTH_SHORT).show();

            Toast.makeText(getApplicationContext(), "New position is : \n" + myService.getDriverCourse().getActualPosition(), Toast.LENGTH_LONG).show();

            homeMarker.setPosition(myService.getDriverCourse().getActualPosition());
            pathPolyline.setPoints(PolyUtil.decode(myService.getDriverCourse().getEncodedPoints()));
        }
    };

    private BroadcastReceiver broadcastCarpoolingReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO
            Log.d("BroadcastReceiver", "Broadcast received");
            Toast.makeText(getApplicationContext(), "Carpooling received", Toast.LENGTH_SHORT).show();

            // TODO
            // DO SOMETHING
            String s;
            for (Carpooling c : myService.getRequestedCarpoolings()) {
                s = "Carpooling " + c.getId() + "\n" +
                        "pick up: " + c.getPickupPoint() + "\n" +
                        "drop off: " + c.getDropoffPoint() + "\n" +
                        "time: " + c.getPickupTime() + "\n" +
                        "state: " + c.getState() + "\n" +
                        "fare: " + c.getFare() + "\n";

                Log.d("BroadcastReceiver", s);
                Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
            }
        }
    };


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        LatLng actualPosition = driverCourse.getOrigin().getCoordinates();
        Place destination = driverCourse.getDestination();

        // set the ORIGIN marker
        homeMarker = googleMap.addMarker(new MarkerOptions().position(actualPosition).title(getResources().getString(R.string.origin_title)));
        // set the destination marker
        destinationMarker = mMap.addMarker(new MarkerOptions().position(destination.getCoordinates())
                .title(getResources().getString(R.string.destination_title))
                .snippet(destination.getPlaceName()));
        destinationMarker.showInfoWindow();

        // draw the path
        List<LatLng> pathPoints = PolyUtil.decode(driverCourse.getEncodedPoints());
        pathPolyline = mMap.addPolyline(new PolylineOptions());
        pathPolyline.setPoints(pathPoints);
        homeMarker.setIcon(BitmapDescriptorFactory.fromResource(R.drawable.driver_50));
    }
}
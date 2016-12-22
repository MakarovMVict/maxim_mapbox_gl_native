package com.mapbox.mapboxsdk.location;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.mapbox.mapboxsdk.telemetry.TelemetryLocationReceiver;
import com.mapzen.android.lost.api.LocationRequest;
import com.mapzen.android.lost.api.LostApiClient;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages locational updates. Contains methods to register and unregister location listeners.
 * <ul>
 * <li>You can register a {@link LocationListener} with {@link #addLocationListener(LocationListener)} to receive location updates.</li>
 * <li> You can unregister a {@link LocationListener} with {@link #removeLocationListener(LocationListener)}.</li>
 * </ul>
 * <p>
 * Note: If registering a listener in your Activity.onResume() implementation, you should unregister it in Activity.onPause().
 * (You won't receive location updates when paused, and this will cut down on unnecessary system overhead).
 * Do not unregister in Activity.onSaveInstanceState(), because this won't be called if the user moves back in the history stack.
 * </p>
 */
public class LocationServices implements com.mapzen.android.lost.api.LocationListener {

    private static final String TAG = "LocationServices";

    private static LocationServices instance;

    private Context context;
    private LostApiClient locationClient;
    private Location lastLocation;

    private CopyOnWriteArrayList<LocationListener> locationListeners;

    private boolean isGPSEnabled;

    /**
     * Private constructor for singleton LocationServices
     */
    private LocationServices(Context context) {
        super();
        this.context = context;
        // Setup location services
        locationClient = new LostApiClient.Builder(context).build();
        locationListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Primary (singleton) access method for LocationServices
     *
     * @param context Context
     * @return LocationServices
     */
    public static LocationServices getLocationServices(@NonNull final Context context) {
        if (instance == null) {
            instance = new LocationServices(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * Enabled / Disable GPS focused location tracking
     *
     * @param enableGPS true if GPS is to be enabled, false if GPS is to be disabled
     */
    public void toggleGPS(boolean enableGPS) {
        if (!areLocationPermissionsGranted()) {
            Log.w(TAG, "Location Permissions Not Granted Yet.  Try again after requesting.");
            return;
        }

        // Disconnect
        if (locationClient.isConnected()) {
            // Disconnect first to ensure that the new requests are GPS
            com.mapzen.android.lost.api.LocationServices.FusedLocationApi.removeLocationUpdates(this);
            locationClient.disconnect();
        }

        // Setup Fresh
        locationClient.connect();
        Location lastLocation = com.mapzen.android.lost.api.LocationServices.FusedLocationApi.getLastLocation();
        if (lastLocation != null) {
            this.lastLocation = lastLocation;
        }

        LocationRequest locationRequest;

        if (enableGPS) {
            // LocationRequest Tuned for GPS
            locationRequest = LocationRequest.create()
                    .setFastestInterval(1000)
                    .setSmallestDisplacement(3.0f)
                    .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

            com.mapzen.android.lost.api.LocationServices.FusedLocationApi.requestLocationUpdates(locationRequest, this);
        } else {
            // LocationRequest Tuned for PASSIVE
            locationRequest = LocationRequest.create()
                    .setFastestInterval(1000)
                    .setSmallestDisplacement(3.0f)
                    .setPriority(LocationRequest.PRIORITY_NO_POWER);

            com.mapzen.android.lost.api.LocationServices.FusedLocationApi.requestLocationUpdates(locationRequest, this);
        }

        isGPSEnabled = enableGPS;
    }

    /**
     * Returns if the GPS sensor is currently enabled
     *
     * @return active state of the GPS
     */
    public boolean isGPSEnabled() {
        return isGPSEnabled;
    }

    /**
     * Called when the location has changed.
     *
     * @param location The updated location
     */
    @Override
    public void onLocationChanged(Location location) {
//        Log.d(TAG, "onLocationChanged()..." + location);
        this.lastLocation = location;

        // Update Listeners
        for (LocationListener listener : this.locationListeners) {
            listener.onLocationChanged(location);
        }

        Intent locIntent = new Intent(TelemetryLocationReceiver.INTENT_STRING);
        locIntent.putExtra(LocationManager.KEY_LOCATION_CHANGED, location);
        LocalBroadcastManager.getInstance(context.getApplicationContext()).sendBroadcast(locIntent);
    }

    /**
     * Last known location
     *
     * @return Last known location data
     */
    public Location getLastLocation() {
        return lastLocation;
    }

    /**
     * Registers a LocationListener to receive location updates
     *
     * @param locationListener LocationListener
     */
    public void addLocationListener(@NonNull LocationListener locationListener) {
        if (!this.locationListeners.contains(locationListener)) {
            this.locationListeners.add(locationListener);
        }
    }

    /**
     * Unregister a LocationListener to stop receiving location updates
     *
     * @param locationListener LocationListener to remove
     * @return True if LocationListener was found and removed, False if it was not
     */
    public boolean removeLocationListener(@NonNull LocationListener locationListener) {
        return this.locationListeners.remove(locationListener);
    }

    /**
     * Check status of Location Permissions
     * @return True if granted to the app, False if not
     */
    public boolean areLocationPermissionsGranted() {
        if ((ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) &&
                (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)) {
            Log.w(TAG, "Location Permissions Not Granted Yet.  Try again after requesting.");
            return false;
        }
        return true;
    }
}

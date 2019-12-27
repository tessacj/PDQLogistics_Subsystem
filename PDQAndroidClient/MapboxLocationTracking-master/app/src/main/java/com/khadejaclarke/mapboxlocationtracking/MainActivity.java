package com.khadejaclarke.mapboxlocationtracking;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;

// Classes needed to initialize the map
import com.khadejaclarke.mapboxlocationtracking.async.LoadGeoJsonDataTask;
import com.khadejaclarke.mapboxlocationtracking.models.Collection;
import com.khadejaclarke.mapboxlocationtracking.models.Truck;
import com.khadejaclarke.mapboxlocationtracking.utils.APIService;
import com.mapbox.android.core.permissions.PermissionsListener;
import com.mapbox.android.core.permissions.PermissionsManager;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.location.LocationComponent;
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions;
import com.mapbox.mapboxsdk.location.LocationComponentOptions;
import com.mapbox.mapboxsdk.location.OnCameraTrackingChangedListener;
import com.mapbox.mapboxsdk.location.OnLocationClickListener;
import com.mapbox.mapboxsdk.location.modes.CameraMode;
import com.mapbox.mapboxsdk.location.modes.RenderMode;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;

// Classes needed to add markers to the map
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.mapboxsdk.plugins.localization.LocalizationPlugin;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static com.mapbox.mapboxsdk.style.expressions.Expression.eq;
import static com.mapbox.mapboxsdk.style.expressions.Expression.get;
import static com.mapbox.mapboxsdk.style.expressions.Expression.literal;
import static com.mapbox.mapboxsdk.style.layers.Property.ICON_ANCHOR_BOTTOM;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAnchor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconOffset;

// Classes needed to connect to the REST web service
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import timber.log.Timber;


public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, MapboxMap.OnMapClickListener, OnLocationClickListener, PermissionsListener, OnCameraTrackingChangedListener {
    private String TAG = MainActivity.class.getSimpleName();

    // Variables needed to poll the database
    private Handler handler;
    private Runnable runnable;
    private Call<Collection> call;

    // apiCallTime is the time interval when we call the API in milliseconds, by default this is set
    // to 2000 and you should only increase the value, reducing the interval will only cause server
    // traffic, the latitude and longitude values aren't updated that frequently.
    private int apiCallTime = 10000;

    // Variables needed to initialize a map
    private MapboxMap mapboxMap;
    private MapView mapView;

    private static final String GEOJSON_SOURCE_ID = "GEOJSON_SOURCE_ID";
    private static final String MARKER_IMAGE_ID = "MARKER_IMAGE_ID";
    private static final String MARKER_LAYER_ID = "MARKER_LAYER_ID";
    private static final String CALLOUT_LAYER_ID = "CALLOUT_LAYER_ID";

    private static final String PROPERTY_SELECTED = "selected";
    private static final String PROPERTY_ID = "id";

    private GeoJsonSource source;
    private FeatureCollection featureCollection;

    private PermissionsManager permissionsManager;
    private LocationComponent locationComponent;
    private boolean isInTrackingMode;
    private boolean flag = false;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Mapbox access token is configured here. This needs to be called either in your application
        // object or in the same activity which contains the mapview.
        Mapbox.getInstance(this, getString(R.string.access_token));

        // This contains the MapView in XML and needs to be called after the access token is configured.
        setContentView(R.layout.activity_main);

        System.out.println("ON CREATE***********************************************");
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {
        System.out.println("onmapready***********************************************");
        this.mapboxMap = mapboxMap;
        mapboxMap.setStyle(Style.MAPBOX_STREETS, style -> {
            enableLocationComponent(style);

            LocalizationPlugin localizationPlugin = new LocalizationPlugin(mapView, mapboxMap, style);

            try {
                System.out.println("ONMAPREADY ENTERED***********************************************");
                localizationPlugin.matchMapLanguageWithDeviceDefault();
            } catch (RuntimeException exception) {
                Timber.d(TAG, exception.toString() + "ONMAPREADY ERRORRRR*****************************");
            }
            mapboxMap.addOnMapClickListener(MainActivity.this);
////            try {
////                wait(120000);
////            } catch (InterruptedException e) {
////                e.printStackTrace();
//            }
            if (flag == false){
                callAPI();
            }

        });
    }

    @SuppressWarnings( {"MissingPermission"})
    private void enableLocationComponent(@NonNull Style loadedMapStyle) {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            // Create and customize the LocationComponent's optionsg
            LocationComponentOptions customLocationComponentOptions = LocationComponentOptions.builder(this)
                    .elevation(5)
                    .accuracyAlpha(.6f)
                    .accuracyColor(Color.RED)
                    .foregroundDrawable(R.drawable.android_custom_location_icon)
                    .build();

            // Get an instance of the component
            locationComponent = mapboxMap.getLocationComponent();

            LocationComponentActivationOptions locationComponentActivationOptions =
                    LocationComponentActivationOptions.builder(this, loadedMapStyle)
                            .locationComponentOptions(customLocationComponentOptions)
                            .build();

            // Activate with options
            locationComponent.activateLocationComponent(locationComponentActivationOptions);

            // Enable to make component visible
            locationComponent.setLocationComponentEnabled(true);

            // Set the component's camera mode
            locationComponent.setCameraMode(CameraMode.TRACKING);

            // Set the component's render mode
            locationComponent.setRenderMode(RenderMode.NORMAL);

            // Add the location icon click listener
            locationComponent.addOnLocationClickListener(this);

            // Add the camera tracking listener. Fires if the map camera is manually moved.
            locationComponent.addOnCameraTrackingChangedListener(this);

            findViewById(R.id.back_to_camera_tracking_mode).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (!isInTrackingMode) {
                        isInTrackingMode = true;
                        locationComponent.setCameraMode(CameraMode.TRACKING);
                        locationComponent.zoomWhileTracking(16f);
                        Toast.makeText(MainActivity.this, getString(R.string.tracking_enabled),
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, getString(R.string.tracking_already_enabled),
                                Toast.LENGTH_SHORT).show();
                    }
                }
            });

        } else {
            permissionsManager = new PermissionsManager(this);
            permissionsManager.requestLocationPermissions(this);
        }
    }

    @SuppressWarnings( {"MissingPermission"})
    @Override
    public void onLocationComponentClick() {
        if (locationComponent.getLastKnownLocation() != null) {
            Toast.makeText(this, String.format(getString(R.string.current_location),
                    locationComponent.getLastKnownLocation().getLatitude(),
                    locationComponent.getLastKnownLocation().getLongitude()), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onCameraTrackingDismissed() {
        isInTrackingMode = false;
    }

    @Override
    public void onCameraTrackingChanged(int currentMode) {
        // Empty on purpose
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onExplanationNeeded(List<String> permissionsToExplain) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onPermissionResult(boolean granted) {
        if (granted) {
            mapboxMap.getStyle(new Style.OnStyleLoaded() {
                @Override
                public void onStyleLoaded(@NonNull Style style) {
                    enableLocationComponent(style);
                }
            });
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public boolean onMapClick(@NonNull LatLng point) {
        System.out.println("ONMAPCLICK***********************************************");
        return handleClickIcon(mapboxMap.getProjection().toScreenLocation(point));
    }

    /**
     * Sets up all of the sources and layers needed
     *
     * @param collection the FeatureCollection to set equal to the globally-declared FeatureCollection
     */
    public void setUpData(final FeatureCollection collection) {
        featureCollection = collection;

        if (mapboxMap != null) {
            mapboxMap.getStyle(style -> {
                setupSource(style);
                setUpImage(style);
                setUpMarkerLayer(style);
                setUpInfoWindowLayer(style);
            });
        }
    }

    /**
     * Adds the GeoJSON source to the map
     */
    private void setupSource(@NonNull Style loadedStyle) {
        source = new GeoJsonSource(GEOJSON_SOURCE_ID, featureCollection);
        try {
            loadedStyle.addSource(source);
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }

    /**
     * Adds the marker image to the map for use as a SymbolLayer icon
     */
    private void setUpImage(@NonNull Style loadedStyle) {
        System.out.println("SET UP IMAGE**********************************************");
        loadedStyle.addImage(MARKER_IMAGE_ID, BitmapFactory.decodeResource(
                this.getResources(), R.drawable.red_marker));
    }

    /**
     * Updates the display of data on the map after the FeatureCollection has been modified
     */
    public void refreshSource() {
        if (source != null && featureCollection != null) {
            System.out.println("REFRESH SOURCE 1***********************************************");
            source.setGeoJson(featureCollection);
        }
        System.out.println("REFRESH SOURCE 2***********************************************");
    }

    /**
     * Setup a layer with maki icons, eg. west coast city.
     */
    private void setUpMarkerLayer(@NonNull Style loadedStyle) {
        System.out.println("SETUP MARKER LAYER***********************************************");
        try {
            loadedStyle.addLayer(new SymbolLayer(MARKER_LAYER_ID, GEOJSON_SOURCE_ID)
                    .withProperties(
                            iconImage(MARKER_IMAGE_ID),
                            iconAllowOverlap(true),
                            iconOffset(new Float[]{0f, -8f})
                    ));
        }catch (Exception e){
            System.out.println("BUG: " + e.toString());
        }
    }

    /**
     * Setup a layer with Android SDK call-outs
     * <p>
     * name of the feature is used as key for the iconImage
     * </p>
     */
    private void setUpInfoWindowLayer(@NonNull Style loadedStyle) {
        System.out.println("SETUPWINDOWINFO***********************************************");
        try {
            loadedStyle.addLayer(new SymbolLayer(CALLOUT_LAYER_ID, GEOJSON_SOURCE_ID)
                    .withProperties(
                            /* show image with id title based on the value of the name feature property */
                            iconImage("{name}"),

                            /* set anchor of icon to bottom-left */
                            iconAnchor(ICON_ANCHOR_BOTTOM),

                            /* all info window and marker image to appear at the same time*/
                            iconAllowOverlap(true),

                            /* offset the info window to be above the marker */
                            iconOffset(new Float[]{-2f, -28f})
                    )
                    /* add a filter to show only when selected feature property is true */
                    .withFilter(eq((get(PROPERTY_SELECTED)), literal(true))));
        }catch (Exception e){
            e.getMessage();
        }
    }

    /**
     * This method handles click events for SymbolLayer symbols.
     * <p>
     * When a SymbolLayer icon is clicked, we moved that feature to the selected truck.
     * </p>
     *
     * @param screenPoint the point on screen clicked
     */
    private boolean handleClickIcon(PointF screenPoint) {
        System.out.println("HANDLE CLICK ICON***********************************************");
        List<Feature> features = mapboxMap.queryRenderedFeatures(screenPoint, MARKER_LAYER_ID);
        if (!features.isEmpty()) {
            System.out.println("HANDLE CLICK FEATURES EMPTY***********************************************");
            String name = features.get(0).getStringProperty(PROPERTY_ID);
            List<Feature> featureList = featureCollection.features();
            if (featureList != null) {
                System.out.println("FEATURES LIST NOT NULL**********************************************");
                for (int i = 0; i < featureList.size(); i++) {
                    if (featureList.get(i).getStringProperty(PROPERTY_ID).equals(name)) {
                        if (featureSelectStatus(i)) {
                            setFeatureSelectState(featureList.get(i), false);
                        } else {
                            setSelected(i);
                        }
                    }
                }
            }
            return true;
        } else {System.out.println("FEATURES EMPTY***********************************************");
            return false;
        }

    }

    /**
     * Set a feature selected state.
     *
     * @param index the index of selected feature
     */
    private void setSelected(int index) {
        System.out.println("SET SELECTED***********************************************");
        if (featureCollection.features() != null) {
            Feature feature = featureCollection.features().get(index);
            setFeatureSelectState(feature, true);
            refreshSource();
        }
    }

    /**
     * Selects the state of a feature
     *
     * @param feature the feature to be selected.
     */
    private void setFeatureSelectState(Feature feature, boolean selectedState) {
        System.out.println("FEATURE SELECT STATE***********************************************");
        if (feature.properties() != null) {
            feature.properties().addProperty(PROPERTY_SELECTED, selectedState);
            refreshSource();
        }
    }

    /**
     * Checks whether a Feature's boolean "selected" property is true or false
     *
     * @param index the specific Feature's index position in the FeatureCollection's list of Features.
     * @return true if "selected" is true. False if the boolean property is false.
     */
    private boolean featureSelectStatus(int index) {
        System.out.println("FEATURE SELECT STATUS***********************************************");
        if (featureCollection == null) {
            return false;
        }
        return featureCollection.features().get(index).getBooleanProperty(PROPERTY_SELECTED);
    }

    /**
     * Invoked when the bitmaps have been generated from a view.
     */
    public void setImageGenResults(HashMap<String, Bitmap> imageMap) {
        System.out.println("SET IMAGE GEN RESULTS***********************************************");
        if (mapboxMap != null) {
            System.out.println("Mapbox map not null");
            mapboxMap.getStyle(style -> {
                // calling addImages is faster as separate addImage calls for each bitmap.
                if (imageMap == null || imageMap.isEmpty()){
                    System.out.println("CHECK IS NULL" );
                    System.out.println(style);

                } else{
                    System.out.println("CHECK NOT NULL");
                    System.out.println(style);
                    System.out.println(imageMap);


                }
                style.addImages(imageMap);
            });
        }
        System.out.println("PASSED");
    }


    private void callAPI() {
        System.out.println("CALL API***********************************************");
        // Build our client, The API we are using is very basic only returning a handful of
        // information, mainly, the current latitude and longitude of the International Space Station.
        Retrofit client = new Retrofit.Builder()
                .baseUrl(APIService.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        final APIService service = client.create(APIService.class);

        // A handler is needed to called the API every x amount of seconds.
        handler = new Handler();
        runnable = new Runnable() {
            @Override
            public void run() {
                // Call the API so we can get the updated coordinates.
                call = service.getTrucksFromAPI();
                System.out.println("IT STOPS HERE");
                call.enqueue(new Callback<Collection>() {
                    @Override
                    public void onResponse(@NonNull Call<Collection> call, @NonNull Response<Collection> response) {
                        System.out.println("BELLY: " + response.toString());
                        if (response.body() != null) {
                            try {
                                List<Truck> trucks = response.body().getTrucks();
                                JSONObject object = convertToGeoJson(trucks);
                                exportAsFile(object);
                                if(flag == false) {
                                    new LoadGeoJsonDataTask(MainActivity.this).execute();
                                }
                                boolean flag = true;

                            } catch (IOException | JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                    
                    private JSONObject convertToGeoJson(List<Truck> trucks) throws JSONException {
                        System.out.println("CONVERT TO GEOJSON***********************************************");
                        JSONObject collection = new JSONObject();
                        
                        JSONArray features = new JSONArray();
                        for (Truck truck : trucks) {
                            JSONObject feature = new JSONObject();

                            JSONObject properties = new JSONObject();
                            properties.put("id", truck.getID());

                            JSONArray coordinates = new JSONArray();
                            coordinates.put(truck.getLongitude());
                            coordinates.put(truck.getLatitude());

                            JSONObject geometry = new JSONObject();
                            geometry.put("type", "Point");
                            geometry.put("coordinates", coordinates);

                            feature.put("type", "Feature");
                            feature.put("properties", properties);
                            feature.put("geometry", geometry);
                            
                            features.put(feature);
                        }

                        collection.put("type", "FeatureCollection");
                        collection.put("features", features); // adding the list to our JSON Object

                        System.out.println("FEATURE COLLECTION***********************************************" + collection.toString());
                        return collection;
                    }

                    private void exportAsFile(JSONObject collection) throws IOException {
                        BufferedWriter file = new BufferedWriter(new FileWriter(getFilesDir() + "trucks.geojson"));
                        // FileWriter creates a file in write mode at the given location
                        //FileWriter file = new FileWriter("trucks.geojson");

                        try {
                            file.write(collection.toString());
                        
                        } catch (Exception e) {
                            e.printStackTrace();
                        
                        } finally {
                            file.flush();
                            file.close();
                        }

                        System.out.println("COLLECTION:" + collection);
                    }

                    @Override
                    public void onFailure(@NonNull Call<Collection> call, @NonNull Throwable throwable) {
                        // If retrofit fails or the API was unreachable, an error will be called.
                        // to check if throwable is null, then give a custom message.
                        if (throwable.getMessage() == null) {
                            Timber.e("Http connection failed");
                            Log.i(TAG, "Http connection failed");
                        } else {
                            Timber.e(throwable);
                            Log.i(TAG, throwable.toString());
                        }

                    }
                });
                // Schedule the next execution time for this runnable.
                handler.postDelayed(this, apiCallTime);
            }
        };

        // The first time this runs we don't need a delay so we immediately post.
        handler.post(runnable);
    }


    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
        // When the user returns to the activity we want to resume the API calling.
        if (handler != null && runnable != null) {
            handler.post(runnable);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
        // When the user leaves the activity, there is no need in calling the API since the map
        // isn't in view.
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mapboxMap != null) {
            mapboxMap.removeOnMapClickListener(this);
        }
        mapView.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }
}
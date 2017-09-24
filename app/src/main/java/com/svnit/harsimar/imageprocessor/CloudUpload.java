package com.svnit.harsimar.imageprocessor;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.net.Uri;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.client.Firebase;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static com.svnit.harsimar.imageprocessor.MainActivity.imageBitmap;

public class CloudUpload extends AppCompatActivity implements OnMapReadyCallback {

    private static final int MAX_LENGTH = 30;
    Bitmap image = null;
    private double latitude;
    private double longitude;
    private String label;
    private String uploadAddress;
    private List<Address> addresses = Collections.emptyList();

    private ImageView imageView;
    private EditText labelText;
    private EditText gpsText;
    private Button uploadBtn;
    private Uri mImageUri;

    private StorageReference mStorage;
    private ProgressDialog mProgress;
    private DatabaseReference mDatabase;

    private GoogleMap mGoogleMap;

    @Override
    protected void onStart() {
        super.onStart();
        Firebase.setAndroidContext(this);


    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cloud_upload);

        if (googleApiAvailability()) {
            Toast.makeText(CloudUpload.this, "Maps Linked ", Toast.LENGTH_SHORT).show();
            initMaps();
        }
        Bundle activitySentData = getIntent().getExtras();
        image = activitySentData.getParcelable(predictions_adapter.IMAGE_KEY);
        latitude = activitySentData.getDouble(predictions_adapter.LAT_KEY);
        longitude = activitySentData.getDouble(predictions_adapter.LON_KEY);
        Log.d("harsimarSingh", "received values" + latitude);
        label = activitySentData.getString(predictions_adapter.LABEL_KEY);

        imageView = (ImageView) findViewById(R.id.imageView);
        labelText = (EditText) findViewById(R.id.label_et);
        gpsText = (EditText) findViewById(R.id.gps_et);
        uploadBtn = (Button) findViewById(R.id.fireabaseUpload_button);

        labelText.setText(label);
        imageView.setImageBitmap(image);

        try {
            addresses = gpsConverter(latitude, longitude);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String address = addresses.get(0).getAddressLine(0); // If any additional address line present than only, check with max available address lines by getMaxAddressLineIndex()
        String city = addresses.get(0).getLocality();
        String state = addresses.get(0).getAdminArea();
        String country = addresses.get(0).getCountryName();
        String postalCode = addresses.get(0).getPostalCode();
        String knownName = addresses.get(0).getFeatureName();

        gpsText.setText(address + ", " + city + ", " + state);

        uploadAddress = address + ", " + city + ", " + state;
        uploadBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFirebaseUpload();
            }


        });


    }

    private List<Address> gpsConverter(double latitude, double longitude) throws IOException {
        Geocoder geocoder;
        List<Address> addresses;
        geocoder = new Geocoder(this, Locale.getDefault());
        addresses = geocoder.getFromLocation(latitude, longitude, 1);
        // Here 1 represent max location result to returned, by documents it recommended 1 to 5
        return addresses;

    }

    private void initMaps() {
//        View view = inflater.inflate(R.layout.activity_maps, null, false);
//
//        SupportMapFragment mapFragment = (SupportMapFragment) this.getChildFragmentManager()
//                .findFragmentById(R.id.map);
//        mapFragment.getMapAsync(this);
//
//        return view;
        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.maps_fragment);
        mapFragment.getMapAsync(this);
    }

    private void startFirebaseUpload() {

        final ProgressDialog mProgress = new ProgressDialog(CloudUpload.this);
        mProgress.setMessage("Sending data to SMC...");
        mProgress.setTitle("Please Wait");
        mProgress.show();
        firebaseInit();

        mStorage = FirebaseStorage.getInstance().getReference();
        mDatabase = FirebaseDatabase.getInstance().getReference().child("ProcessedImages");

        StorageReference filepath =
                mStorage.child("MobileCaptures").child(random());
        filepath.putBytes(converBitmap(image)).addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {

                Uri downloadUri = taskSnapshot.getDownloadUrl();

                DatabaseReference newPost = mDatabase.push();
                Log.d("harsimarSINGH", newPost.toString());

                newPost.child("imageLink").setValue(downloadUri.toString().trim());
                newPost.child("label").setValue(label);
                newPost.child("location").setValue(uploadAddress);

                mProgress.dismiss();
                Toast.makeText(CloudUpload.this, "Congratulation! You earned 5 Loyality Points ! .", Toast.LENGTH_SHORT).show();
                finish();
            }
        });


    }

    public boolean googleApiAvailability() {
        GoogleApiAvailability api = GoogleApiAvailability.getInstance();
        int isAvailable = api.isGooglePlayServicesAvailable(this);
        if (isAvailable == ConnectionResult.SUCCESS) return true;
        else if (api.isUserResolvableError(isAvailable)) {
            Dialog dialog = api.getErrorDialog(this, isAvailable, 0);
            dialog.show();

        } else
            Toast.makeText(CloudUpload.this, "Cant connect to the network", Toast.LENGTH_LONG).show();
        return false;
    }

    private byte[] converBitmap(Bitmap imageBitmap) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        byte[] byteArray = stream.toByteArray();
        return byteArray;
    }


    public static String random() {
        Random generator = new Random();
        StringBuilder randomStringBuilder = new StringBuilder();
        int randomLength = generator.nextInt(MAX_LENGTH);
        char tempChar;
        for (int i = 0; i < randomLength; i++) {
            tempChar = (char) (generator.nextInt(96) + 32);
            randomStringBuilder.append(tempChar);
        }
        return randomStringBuilder.toString();
    }

    private void firebaseInit() {
        Firebase.setAndroidContext(this);
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);

    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.d("harsimarSingh", "mapsReady called");
        mGoogleMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mGoogleMap.setMyLocationEnabled(true);




        BufferedReader reader=null;
        try {
            // open and read the file into a StringBuilder
            InputStream in =getApplicationContext().getAssets().open("dustbins.json");
            reader = new BufferedReader(new InputStreamReader(in));
            StringBuilder jsonString = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                // line breaks are omitted and irrelevant
                jsonString.append(line);
            }
            // parse the JSON using JSONTokener
            JSONArray array = (JSONArray) new JSONTokener(jsonString.toString()).nextValue();

            // do something here if needed from JSONObjects
            for (int i = 0; i < array.length(); i++) {
                JSONObject dustJson=array.getJSONObject(i);
                double longJson = Double.parseDouble(dustJson.getString("Longitude"));
                double latJson = Double.parseDouble(dustJson.getString("Latitude"));

               // Log.d("harsimarSingh","lat "+latJson+" long "+longJson+"  distance   "+distance(latJson,longJson,latitude,longitude));


                MarkerOptions options=new MarkerOptions().title("SMC DUMBINS").position(new LatLng(latJson,longJson));


                if(distance(latJson,longJson,latitude,longitude)<3)
                    mGoogleMap.addMarker(options);

            }



        } catch (FileNotFoundException e) {
            // we will ignore this one, since it happens when we start fresh
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null)
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }



        goToLocationZoomed(latitude,longitude,15);

    }

    private void goToLocation(double latitude, double longitude) {
        LatLng ll=new LatLng(latitude,longitude);
        CameraUpdate update= CameraUpdateFactory.newLatLng(ll);
        mGoogleMap.moveCamera(update);
    }
    private void goToLocationZoomed(double latitude, double longitude,int zoom) {
        LatLng ll=new LatLng(latitude,longitude);
        CameraUpdate update= CameraUpdateFactory.newLatLngZoom(ll,zoom);
        mGoogleMap.animateCamera(update);
    }
    private static double rad2deg(double rad) {
        return (rad * 180 / Math.PI);
    }
    private static double deg2rad(double deg) {
        return (deg * Math.PI / 180.0);
    }
    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) +
                Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        dist = dist * 60 * 1.1515;
        dist = dist * 1.609344;
        return (dist);
    }
}

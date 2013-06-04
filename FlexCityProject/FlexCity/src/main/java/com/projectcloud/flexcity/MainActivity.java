package com.projectcloud.flexcity;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends Activity {
    private static String TAG = MainActivity.class.getName();

    //UI stuff
    private ImageButton mStartStopBtn = null;
    private ImageButton mToggleBtn = null;

    //GPS stuff
    private LocationManager mLocManger;
    private long mTimeInterval = 500; //in milliseonds
    private float mDistanceInterval = 1; //in meters
    private boolean mIsTracking = false;
    private boolean mIsAutoModeOn = false;

    //Speed stuff
    private static int measurement_index = 0;
    private static final int HOUR_MULTIPLIER = 3600;
    private static final double UNIT_MULTIPLIERS[] = { 0.001, 0.000621371192 };
    private String mAverageSpeed;

    private ArrayList<Location> mLocTable = new ArrayList<Location>();
    private ArrayList<String> mSpeedTable = new ArrayList<String>();

    //File stuff
    private File mCurrentFile = null;

    //Upload stuff
    private String mUploadServerURI = "http://prjctcld.com/Flexity/upload.php";

    class UploaddTask extends AsyncTask<String, Void, Void> {

        private Exception exception;

        protected Void doInBackground(String... urls) {
            uploadLastFile();
            return null;
        }

        protected void onPostExecute(Void v) {
        }
    }

    private LocationListener mLocListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {

            String lat = String.valueOf(location.getLatitude());
            String lon = String.valueOf(location.getLongitude());
            String speedString = "" + roundDecimal(convertSpeed(location.getSpeed()),2);

            Log.e(TAG, "location changed: lat=" + lat + ", lon=" + lon + ", speed=" + speedString);

            mLocTable.add(location);
            mSpeedTable.add(speedString);
        }

        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {
            Log.e(TAG, "onStatusChanged " + s);
        }

        @Override
        public void onProviderEnabled(String s) {
            Log.e(TAG, "onProviderEnabled " + s);
        }

        @Override
        public void onProviderDisabled(String s) {
            Log.e(TAG, "onProviderDisabled " + s);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //get loc manager
        mLocManger = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        //UI init.
        mStartStopBtn = (ImageButton) findViewById(R.id.btn_start_stop);
        mToggleBtn = (ImageButton) findViewById(R.id.btn_auto_mode);

        mStartStopBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(!mIsTracking){
                    startTracking();
                }
                else{
                    stopTracking();
                }
            }
        });

        mToggleBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggleAutoMode();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.action_upload:
                new UploaddTask().execute("");
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /*
        * *********************************************************************************************************
        ******************************************** MAIN ROUTINES *************************************************
        * *********************************************************************************************************
        */
    private void startTracking(){
        //change btn graphic
        mStartStopBtn.setImageResource(R.drawable.btn_stop);

        mLocTable.clear();
        mSpeedTable.clear();

        mCurrentFile = null;

        //acquire GPS
        mLocManger.requestLocationUpdates(LocationManager.GPS_PROVIDER, mTimeInterval, mDistanceInterval, mLocListener);
        mIsTracking = true;

        //create file
        createFile();
    }

    private void stopTracking(){
        if(mLocManger != null){
            //stop GPS tracking
            mLocManger.removeUpdates(mLocListener);

            //compute overall speed
            calculateAverageSpeed();
            //save data
            writeToFile();

            mIsTracking = false;
            //change btn graphic
            mStartStopBtn.setImageResource(R.drawable.btn_start);
        }
    }

    private void toggleAutoMode(){
        if(mIsAutoModeOn){
            mToggleBtn.setImageResource(R.drawable.toggle_off);
            mIsAutoModeOn = false;
        }
        else{
            mToggleBtn.setImageResource(R.drawable.toggle_on);
            mIsAutoModeOn = true;
        }
    }

    private void createFile(){
        File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File (sdCard.getAbsolutePath() + "/records");
        dir.mkdirs();

        String currentDateTimeString = new SimpleDateFormat("yyyy-MM-dd hh-mm-ss").format(new Date());
        String fileName = "track_" + currentDateTimeString;

        mCurrentFile = new File(dir, fileName);
        Log.e(TAG, "createFile: " + fileName);
    }

    private void writeToFile(){
        Log.e(TAG, "writeToFile");

        if(mCurrentFile != null && mLocTable.size() > 0){
            FileWriter gpxwriter = null;
            try {
                gpxwriter = new FileWriter(mCurrentFile, true);
                BufferedWriter out = new BufferedWriter(gpxwriter);

                //write speed first
                out.write(mAverageSpeed);

                for(Location l : mLocTable){
                    String lat = String.valueOf(l.getLatitude());
                    String lon = String.valueOf(l.getLongitude());

                    String data = lat + "," + lon;

                    out.newLine();
                    out.write(data);
                }
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private int uploadLastFile(){
        Log.e(TAG, "uploadLastFile");

        if(mCurrentFile == null){
            return -1;
        }

        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1024;
        int serverResponseCode = 0;

        /*File sdCard = Environment.getExternalStorageDirectory();
        File dir = new File (sdCard.getAbsolutePath() + "/records");
        dir.mkdirs();

        mCurrentFile = new File(dir, "testUp");*/

        try {
            // open a URL connection to the Servlet
            FileInputStream fileInputStream = new FileInputStream(mCurrentFile);
            URL url = new URL(mUploadServerURI);

            // Open a HTTP  connection to  the URL
            conn = (HttpURLConnection) url.openConnection();
            conn.setDoInput(true); // Allow Inputs
            conn.setDoOutput(true); // Allow Outputs
            conn.setUseCaches(false); // Don't use a Cached Copy
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Connection", "Keep-Alive");
            //conn.setRequestProperty("ENCTYPE", "multipart/form-data");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
            //conn.setRequestProperty("uploaded_file", fileToUpload.getName());

            dos = new DataOutputStream(conn.getOutputStream());

            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\""+ mCurrentFile.getName() + "\"" + lineEnd);

            dos.writeBytes(lineEnd);

            Log.e(TAG, "Headers are written");

            // create a buffer of  maximum size
            bytesAvailable = fileInputStream.available();

            bufferSize = Math.min(bytesAvailable, maxBufferSize);
            buffer = new byte[bufferSize];

            // read file and write it into form...
            bytesRead = fileInputStream.read(buffer, 0, bufferSize);

            while (bytesRead > 0) {

                dos.write(buffer, 0, bufferSize);
                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);
            }

            // send multipart form data necesssary after file data...
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

            Log.e(TAG, "File is written");

            // Responses from the server (code and message)
            serverResponseCode = conn.getResponseCode();
            String serverResponseMessage = conn.getResponseMessage();

            Log.i("uploadFile", "HTTP Response is : " + serverResponseMessage + ": " + serverResponseCode);

            if(serverResponseCode == 200){
                Log.e(TAG, "Upload file to server succeeded");
                mCurrentFile = null; //prevent to reupload the file...
            }

            //close the streams //
            fileInputStream.close();
            dos.flush();
            dos.close();
        } catch (MalformedURLException ex) {

            //dialog.dismiss();
            ex.printStackTrace();
            Log.e(TAG, "Upload file to server error: " + ex.getMessage(), ex);
        } catch (Exception e) {

            //dialog.dismiss();
            Log.e(TAG, "Upload file to server Exception : "+ e.getMessage(), e);
        }
        //dialog.dismiss();
        return serverResponseCode;
}

    /*
    * *********************************************************************************************************
    **************************************************** UTILS *************************************************
    * *********************************************************************************************************
    */
    private double convertSpeed(double speed){
        return ((speed * HOUR_MULTIPLIER) * UNIT_MULTIPLIERS[measurement_index]);
    }

    private double roundDecimal(double value, final int decimalPlace)
    {
        BigDecimal bd = new BigDecimal(value);

        bd = bd.setScale(decimalPlace, RoundingMode.HALF_UP);
        value = bd.doubleValue();

        return value;
    }

    private void calculateAverageSpeed(){
        double averageSpeed = 0;

        if(mSpeedTable.size() > 0){
            //calculate an average of the speed
            for(String speed : mSpeedTable){
                double value = Double.valueOf(speed);
                averageSpeed += value;
            }

            averageSpeed /= mSpeedTable.size();
            mAverageSpeed = String.valueOf(averageSpeed);

            Log.e(TAG, "calculateAverageSpeed: " + mAverageSpeed);
        }
    }

    /*
    * *********************************************************************************************************
    ***************************************************** DEBUG *************************************************
    * *********************************************************************************************************
    */
    private void writeToFileDebug(String data){
        Log.e(TAG, "writeToFile");

        if(mCurrentFile != null){
            FileWriter gpxwriter = null;
            try {
                gpxwriter = new FileWriter(mCurrentFile, true);
                BufferedWriter out = new BufferedWriter(gpxwriter);

                out.write(data);
                out.newLine();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

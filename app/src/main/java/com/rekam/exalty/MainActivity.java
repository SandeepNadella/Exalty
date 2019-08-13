package com.rekam.exalty;

import android.Manifest;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.location.Location;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityTransition;
import com.google.android.gms.location.ActivityTransitionRequest;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabel;
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler;
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    public static final String[] EVENT_PROJECTION = new String[]{
            CalendarContract.EventsEntity.TITLE
    };
    private static final int REQUEST_PERMISSION_STORAGE = 101;
    private static final int REQUEST_PERMISSION_CAMERA = 1001;
    private static final int REQUEST_PERMISSION_CALENDAR = 1002;
    private static final int RC_SELECT_PICTURE = 103;
    private static final int RC_CAPTURE_IMAGE = 1;
    private FusedLocationProviderClient fusedLocationClient;
    private Bitmap mBitmap;
    private Uri mImageUri;
    private String mCurrentPhotoPath;
    private PendingIntent pIntent;

    private static String getPath(Context context, Uri uri) {
        String result = null;
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = context.getContentResolver().query(uri, proj, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int column_index = cursor.getColumnIndexOrThrow(proj[0]);
                result = cursor.getString(column_index);
            }
            cursor.close();
        }
        if (result == null) {
            result = "Not found";
        }
        return result;
    }

    private static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
    }

    private void showError(View view) {
        Snackbar.make(view, "Something went wrong", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    private void showError() {
        Snackbar.make(findViewById(R.id.appRelativeLayout), "Something went wrong", Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    private void showError(String msg) {
        Snackbar.make(findViewById(R.id.appRelativeLayout), msg, Snackbar.LENGTH_LONG)
                .setAction("Action", null).show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.captureYourWorld).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkCameraPermission(REQUEST_PERMISSION_CAMERA);
            }
        });

        findViewById(R.id.captureSomeWorld).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkStoragePermission(REQUEST_PERMISSION_STORAGE);
            }
        });

        findViewById(R.id.surprise).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkCalendarPermission(REQUEST_PERMISSION_CALENDAR);
            }
        });

        final EditText edittext = (EditText) findViewById(R.id.captureUserInputMood);
        edittext.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                // If the event is a key-down event on the "enter" button
                if ((event.getAction() == KeyEvent.ACTION_DOWN) &&
                        (keyCode == KeyEvent.KEYCODE_ENTER)) {
                    startPlaylistRecommendation(edittext.getText().toString());
                    return true;
                }
                return false;
            }
        });

        edittext.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int DRAWABLE_RIGHT = 2;
                //check if icon is clicked
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (event.getRawX() >= (edittext.getRight() - edittext.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                        startPlaylistRecommendation(edittext.getText().toString());
                        return true;
                    }
                }
                return false;
            }
        });

        List<ActivityTransition> transitions = new ArrayList<>();

        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.IN_VEHICLE)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());

        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.ON_BICYCLE)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());
        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.RUNNING)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());
        transitions.add(
                new ActivityTransition.Builder()
                        .setActivityType(DetectedActivity.WALKING)
                        .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                        .build());
        ActivityTransitionRequest request = new ActivityTransitionRequest(transitions);

        Intent intent = new Intent(this, MainActivity.class);
        pIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Task<Void> task = ActivityRecognition.getClient(this)
                .requestActivityTransitionUpdates(request, pIntent);

        Context thisConext = this;

        task.addOnSuccessListener(
                new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void result) {

                    }
                }
        );

        task.addOnFailureListener(
                new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {

                    }
                }
        );
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {

                            try {
                                FetchArtists fetchArtists = new FetchArtists(thisConext);
                                fetchArtists.execute(location.getLatitude() + "_" + location.getLongitude());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
    }

    private void startPlaylistRecommendation(List<?> labels) {
        Intent playerIntent = new Intent(MainActivity.this, RemotePlayerActivity.class);
        List<String> labelsStr = new ArrayList<>();
        if (!labels.isEmpty()) {
            if (labels.get(0) instanceof FirebaseVisionImageLabel) {
                for (FirebaseVisionImageLabel label : (List<FirebaseVisionImageLabel>) labels) {
                    Log.println(Log.DEBUG, "ML Kit", "Label: " + label.getText() + " Confidence: " + label.getConfidence());
                    labelsStr.add(label.getText());
                }
            } else {
                labelsStr = (List<String>) labels;
            }
            playerIntent.putStringArrayListExtra("labels", (ArrayList<String>) labelsStr);
            MainActivity.this.startActivity(playerIntent);
        }
    }

    private void startPlaylistRecommendation(String mood) {
        Intent playerIntent = new Intent(MainActivity.this, RemotePlayerActivity.class);
        playerIntent.putExtra("mood", mood);
        Log.println(Log.DEBUG, "User Input", "Mood: " + mood);
        MainActivity.this.startActivity(playerIntent);
    }

    private List<String> readCalendarEvents() {
        Calendar currentTime = Calendar.getInstance();
        List<String> events = new ArrayList<>();
        Calendar beginTime = Calendar.getInstance();
        beginTime.set(currentTime.get(Calendar.YEAR), currentTime.get(Calendar.MONTH), currentTime.get(Calendar.DATE), 0, 0);
        Calendar endTime = Calendar.getInstance();
        endTime.set(currentTime.get(Calendar.YEAR), currentTime.get(Calendar.MONTH), currentTime.get(Calendar.DATE), 23, 59);
        Cursor cur = null;
        ContentResolver cr = getContentResolver();
        Uri uri = CalendarContract.EventsEntity.CONTENT_URI;
        String selection = "((" + CalendarContract.Events.DTSTART + " >= ?) AND ("
                + CalendarContract.Events.DTSTART + " <= ?) AND (deleted != 1 ))";
        String[] selectionArgs = new String[]{Long.toString(beginTime.getTimeInMillis()), Long.toString(endTime.getTimeInMillis())};
        // Submit the query and get a Cursor object back.
        cur = cr.query(uri, EVENT_PROJECTION, selection, selectionArgs, null);
        while (cur.moveToNext()) {
            String eventTitle;

            // Get the field values
            eventTitle = cur.getString(0);
            Log.println(Log.DEBUG, "Calendar", "Event Display Name: " + eventTitle);
            events.add(eventTitle);
        }
        return events;
    }

    private void checkStoragePermission(int requestCode) {
        switch (requestCode) {
            case REQUEST_PERMISSION_STORAGE:
                int hasWriteExternalStoragePermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                if (hasWriteExternalStoragePermission == PackageManager.PERMISSION_GRANTED) {
                    selectPicture();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, requestCode);
                }
                break;

        }
    }

    private void checkCameraPermission(int requestCode) {
        switch (requestCode) {
            case REQUEST_PERMISSION_CAMERA:
                int hasCameraPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
                if (hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
                    issueCameraIntent();
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, requestCode);
                }
                break;
        }
    }

    private void checkCalendarPermission(int requestCode) {
        switch (requestCode) {
            case REQUEST_PERMISSION_CALENDAR:
                int hasCameraPermission = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR);
                if (hasCameraPermission == PackageManager.PERMISSION_GRANTED) {
                    SharedPreferences preferences = getSharedPreferences("ActionAppPreferences", 0);
                    String artist;
                    Random r = new Random();
                    int random = r.nextInt((2 - 1) + 1) + 1;
                    if (random == 1) {
                        artist = preferences.getString("Artist", "");
                    } else {
                        artist = preferences.getString("Weather", "");
                    }
                    if (!"".equals(artist)) {
                        startPlaylistRecommendation(artist);
                        if (random == 1) {
                            Toast.makeText(this, "Playing from Location!", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Playing from Weather!", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        List<String> events = readCalendarEvents();
                        startPlaylistRecommendation(events);
                        Toast.makeText(this, "Playing from Calendar events!", Toast.LENGTH_LONG).show();
                    }
                } else {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_CALENDAR}, requestCode);
                }
                break;
        }
    }

    private void issueCameraIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
                showError();
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                mImageUri = FileProvider.getUriForFile(this,
                        "com.rekam.exalty.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, mImageUri);
                startActivityForResult(takePictureIntent, RC_CAPTURE_IMAGE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_PERMISSION_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    selectPicture();
                } else {
                    needPermission(requestCode, R.string.permission_request);
                }
                break;
            case REQUEST_PERMISSION_CAMERA:
                if (grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {
                    issueCameraIntent();

                }
                break;
            case REQUEST_PERMISSION_CALENDAR:
                if (grantResults[0] ==
                        PackageManager.PERMISSION_GRANTED) {
                    List<String> events = readCalendarEvents();
                    startPlaylistRecommendation(events);
                }
                break;
            default:
                break;
        }
    }

    private void needPermission(final int requestCode, int msg) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setMessage(msg);
        alert.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, requestCode);
            }
        });
        alert.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                dialogInterface.dismiss();
            }
        });
        alert.setCancelable(false);
        alert.show();
    }

    private void selectPicture() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, RC_SELECT_PICTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_PERMISSION_STORAGE:
                    checkStoragePermission(requestCode);
                    break;
                case REQUEST_PERMISSION_CAMERA:
                    checkCameraPermission(REQUEST_PERMISSION_CAMERA);
                    break;
                case RC_SELECT_PICTURE:
                    processImage(getPath(this.getApplicationContext(), data.getData()));
                    break;
                case RC_CAPTURE_IMAGE:
                    processImage(mCurrentPhotoPath);
                    break;
            }
        }
    }

    private void processImage(String imagePath) {
        mBitmap = null;
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        // Decode the image file into a Bitmap sized to fill the View
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inPurgeable = true;
        mBitmap = BitmapFactory.decodeFile(imagePath, bmOptions);
        if (mBitmap != null && imagePath != null) {
            ExifInterface ei = null;
            try {
                ei = new ExifInterface(imagePath);
            } catch (IOException e) {
                e.printStackTrace();
                showError();
            }
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);

            Bitmap rotatedBitmap = null;
            switch (orientation) {

                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotatedBitmap = rotateImage(mBitmap, 90);
                    break;

                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotatedBitmap = rotateImage(mBitmap, 180);
                    break;

                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotatedBitmap = rotateImage(mBitmap, 270);
                    break;

                case ExifInterface.ORIENTATION_NORMAL:
                default:
                    rotatedBitmap = mBitmap;
            }
            mBitmap = rotatedBitmap;
            generateLabels();
        }
    }

    private void generateLabels() {
        if (mBitmap != null) {
            FirebaseVisionOnDeviceImageLabelerOptions options = new FirebaseVisionOnDeviceImageLabelerOptions.Builder()
                    .setConfidenceThreshold(0.7f)
                    .build();
            FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(mBitmap);
            FirebaseVisionImageLabeler detector = FirebaseVision.getInstance().getOnDeviceImageLabeler(options);
            detector.processImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionImageLabel>>() {
                @Override
                public void onSuccess(List<FirebaseVisionImageLabel> labels) {
                    startPlaylistRecommendation(labels);
                    showError("Labels Generated");
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    e.printStackTrace();
                    showError();
                }
            });
        }
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }
}

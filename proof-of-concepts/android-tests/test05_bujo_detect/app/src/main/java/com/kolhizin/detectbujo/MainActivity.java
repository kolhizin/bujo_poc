package com.kolhizin.detectbujo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.FileProvider;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

public class MainActivity extends FragmentActivity implements View.OnClickListener, View.OnTouchListener {


    public class AsyncPreprocessTask extends AsyncTask<Bitmap, BuJoPage, BuJoPage> {
        private Detector detector_;
        private Context context_;

        public AsyncPreprocessTask(Context context, Detector detector) {
            super();
            context_ = context;
            detector_ = detector;
        }

        @Override
        protected BuJoPage doInBackground(Bitmap... params) {
            detector_.getPage().setStatusStart("Loading image!");
            publishProgress(detector_.getPage());
            detector_.getPage().setStatusLoadedBitmap("Finished reading image! Initiating full detection!");
            publishProgress(detector_.getPage());
            try {
                detector_.load(params[0]);
                publishProgress(detector_.getPage());
                detector_.preprocess();
                publishProgress(detector_.getPage());
                detector_.detectRegions();
                publishProgress(detector_.getPage());
            } catch (Exception e) {
                e.printStackTrace();
                detector_.getPage().setError("Exception: " + e.getMessage());
            }
            return detector_.getPage();
        }

        @Override
        protected void onProgressUpdate(BuJoPage... values) {
            super.onProgressUpdate(values);
            if(values[0].getStatus().fErrors)
                Toast.makeText(context_, values[0].getErrorMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onPostExecute(BuJoPage page) {
            super.onPostExecute(page);
            showUpdatedImage();
            Toast.makeText(context_, "Done!", Toast.LENGTH_LONG).show();
        }
    }

    public class AsyncDetectLinesTask extends AsyncTask<Integer, BuJoPage, BuJoPage> {
        private Detector detector_;
        private Context context_;

        public AsyncDetectLinesTask(Context context, Detector detector) {
            super();
            context_ = context;
            detector_ = detector;
        }

        @Override
        protected BuJoPage doInBackground(Integer ... params) {
            try {
                detector_.detectLines();
                publishProgress(detector_.getPage());
            } catch (Exception e) {
                e.printStackTrace();
                detector_.getPage().setError("Exception: " + e.getMessage());
            }
            return detector_.getPage();
        }

        @Override
        protected void onProgressUpdate(BuJoPage... values) {
            super.onProgressUpdate(values);
            if(values[0].getStatus().fErrors)
                Toast.makeText(context_, values[0].getErrorMessage(), Toast.LENGTH_LONG).show();
        }

        @Override
        protected void onPostExecute(BuJoPage page) {
            super.onPostExecute(page);
            showUpdatedImage();
            Toast.makeText(context_, "Done!", Toast.LENGTH_LONG).show();
        }
    }

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("native-lib");
    }

    private static final int RC_SELECT_PICTURE = 1;
    private static final int RC_TAKE_PHOTO = 2;

    private static final int PERCODE_CAMERA = 1;
    private static final int PERCODE_WRITE_EXTSTORAGE = 2;

    private Bitmap mainBitmap = null;
    private ImageButton btnRotate = null, btnLoad = null, btnPhoto = null, btnDetect = null;
    private ImageView imgMain = null;
    private Uri cameraImgUri = null;
    private Classifier classifier = null;
    private Detector detector = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try{
            classifier = new Classifier(this, "model.tflite", "model.chars");
            detector = new Detector();
            BuJoApp app = (BuJoApp)getApplication();
            app.setClassifier(classifier);
            app.setDetector(detector);
        }catch (Exception e){
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }

        imgMain = findViewById(R.id.imageView);
        btnLoad = findViewById(R.id.btnLoad);
        btnPhoto = findViewById(R.id.btnPhoto);
        btnRotate = findViewById(R.id.btnRotate);
        btnDetect = findViewById(R.id.btnDetect);

        updateImageView();

        btnLoad.setOnClickListener(this);
        btnPhoto.setOnClickListener(this);
        btnRotate.setOnClickListener(this);
        btnDetect.setOnClickListener(this);
        imgMain.setOnTouchListener(this);
    }

    private BuJoPage getPage(){
        return ((BuJoApp)getApplication()).getPage();
    }

    private void updateImageView(){
        imgMain.setImageBitmap(mainBitmap);
        if(mainBitmap != null){
            btnRotate.setVisibility(View.VISIBLE);
            btnRotate.setEnabled(true);
            btnDetect.setVisibility(View.VISIBLE);
            btnDetect.setEnabled(true);
        }else{
            btnRotate.setVisibility(View.GONE);
            btnRotate.setEnabled(false);
            btnDetect.setVisibility(View.GONE);
            btnDetect.setEnabled(false);
        }
    }

    private void showUpdatedImage(){
        BuJoPage page = ((BuJoApp)getApplication()).getPage();
        if(page == null)
            return;
        if(page.getStatus().fDetectedLines){
            Bitmap res = page.getAligned().copy(page.getAligned().getConfig(), true);
            Canvas canvas = new Canvas(res);

            BuJoTools.shadeRegions(canvas, page.getSplits());
            BuJoTools.drawLines(canvas, page.getLines(), Color.rgb(100,100,100),10.0f);
            if(page.getActiveLine() != null)
                BuJoTools.drawLine(canvas, page.getActiveLine(), Color.rgb(255,0,0),20.0f);
            imgMain.setImageBitmap(res);
        }else if(page.getStatus().fDetectedRegion) {
            Bitmap res = page.getAligned().copy(page.getAligned().getConfig(), true);
            Canvas canvas = new Canvas(res);

            BuJoTools.shadeRegions(canvas, page.getSplits());
            imgMain.setImageBitmap(res);
        }
    }

    private void rotateBitmap(){
        if(mainBitmap != null){
            Matrix matrix = new Matrix();
            matrix.postRotate(90.0f);
            mainBitmap = Bitmap.createBitmap(mainBitmap, 0, 0, mainBitmap.getWidth(), mainBitmap.getHeight(), matrix, true);
            updateImageView();
        }
    }

    private void detectRegion(){
        BuJoSettings settings = new BuJoSettings();
        try {
            //AsyncDetect task = new AsyncDetect(this, new BuJoPage(), settings);
            detector.reset();
            AsyncPreprocessTask task = new AsyncPreprocessTask(this, detector);
            task.execute(mainBitmap);
            Toast.makeText(this, "Started region-detection!", Toast.LENGTH_SHORT).show();
        }catch (Exception e){
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void detectLines(){
        BuJoSettings settings = new BuJoSettings();
        try {
            AsyncDetectLinesTask task = new AsyncDetectLinesTask(this, detector);
            task.execute(0);
            Toast.makeText(this, "Started line-detection!", Toast.LENGTH_SHORT).show();
        }catch (Exception e){
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    private void detectWords(int id){
        Intent intent = new Intent(this, InspectLineActivity.class);
        startActivity(intent);
    }


    private void dispatchDetect(){
        BuJoPage page = getPage();
        if(page == null){
            Toast.makeText(this, "Page does not exist!", Toast.LENGTH_LONG).show();
            return;
        }
        if(!page.getStatus().fDetectedRegion){
            detectRegion();
        }else if(!page.getStatus().fDetectedLines){
            detectLines();
        }else{
            if(page.getActiveLine() == null){
                Toast.makeText(this, "First select line!", Toast.LENGTH_LONG).show();
                return;
            }
            detectWords(page.getActiveLineId());
        }
    }

    @Override
    public void onClick(View v){
        switch(v.getId())
        {
            case R.id.btnLoad: loadPicture(); return;
            case R.id.btnPhoto: takePhoto(); return;
            case R.id.btnRotate: rotateBitmap(); return;
            case R.id.btnDetect: dispatchDetect(); return;
        }
    }
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if(v.getId() == R.id.imageView){
            float x = event.getX();
            float y = event.getY();
            int w = imgMain.getWidth();
            int h = imgMain.getHeight();
            Drawable drbl = imgMain.getDrawable();
            if(drbl == null)
                return false;
            int w0 = drbl.getIntrinsicWidth();
            int h0 = drbl.getIntrinsicHeight();
            if(w0 <= 0 || h0 <= 0)
                return false;
            float [] loc = ImageUtils.toLocalCoord(x, y, w, h, w0, h0);
            if((loc[0] >= 0.0f)&&(loc[0] <= 1.0f)&&(loc[1] >= 0.0f)&&(loc[1] <= 1.0f))
            {
                touchMainImg(loc[0], loc[1], event);
                return true;
            }
        }
        return false;
    }

    protected void touchMainImg(float locX, float locY, MotionEvent event){
        if(getPage() != null && getPage().getStatus().fDetectedLines) {
            int id = getPage().getClosestLine(locX, locY);
            BuJoPage.BuJoLine newLine = null;
            if(id != -1)
                newLine = getPage().getLine(id);
            boolean fSame = (newLine == getPage().getActiveLine());
            if(!fSame) {
                getPage().activateLine(id);
                showUpdatedImage();
            }
        }
    }

    protected void takePhoto(){
        if(!getPackageManager().hasSystemFeature(getPackageManager().FEATURE_CAMERA)){
            Toast.makeText(this, "you have no camera :(", Toast.LENGTH_SHORT).show();
            return;
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERCODE_CAMERA);
        }else if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERCODE_WRITE_EXTSTORAGE);
        }else{
            File mainDirectory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "BuJo/tmp");
            if (!mainDirectory.exists())
                mainDirectory.mkdirs();

            Calendar calendar = Calendar.getInstance();
            File tmpFile = null;
            try {
                tmpFile = File.createTempFile(
                        "IMG_"+calendar.getTimeInMillis(),
                        ".jpeg",
                        mainDirectory
                );
            }catch (IOException e){
                Toast.makeText(this, "Exception", Toast.LENGTH_LONG);
            }
            //Add provider_paths.xml and update manifest to include provider-section
            cameraImgUri = FileProvider.getUriForFile(this,
                    BuildConfig.APPLICATION_ID + ".provider", tmpFile);
            Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
            cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraImgUri);
            cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivityForResult(cameraIntent, RC_TAKE_PHOTO);
            }catch (Exception e){
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    protected void loadPicture(){
        Intent intent = new Intent(Intent.ACTION_PICK,
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, RC_SELECT_PICTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // TODO Auto-generated method stub
        super.onActivityResult(requestCode, resultCode, data);

        Uri targetUri = null;
        if (requestCode == RC_SELECT_PICTURE){
            if (resultCode == RESULT_OK){
                targetUri = data.getData();
            }
        }
        if(requestCode == RC_TAKE_PHOTO){
            if(resultCode == RESULT_OK){
                targetUri = cameraImgUri;
            }
        }
        if(targetUri != null){

            Bitmap bmp = null;
            try{
                bmp = ImageUtils.loadImage(this, targetUri, 4096);
            }catch (Exception e){
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
            }
            if(bmp != null) {
                mainBitmap = bmp;
                updateImageView();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERCODE_CAMERA) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }else if (requestCode == PERCODE_WRITE_EXTSTORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "storage write permission granted", Toast.LENGTH_LONG).show();
            }else{
                Toast.makeText(this, "storage write permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

}

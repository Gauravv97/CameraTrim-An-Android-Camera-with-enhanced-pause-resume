package com.csaproject.gehu.myapplication;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v13.app.FragmentCompat;
import android.support.v4.app.ActivityCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.googlecode.mp4parser.BasicContainer;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.features2d.ORB;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Created by Gaurav on 3/25/2018.
 */

public class RecordVideo extends Activity {
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    private static final String TAG = "Camera2VideoFragment";
    private static final int REQUEST_VIDEO_PERMISSIONS = 1;
    private static final String FRAGMENT_DIALOG = "dialog";

    private static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
    };

    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView mTextureView;

    /**
     * Button to record video
     */
    private ImageView mButtonVideo;

    /**
     * A reference to the opened {@link android.hardware.camera2.CameraDevice}.
     */
    private CameraDevice mCameraDevice;


    private ImageView mButtonPlayPause;

    private ImageView mPauseImageView;

    private Boolean mIsPaused=false;

    private ArrayList<String> pauseList;

    private DBHandler dbHandler;

    MediaMetadataRetriever mMediaMetadataRetriever;

    /**
     * A reference to the current {@link android.hardware.camera2.CameraCaptureSession} for
     * preview.
     */
    private CameraCaptureSession mPreviewSession;

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture,
                                              int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture,
                                                int width, int height) {
            configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }

    };

    /**
     * The {@link android.util.Size} of camera preview.
     */
    private Size mPreviewSize;

    /**
     * The {@link android.util.Size} of video recording.
     */
    private Size mVideoSize;

    /**
     * MediaRecorder
     */
    private MediaRecorder mMediaRecorder;

    /**
     * Whether the app is recording video now
     */
    private boolean mIsRecordingVideo;

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread,mBackgroundThread2;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its status.
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();
            mCameraOpenCloseLock.release();
            if (null != mTextureView) {
                configureTransform(mTextureView.getWidth(), mTextureView.getHeight());
            }
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            RecordVideo.this.finish();
        }

    };

    private Integer mSensorOrientation;
    private String mNextVideoAbsolutePath;
    private CaptureRequest.Builder mPreviewBuilder;
    private  Mat background;
    private ImageReader mImageReader;
    MatOfKeyPoint keyPoint2,sceneDescriptor2;
    Scalar mColorsRGB[] = new Scalar[]{new Scalar(255, 0, 0, 255), new Scalar(0, 255, 0, 255), new Scalar(0, 0, 255, 255)};
    private void StartMatching(Mat mat,MatOfKeyPoint s1,MatOfKeyPoint s2,MatOfKeyPoint k1,MatOfKeyPoint k2){
        Mat m=mat.clone();
        MatOfDMatch matches = new MatOfDMatch();
        matcher.match(s1,s2,matches);
        List<DMatch> matches_original = matches.toList();
        int DIST_LIMIT = 30;
        // Check all the matches distance and if it passes add to list of filtered matches


        KeyPoint ar1[]=k1.toArray();

        KeyPoint ar2[]=k2.toArray();
        for (int j = 0; j < matches_original.size(); j++) {
            DMatch d = matches_original.get(j);
            if (Math.abs(d.distance) <= DIST_LIMIT&&Math.abs(d.distance) >= 2) {
                Imgproc.circle(m,ar1[d.queryIdx].pt,4,mColorsRGB[2]);
                Imgproc.circle(m,ar2[d.trainIdx].pt,4,mColorsRGB[1]);
                Imgproc.line(m,ar1[d.queryIdx].pt,ar2[d.trainIdx].pt,mColorsRGB[0],2);
            }

        }
        final Bitmap bitmap= Bitmap.createBitmap(m.cols(),m.rows(),Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(m,bitmap);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPauseImageView.setImageBitmap(bitmap);
            }
        });
        m.release();
        s2.release();
    }
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;
            try{
                image = reader.acquireLatestImage();
                if (image != null) {

                    Mat background;//=imageToMat(image);

                    Bitmap bitmap = fromImage(image);
                    /*runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mPauseImageView.setImageBitmap(bitmap);
                        }
                    });*/
                    background=new Mat(image.getHeight(),image.getWidth(),CvType.CV_8UC4);
                    Utils.bitmapToMat(bitmap,background);
                    Core.rotate(background,background,Core.ROTATE_90_CLOCKWISE);

                    keyPoint2=new MatOfKeyPoint();
                    sceneDescriptor2=new MatOfKeyPoint();
                    orb.detect(background,keyPoint2);
                    orb.compute(background,keyPoint2,sceneDescriptor2);




                    /*ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    FloatBuffer floatBuffer = buffer.asFloatBuffer();
                    float[] floatArray = new float[floatBuffer.limit()];
                    floatBuffer.get(floatArray);
                    background.put(0,0,floatArray);
                    //Utils.bitmapToMat(bitmap,background);
                    Imgproc.cvtColor(background,background,Imgproc.COLOR_BGR2GRAY);
                    keyPoint2=new MatOfKeyPoint();
                    sceneDescriptor2=new MatOfKeyPoint();
                    orb.detect(background,keyPoint2);
                    orb.compute(background,keyPoint2,sceneDescriptor2);
                    sceneDescriptor2.toArray();
                    background.release();
                    keyPoint2.release();
                    //background.convertTo(background,CvType.CV_32F);*/
                    //if(sceneDescriptor2.type()==sceneDescriptors.type())
                    StartMatching(imageView,sceneDescriptors,sceneDescriptor2,keypoints,keyPoint2);
                    //sceneDescriptors.toArray();
                    image.close();
                    image=null;

                }
            } catch (Exception e) {
                Log.e("MyApp", e.getMessage());
                if(image!=null)
                    image.close();
            }
        }
        Bitmap fromImage(Image image) {
            Bitmap bmp=Bitmap.createBitmap(image.getWidth(), image.getHeight(), Bitmap.Config.ARGB_8888);
            byte[][] cachedYuvBytes = new byte[3][];;
            int[] cachedRgbBytes =new int[image.getWidth()* image.getHeight()];
            cachedRgbBytes = ImageUtils.convertImageToBitmap(image, cachedRgbBytes, cachedYuvBytes);
            bmp.setPixels(cachedRgbBytes, 0, image.getWidth(), 0, 0,
                    image.getWidth(), image.getHeight());
            return bmp;
        }


        public  Mat imageToMat(Image image) {
            ByteBuffer buffer;
            int rowStride;
            int pixelStride;
            int width = image.getWidth();
            int height = image.getHeight();
            int offset = 0;

            Image.Plane[] planes = image.getPlanes();
            byte[] data = new byte[image.getWidth() * image.getHeight() * ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8];
            byte[] rowData = new byte[planes[0].getRowStride()];

            for (int i = 0; i < planes.length; i++) {
                buffer = planes[i].getBuffer();
                rowStride = planes[i].getRowStride();
                pixelStride = planes[i].getPixelStride();
                int w = (i == 0) ? width : width / 2;
                int h = (i == 0) ? height : height / 2;
                for (int row = 0; row < h; row++) {
                    int bytesPerPixel = ImageFormat.getBitsPerPixel(ImageFormat.YUV_420_888) / 8;
                    if (pixelStride == bytesPerPixel) {
                        int length = w * bytesPerPixel;
                        buffer.get(data, offset, length);

                        if (h - row != 1) {
                            buffer.position(buffer.position() + rowStride - length);
                        }
                        offset += length;
                    } else {


                        if (h - row == 1) {
                            buffer.get(rowData, 0, width - pixelStride + 1);
                        } else {
                            buffer.get(rowData, 0, rowStride);
                        }

                        for (int col = 0; col < w; col++) {
                            data[offset++] = rowData[col * pixelStride];
                        }
                    }
                }
            }
            buffer=ByteBuffer.wrap(data);
            FloatBuffer floatBuffer = buffer.asFloatBuffer();
            float[] floatArray = new float[floatBuffer.limit()];
            floatBuffer.get(floatArray);
            Mat mat = new Mat(height , width, CvType.CV_32F);
            mat.put(0, 0, floatArray);

            return mat;
        }
    };



    /**
     * In this sample, we choose a video size with 3x4 aspect ratio. Also, we don't use sizes
     * larger than 1080p, since MediaRecorder cannot handle such a high-resolution video.
     *
     * @param choices The list of available sizes
     * @return The video size
     */
    private static Size chooseVideoSize(Size[] choices) {
        for (Size size : choices) {
            if (size.getWidth() == size.getHeight() * 4 / 3 && size.getWidth() <= 1080) {
                return size;
            }
        }
        Log.e(TAG, "Couldn't find any suitable video size");
        return choices[choices.length - 1];
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, chooses the smallest one whose
     * width and height are at least as large as the respective requested values, and whose aspect
     * ratio matches with the specified value.
     *
     * @param choices     The list of sizes that the camera supports for the intended output class
     * @param width       The minimum desired width
     * @param height      The minimum desired height
     * @param aspectRatio The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio) {
        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getHeight() == option.getWidth() * h / w &&
                    option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }

        // Pick the smallest of those, assuming we found any
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }
    DescriptorMatcher matcher;
    ORB orb;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_view);
        mTextureView = (AutoFitTextureView) findViewById(R.id.texture);
        mButtonVideo =  findViewById(R.id.video);

        mButtonVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mPauseImageView.setVisibility(View.GONE);
                if (mIsRecordingVideo) {
                    mButtonPlayPause.setVisibility(View.GONE);
                    if(!mIsPaused)
                        stopRecordingVideo();
                    else {
                        mIsRecordingVideo = false;
                        mButtonVideo.setImageResource(R.drawable.circle);
                        mNextVideoAbsolutePath = null;

                    }
                    new MergeVideo(pauseList).execute();
                    //merge all videos
                    pauseList=new ArrayList<>(2);
                    mIsPaused=false;
                } else {
                    mButtonPlayPause.setVisibility(View.VISIBLE);
                    mButtonPlayPause.setImageDrawable(getDrawable(R.drawable.ic_pause_black_24dp));
                    mIsPaused=false;
                    startRecordingVideo();
                }
            }
        });
        findViewById(R.id.info).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new AlertDialog.Builder(RecordVideo.this)
                        .setMessage(R.string.intro_message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
            }
        });
        mButtonPlayPause=findViewById(R.id.PauseButton);
        mButtonPlayPause.setVisibility(View.GONE);
        mButtonPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                togglePlayPause();
            }
        });
        OpenCVLoader.initDebug();
        mPauseImageView=findViewById(R.id.onionImageView);
        mMediaMetadataRetriever=new MediaMetadataRetriever();
        pauseList=new ArrayList<>(2);

        dbHandler=new DBHandler(this,null,null,1);

        String filepath=null;
        try{
            filepath=getIntent().getExtras().getString("filepath",null);
        }catch (Exception e){

        }

        orb=ORB.create();
        matcher=DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING);
        if(filepath!=null){
            dontDelete=true;
            pauseList.add(filepath);
            mButtonPlayPause.setVisibility(View.VISIBLE);
            mButtonPlayPause.setImageDrawable(getDrawable(R.drawable.ic_play_arrow_black_24dp));
            mIsRecordingVideo=true;
            mIsPaused=true;
            mPauseImageView.setVisibility(View.VISIBLE);
            mButtonVideo.setImageResource(R.drawable.ic_stop_black_24dp);

            int widthPixels=Resources.getSystem().getDisplayMetrics().widthPixels;
            mPauseImageView.getLayoutParams().width=widthPixels;

            //mPauseImageView.setLayoutParams(mTextureView.getLayoutParams());
            mMediaMetadataRetriever.setDataSource(RecordVideo.this, Uri.fromFile(new File(pauseList.get(pauseList.size()-1))));
            String time = mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            //Toast.makeText(this,time,Toast.LENGTH_SHORT).show();
            long timeInMillisec = Long.parseLong(time)*1000;
            Bitmap bitmap=mMediaMetadataRetriever.getFrameAtTime(timeInMillisec);

            mPauseImageView.getLayoutParams().height=bitmap.getHeight()*widthPixels/bitmap.getWidth();

            if(imageView!=null)
                imageView.release();
            imageView=new Mat(bitmap.getHeight(),bitmap.getWidth(),CvType.CV_8UC4);
            sceneDescriptors=new MatOfKeyPoint();
            keypoints=new MatOfKeyPoint();
            Utils.bitmapToMat(bitmap,imageView);
            Mat tmp=imageView.clone();
            Imgproc.cvtColor(imageView,tmp,Imgproc.COLOR_BGR2GRAY);

            orb.detect(tmp,keypoints);
            orb.compute(tmp,keypoints,sceneDescriptors);
            tmp.release();
            Utils.matToBitmap(imageView,bitmap);
            mPauseImageView.setImageBitmap(bitmap);
            mPauseImageView.setAlpha(.6f);
        }


    }
    boolean dontDelete=false;
    private void togglePlayPause(){
        if(mIsPaused){
            mButtonPlayPause.setImageDrawable(getDrawable(R.drawable.ic_pause_black_24dp));
            mIsPaused=false;
            ResumeVideo();
        }else {
            mButtonPlayPause.setImageDrawable(getDrawable(R.drawable.ic_play_arrow_black_24dp));
            mIsPaused=true;
            PauseVideo();
        }
    }
    private Mat imageView;
    MatOfKeyPoint keypoints,sceneDescriptors;
    private void PauseVideo(){

        pauseRecordingVideo();
        mPauseImageView.setVisibility(View.VISIBLE);

        int widthPixels=Resources.getSystem().getDisplayMetrics().widthPixels;
        mPauseImageView.getLayoutParams().width=widthPixels;

         //mPauseImageView.setLayoutParams(mTextureView.getLayoutParams());
        mMediaMetadataRetriever.setDataSource(RecordVideo.this, Uri.fromFile(new File(pauseList.get(pauseList.size()-1))));
        String time = mMediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
        //Toast.makeText(this,time,Toast.LENGTH_SHORT).show();
        long timeInMillisec = Long.parseLong(time)*1000;
        Bitmap bitmap=mMediaMetadataRetriever.getFrameAtTime(timeInMillisec);

        mPauseImageView.getLayoutParams().height=bitmap.getHeight()*widthPixels/bitmap.getWidth();

        if(imageView!=null)
            imageView.release();
        imageView=new Mat(bitmap.getHeight(),bitmap.getWidth(),CvType.CV_8UC4);
        sceneDescriptors=new MatOfKeyPoint();
        keypoints=new MatOfKeyPoint();
        Utils.bitmapToMat(bitmap,imageView);
        Mat tmp=imageView.clone();
        Imgproc.cvtColor(imageView,tmp,Imgproc.COLOR_BGR2GRAY);

        orb.detect(tmp,keypoints);
        orb.compute(tmp,keypoints,sceneDescriptors);
        tmp.release();
        Utils.matToBitmap(imageView,bitmap);
        mPauseImageView.setImageBitmap(bitmap);
        mPauseImageView.setAlpha(.6f);
    }
    private void pauseRecordingVideo(){
        mMediaRecorder.stop();
        mMediaRecorder.reset();
        mNextVideoAbsolutePath = null;
        startPreview();



    }
    private void ResumeVideo(){
                mPauseImageView.setVisibility(View.GONE);
        startRecordingVideo();

    }



    @Override
    public void onResume() {
        super.onResume();
        OpenCVLoader.initDebug();
        startBackgroundThread();
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread  = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());

    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

    }

    /**
     * Gets whether you should show UI with rationale for requesting permissions.
     *
     * @param permissions The permissions your app wants to request.
     * @return Whether you can show permission rationale UI.
     */
   /* private boolean shouldShowRequestPermissionRationale(String[] permissions) {
        for (String permission : permissions) {
            if (FragmentCompat.shouldShowRequestPermissionRationale(this, permission)) {
                return true;
            }
        }
        return false;
    }*/

    /**
     * Requests permissions needed for recording video.
     */
   /* private void requestVideoPermissions() {
        if (shouldShowRequestPermissionRationale(VIDEO_PERMISSIONS)) {
            new ConfirmationDialog().show(getChildFragmentManager(), FRAGMENT_DIALOG);
        } else {
            FragmentCompat.requestPermissions(this, VIDEO_PERMISSIONS, REQUEST_VIDEO_PERMISSIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (requestCode == REQUEST_VIDEO_PERMISSIONS) {
            if (grantResults.length == VIDEO_PERMISSIONS.length) {
                for (int result : grantResults) {
                    if (result != PackageManager.PERMISSION_GRANTED) {
                        ErrorDialog.newInstance(getString(R.string.permission_request))
                                .show(getChildFragmentManager(), FRAGMENT_DIALOG);
                        break;
                    }
                }
            } else {
                ErrorDialog.newInstance(getString(R.string.permission_request))
                        .show(getChildFragmentManager(), FRAGMENT_DIALOG);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private boolean hasPermissionsGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ActivityCompat.checkSelfPermission(RecordVideo.this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
*/
    /**
     * Tries to open a {@link CameraDevice}. The result is listened by `mStateCallback`.
     */
    @SuppressWarnings("MissingPermission")
    private void openCamera(int width, int height) {
       /* if (!hasPermissionsGranted(VIDEO_PERMISSIONS)) {
            requestVideoPermissions();
            return;
        }*/


        CameraManager manager = (CameraManager) RecordVideo.this.getSystemService(Context.CAMERA_SERVICE);
        try {
            Log.d(TAG, "tryAcquire");
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            String cameraId = manager.getCameraIdList()[0];

            // Choose the sizes for camera preview and video recording
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (map == null) {
                throw new RuntimeException("Cannot get available preview/video sizes");
            }
            mVideoSize = chooseVideoSize(map.getOutputSizes(MediaRecorder.class));
            mImageReader=ImageReader.newInstance(mVideoSize.getWidth(),mVideoSize.getHeight(), ImageFormat.YUV_420_888,1);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);
            mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                    width, height, mVideoSize);

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mTextureView.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            } else {
                mTextureView.setAspectRatio(mPreviewSize.getHeight(), mPreviewSize.getWidth());
            }
            configureTransform(width, height);
            mMediaRecorder = new MediaRecorder();
            manager.openCamera(cameraId, mStateCallback, null);
        } catch (CameraAccessException e) {
            Toast.makeText(RecordVideo.this, "Cannot access the camera.", Toast.LENGTH_SHORT).show();
            RecordVideo.this.finish();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            /*ErrorDialog.newInstance(getString(R.string.camera_error))
                    .show(getChildFragmentManager(), FRAGMENT_DIALOG);*/
            //Create AlertDialog later
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.");
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.");
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Start the camera preview.
     */
    private void startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);




            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            if(mIsRecordingVideo&&mIsPaused) {
                surfaces.add(mImageReader.getSurface());
                mPreviewBuilder.addTarget(mImageReader.getSurface());
            }
            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                    }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                    Toast.makeText(RecordVideo.this, "Failed", Toast.LENGTH_SHORT).show();

                }
            }, mBackgroundHandler);

            /*Surface previewSurface = new Surface(texture);
            mPreviewBuilder.addTarget(previewSurface);
            if(mIsRecordingVideo&&mIsPaused) {
                mPreviewBuilder.addTarget(mImageReader.getSurface());
                mCameraDevice.createCaptureSession(Collections.singletonList(mImageReader.getSurface()),
                        new CameraCaptureSession.StateCallback() {

                            @Override
                            public void onConfigured(@NonNull CameraCaptureSession session) {

                            }

                            @Override
                            public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                                Toast.makeText(RecordVideo.this, "Failed", Toast.LENGTH_SHORT).show();

                            }
                        }, mBackgroundHandler2);

            }


            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mPreviewSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                                Toast.makeText(RecordVideo.this, "Failed", Toast.LENGTH_SHORT).show();

                        }
                    }, mBackgroundHandler);*/
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    /**
     * Update the camera preview. {@link #startPreview()} needs to be called in advance.
     */
    private void updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            setUpCaptureRequestBuilder(mPreviewBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mPreviewSession.setRepeatingRequest(mPreviewBuilder.build(), null, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should not to be called until the camera preview size is determined in
     * openCamera, or until the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {

        if (null == mTextureView || null == mPreviewSize) {
            return;
        }
        int rotation = RecordVideo.this.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        }
        mTextureView.setTransform(matrix);
    }

    private void setUpMediaRecorder() throws IOException {

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()) {
            mNextVideoAbsolutePath = getVideoFilePath(RecordVideo.this);
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        int rotation = RecordVideo.this.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation) {
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
    }

    private String getVideoFilePath(Context context) {
        final File folder = new File(Environment.getExternalStorageDirectory().toString()
                + "/OnionVideos" );
        if (!folder.exists()) {
            folder.mkdirs();
        }

        return (Environment.getExternalStorageDirectory().toString()
                + "/OnionVideos" + "/")
                + System.currentTimeMillis() + ".mp4";
    }

    private void startRecordingVideo() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            setUpMediaRecorder();
            pauseList.add(mNextVideoAbsolutePath);
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mPreviewBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();

            // Set up Surface for the camera preview
            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mPreviewBuilder.addTarget(previewSurface);

            // Set up Surface for the MediaRecorder
            Surface recorderSurface = mMediaRecorder.getSurface();
            surfaces.add(recorderSurface);
            mPreviewBuilder.addTarget(recorderSurface);

            // Start a capture session
            // Once the session starts, we can update the UI and start recording
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    mPreviewSession = cameraCaptureSession;
                    updatePreview();
                    RecordVideo.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // UI
                            mButtonVideo.setImageResource(R.drawable.ic_stop_black_24dp);
                            mIsRecordingVideo = true;
                            // Start recording
                            mMediaRecorder.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                        Toast.makeText(RecordVideo.this, "Failed", Toast.LENGTH_SHORT).show();

                }
            }, mBackgroundHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

    }

    private void closePreviewSession() {
        if (mPreviewSession != null) {
            mPreviewSession.close();
            mPreviewSession = null;
        }
    }

    private void stopRecordingVideo() {
        // UI
        mIsRecordingVideo = false;
        mButtonVideo.setImageResource(R.drawable.circle);
        // Stop recording
        mMediaRecorder.stop();
        mMediaRecorder.reset();
/*

            Toast.makeText(RecordVideo.this, "Video saved: " + mNextVideoAbsolutePath,
                    Toast.LENGTH_SHORT).show();*/
            Log.d(TAG, "Video saved: " + mNextVideoAbsolutePath);

        mNextVideoAbsolutePath = null;
        startPreview();
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    public static class ErrorDialog extends DialogFragment {

        private static final String ARG_MESSAGE = "message";

        public static ErrorDialog newInstance(String message) {
            ErrorDialog dialog = new ErrorDialog();
            Bundle args = new Bundle();
            args.putString(ARG_MESSAGE, message);
            dialog.setArguments(args);
            return dialog;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Activity activity = getActivity();
            return new AlertDialog.Builder(activity)
                    .setMessage(getArguments().getString(ARG_MESSAGE))
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            activity.finish();
                        }
                    })
                    .create();
        }

    }

    public static class ConfirmationDialog extends DialogFragment {

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            final Fragment parent = getParentFragment();
            return new AlertDialog.Builder(getActivity())
                    .setMessage(R.string.permission_request)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            FragmentCompat.requestPermissions(parent, VIDEO_PERMISSIONS,
                                    REQUEST_VIDEO_PERMISSIONS);
                        }
                    })
                    .setNegativeButton(android.R.string.cancel,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    parent.getActivity().finish();
                                }
                            })
                    .create();
        }

    }
    public class MergeVideo extends AsyncTask<Void, Integer, String> {
        String []paths;
        String OutPath;
        public MergeVideo(ArrayList<String> paths){
            this.paths=new String[paths.size()];
            paths.toArray(this.paths);

        }
        @Override
        protected void onPreExecute() {
            /*progressDialog = ProgressDialog.show(RecordVideo.this,
                    "Preparing for upload", "Please wait...", true);
            // do initialization of required objects objects here*/
        };

        @Override
        protected String doInBackground(Void... params) {
            String FileName=null;
            if (paths.length > 1) {
                try {
                    Movie[] inMovies = new Movie[paths.length];
                    for (int i = 0; i < paths.length; i++) {
                        inMovies[i] = MovieCreator.build(paths[i]);

                    }
                    List<Track> videoTracks = new LinkedList<Track>();
                    List<Track> audioTracks = new LinkedList<Track>();
                    for (Movie m : inMovies) {
                        for (Track t : m.getTracks()) {
                            if (t.getHandler().equals("soun")) {
                                audioTracks.add(t);
                            }
                            if (t.getHandler().equals("vide")) {
                                videoTracks.add(t);
                            }
                        }
                    }

                    Movie result = new Movie();

                    if (audioTracks.size() > 0) {
                        result.addTrack(new AppendTrack(audioTracks
                                .toArray(new Track[audioTracks.size()])));
                    }
                    if (videoTracks.size() > 0) {
                        result.addTrack(new AppendTrack(videoTracks
                                .toArray(new Track[videoTracks.size()])));
                    }

                    BasicContainer out = (BasicContainer) new DefaultMp4Builder()
                            .build(result);
                    FileName=Environment.getExternalStorageDirectory().toString()
                            + "/OnionVideos" + "/ MyVid"
                            + (dbHandler.rCount()+1) + ".mp4";
                    @SuppressWarnings("resource")
                    FileChannel fc = new RandomAccessFile(String.format(FileName),
                            "rw").getChannel();
                    out.writeContainer(fc);
                    fc.close();
                } catch(FileNotFoundException e){
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                } catch(IOException e){
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                OutPath=FileName;
                return FileName;
            }
            OutPath=paths[0];
            return paths[0];
        }

        @Override
        protected void onPostExecute(String value) {
            File file;

            if(paths.length>1)
                try {

                    for (int i = 1; i < paths.length; i++){

                        file = new File(paths[i]);
                        file.delete();

                    }
                    if(!dontDelete) {
                        file = new File(paths[0]);
                        file.delete();
                    }
                    file=new File(OutPath);
                    String name=file.getName().replaceFirst("[.][^.]+$", "");
                    dbHandler.addRow(new entry(name,OutPath));
                } catch (Exception e) {
                    Log.e("my",e.getMessage());
                }
            else{
                if(!dontDelete) {
                    File from = new File(OutPath);
                    File to = new File(Environment.getExternalStorageDirectory().toString()
                            + "/OnionVideos" + "/ MyVid"
                            + (dbHandler.rCount() + 1) + ".mp4");
                    from.renameTo(to);
                    OutPath = to.getPath();
                    file = new File(OutPath);
                    String name = file.getName().replaceFirst("[.][^.]+$", "");
                    dbHandler.addRow(new entry(name, OutPath));
                }
            }

            super.onPostExecute(value);
        }

    }
}

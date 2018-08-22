package com.csaproject.gehu.myapplication;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.github.hiteshsondhi88.libffmpeg.ExecuteBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.FFmpeg;
import com.github.hiteshsondhi88.libffmpeg.LoadBinaryResponseHandler;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegCommandAlreadyRunningException;
import com.github.hiteshsondhi88.libffmpeg.exceptions.FFmpegNotSupportedException;

import org.florescu.android.rangeseekbar.RangeSeekBar;

import java.io.File;

public class trimActivity extends AppCompatActivity {
    private static final String TAG = "trim";
    RangeSeekBar rangeSeekBar ;
    VideoView videoView;
    Runnable r;
    private FFmpeg ffmpeg;
    private ProgressDialog progressDialog;
    private int stopPosition;
    private int choice;
    private int duration;
    ImageButton cutVideo;
    private TextView tvLeft, tvRight;
    private String filePath, destPath;
    private Uri selectedVideoUri;
    private DBHandler dbHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trim);

        cutVideo = (ImageButton) findViewById(R.id.trim);
        tvLeft = (TextView) findViewById(R.id.tvLeft);
        tvRight = (TextView) findViewById(R.id.tvRight);
        dbHandler = new DBHandler(this, null, null, 1);

        videoView = (VideoView) findViewById(R.id.videoView);
        rangeSeekBar = (RangeSeekBar) findViewById(R.id.rangeSeekBar);
        progressDialog = new ProgressDialog(this);
        progressDialog.setTitle(null);
        progressDialog.setCancelable(false);
        rangeSeekBar.setEnabled(false);
       // rangeSeekBar.setVisibility(View.VISIBLE);

        filePath = getIntent().getStringExtra("filepath");
        selectedVideoUri = Uri.parse( filePath);

        videoView.setVideoURI((selectedVideoUri));
        videoView.start();


        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {

            public void onPrepared(MediaPlayer mp) {
                // TODO Auto-generated method stub
                duration = mp.getDuration() / 1000;
                tvLeft.setText("00:00:00");

                tvRight.setText(getTime(mp.getDuration() / 1000));
                mp.setLooping(true);
                rangeSeekBar.setRangeValues(0, duration);
                rangeSeekBar.setSelectedMinValue(0);
                rangeSeekBar.setSelectedMaxValue(duration);
                rangeSeekBar.setEnabled(true);

                rangeSeekBar.setOnRangeSeekBarChangeListener(new RangeSeekBar.OnRangeSeekBarChangeListener() {
                    @Override
                    public void onRangeSeekBarValuesChanged(RangeSeekBar bar, Object minValue, Object maxValue) {
                        videoView.seekTo((int) minValue * 1000);

                        tvLeft.setText(getTime((int) bar.getSelectedMinValue()));

                        tvRight.setText(getTime((int) bar.getSelectedMaxValue()));

                    }
                });

                final Handler handler = new Handler();
                handler.postDelayed(r = new Runnable() {
                    @Override
                    public void run() {

                        if (videoView.getCurrentPosition() >= rangeSeekBar.getSelectedMaxValue().intValue() * 1000)
                            videoView.seekTo(rangeSeekBar.getSelectedMinValue().intValue() * 1000);
                        handler.postDelayed(r, 1000);
                    }
                }, 1000);

            }
        });


        cutVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                choice = 2;
                if (selectedVideoUri != null) {
                    executeCutVideoCommand(rangeSeekBar.getSelectedMinValue().intValue() * 1000,
                            rangeSeekBar.getSelectedMaxValue().intValue() * 1000);
                }
            }
        });

        rangeSeekBar.setEnabled(false);
        loadFFMpegBinary();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPosition = videoView.getCurrentPosition(); //stopPosition is an int
        videoView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        videoView.seekTo(stopPosition);
        videoView.start();
    }


    private String getTime(int seconds) {
        int hr = seconds / 3600;
        int rem = seconds % 3600;
        int mn = rem / 60;
        int sec = rem % 60;
        return String.format("%02d", hr) + ":" + String.format("%02d", mn) + ":" + String.format("%02d", sec);
    }
    /**
     * Load FFmpeg binary
     */
    private void loadFFMpegBinary() {
        try {
            if (ffmpeg == null) {
                Log.d(TAG, "ffmpeg : era nulo");
                ffmpeg = FFmpeg.getInstance(this);
            }
            ffmpeg.loadBinary(new LoadBinaryResponseHandler() {
                @Override
                public void onFailure() {
                    showUnsupportedExceptionDialog();
                }

                @Override
                public void onSuccess() {
                    Log.d(TAG, "ffmpeg : correct Loaded");
                }
            });
        } catch (FFmpegNotSupportedException e) {
            showUnsupportedExceptionDialog();
        } catch (Exception e) {
            Log.d(TAG, "EXception no controlada : " + e);
        }
    }

    private void showUnsupportedExceptionDialog() {
        new AlertDialog.Builder(trimActivity.this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Not Supported")
                .setMessage("Device Not Supported")
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        trimActivity.this.finish();
                    }
                })
                .create()
                .show();

    }

    /**
     * Command for cutting video
     */
    private void executeCutVideoCommand(int startMs, int endMs) {
        File moviesDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES
        );

        String yourRealPath = filePath;
        destPath = filePath.replace(".mp4", "_trim0.mp4");

        File dest = new File(destPath);
        int fileNo = 0;
        while (dest.exists()) {
            fileNo++;
            destPath = destPath.replace(fileNo-1+".mp4", fileNo+".mp4");
            dest = new File(destPath);
        }

        destPath = dest.getAbsolutePath();


        Log.d(TAG, "startTrim: src: " + yourRealPath);
        Log.d(TAG, "startTrim: dest: " + destPath);
        Log.d(TAG, "startTrim: startMs: " + startMs);
        Log.d(TAG, "startTrim: endMs: " + endMs);


        //String[] complexCommand = {"-i", yourRealPath, "-ss", "" + startMs / 1000, "-t", "" + endMs / 1000, dest.getAbsolutePath()};


        String[] complexCommand = {"-ss", "" + startMs / 1000, "-y", "-i", yourRealPath, "-t", "" + (endMs - startMs) / 1000,"-vcodec", "mpeg4", "-b:v", "2097152", "-b:a", "48000", "-ac", "2", "-ar", "22050", destPath};
       try {
           execFFmpegBinary(complexCommand);
       }
       catch(Exception e){
           Toast.makeText(getApplicationContext(),"trim failed", Toast.LENGTH_SHORT).show();
       }

    }

    /**
     * Executing ffmpeg binary
     */
    private void execFFmpegBinary(final String[] command) {
        try {
            ffmpeg.execute(command, new ExecuteBinaryResponseHandler() {
                @Override
                public void onFailure(String s) {
                    Toast.makeText(getApplicationContext(), "Trim Failed", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "FAILED with output : " + s);
                }

                @Override
                public void onSuccess(String s) {
                    Log.d(TAG, "SUCCESS with output : " + s);
                    Toast.makeText(getApplicationContext(),"Trim Finished", Toast.LENGTH_SHORT).show();
                    videoView.resume();

              try{
                String name=destPath.replaceFirst("[.][^.]+$", "");
                 dbHandler.addRow(new entry(name,destPath));
                  Toast.makeText(getApplicationContext(),"Entered in Db", Toast.LENGTH_SHORT).show();
                }
                catch(Exception e){
                    Toast.makeText(getApplicationContext(),"Db entry failed", Toast.LENGTH_SHORT).show();
              }
                }

                @Override
                public void onProgress(String s) {
                    Log.d(TAG, "Started command : ffmpeg " + s);
                        progressDialog.setMessage("progress : " + s);
                    Log.d(TAG, "progress : " + s);

                }

                @Override
                public void onStart() {
                    Log.d(TAG, "Started command : ffmpeg " + command);
                    videoView.pause();
                    progressDialog.setMessage("Processing...");
                    progressDialog.show();
                    Toast.makeText(getApplicationContext(), "Trim Started", Toast.LENGTH_SHORT).show();

                }

                @Override
                public void onFinish() {
                    Log.d(TAG, "Finished command : ffmpeg " + command);
                        progressDialog.dismiss();

                }
            });
        } catch (FFmpegCommandAlreadyRunningException e) {
            // do nothing for now
        }
    }
}



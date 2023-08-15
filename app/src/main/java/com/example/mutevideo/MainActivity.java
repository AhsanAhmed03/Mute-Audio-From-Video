package com.example.mutevideo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    File outputdirectory = new File(String.valueOf(Environment.getExternalStoragePublicDirectory
            (Environment.DIRECTORY_DOWNLOADS)));
    String outputPath = outputdirectory + File.separator + "FileName" + ".mp4";
    String input_videoPath;
    private ActivityResultLauncher<Intent> videoLauncher;
    Button pick_video, muteVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pick_video = findViewById(R.id.btn_pickvideo);
        muteVideo = findViewById(R.id.btn_mutevideo);

        muteVideo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    muteVideo(input_videoPath,outputPath,0,0,false,true);
                } catch (IOException e) {
                    Toast.makeText(MainActivity.this,"Error in Mute Video"+ e,
                            Toast.LENGTH_LONG).show();
                }
            }
        });

        pick_video.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pickVideo();
            }
        });

        videoLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                result -> {
            if (result.getResultCode() == Activity.RESULT_OK){
                Intent intent = result.getData();
                if (intent != null){
                    Uri videouri = intent.getData();
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(videouri);
                        File temp = new File(getCacheDir(),"video.mp4");
                        OutputStream outputStream = new FileOutputStream(temp);
                        byte []  buffer = new byte[1024];
                        int lenth;
                        while ((lenth = inputStream.read(buffer))>0){
                            outputStream.write(buffer, 0, lenth);
                        }
                        outputStream.close();
                        inputStream.close();

                        input_videoPath = temp.getAbsolutePath();

                        Toast.makeText(this,"Video Picked Successfully ",Toast.LENGTH_LONG).show();

                    }catch (Exception e){
                        Toast.makeText(this,"Error Picking Video "+e,Toast.LENGTH_LONG).show();
                    }
                }
            }
                });
    }

    private void pickVideo(){
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("video/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        videoLauncher.launch(intent);
    }

    @SuppressLint("WrongConstant")
    public void muteVideo(String videoSrc, String video_outputPath, int start,
                          int end, boolean audio, boolean video) throws IOException {

        MediaExtractor mediaExtractor = new MediaExtractor();
        mediaExtractor.setDataSource(videoSrc);
        int count_track = mediaExtractor.getTrackCount();

        MediaMuxer mediaMuxer;
        mediaMuxer = new MediaMuxer(video_outputPath,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

        HashMap<Integer, Integer> mapIndex = new HashMap<>(count_track);
        int sizeBuffer = -1;

        for (int i = 0; i < count_track; i++){
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(i);
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            boolean selectTrack = false;

            if (mime.startsWith("audio/") && audio){
                selectTrack = true;
            } else if (mime.startsWith("video/") && video) {
                selectTrack = true;
            }
            if (selectTrack){
                mediaExtractor.selectTrack(i);
                int index = mediaMuxer.addTrack(mediaFormat);
                mapIndex.put(i, index);
                if (mediaFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)){
                    int sizeNew = mediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    sizeBuffer = sizeNew > sizeBuffer ? sizeNew : sizeBuffer;
                }
            }
        }

        if (sizeBuffer < 0){
            sizeBuffer = 1 * 1024 * 1024;
        }

        MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
        metadataRetriever.setDataSource(videoSrc);

        String string = metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        if (string != null){
            int degrees = Integer.parseInt(string);
            if (degrees >= 0){
                mediaMuxer.setOrientationHint(degrees);
            }
        }

        if (start >0 ){
            mediaExtractor.seekTo(start * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }

        int setOff = 0;
        int indextrack = -1;

        ByteBuffer buffer = ByteBuffer.allocate(sizeBuffer);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        mediaMuxer.start();

        while (true){
            bufferInfo.offset = setOff;
            bufferInfo.size = mediaExtractor.readSampleData(buffer,setOff);
            if (bufferInfo.size < 0){
                bufferInfo.size = 0;
                break;
            }else {
                bufferInfo.presentationTimeUs = mediaExtractor.getSampleTime();
                if (end > 0 && bufferInfo.presentationTimeUs > (end + 1000)){
                    break;
                }else {
                    bufferInfo.flags = mediaExtractor.getSampleFlags();
                    indextrack = mediaExtractor.getSampleTrackIndex();
                    mediaMuxer.writeSampleData(mapIndex.get(indextrack),buffer ,bufferInfo);
                    mediaExtractor.advance();
                }
            }
        }
        mediaMuxer.stop();
        mediaMuxer.release();
        Toast.makeText(this,"Video Muted Successfully ",Toast.LENGTH_LONG).show();
    }
}
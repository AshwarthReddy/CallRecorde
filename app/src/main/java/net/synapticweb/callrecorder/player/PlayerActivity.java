/*
 * Copyright (C) 2019 Eugen Rădulescu <synapticwebb@gmail.com> - All rights reserved.
 *
 * You may use, distribute and modify this code only under the conditions
 * stated in the SW Call Recorder license. You should have received a copy of the
 * SW Call Recorder license along with this file. If not, please write to <synapticwebb@gmail.com>.
 */

package net.synapticweb.callrecorder.player;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;

import net.synapticweb.callrecorder.CrLog;
import net.synapticweb.callrecorder.R;
import net.synapticweb.callrecorder.BaseActivity;
import net.synapticweb.callrecorder.Util;
import net.synapticweb.callrecorder.contactdetail.ContactDetailFragment;
import net.synapticweb.callrecorder.data.Recording;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.chibde.visualizer.LineBarVisualizer;
import com.sdsmdg.harjot.crollerTest.Croller;
import net.synapticweb.callrecorder.data.ServerResponse;
import net.synapticweb.callrecorder.retrofit.APIClient;
import net.synapticweb.callrecorder.retrofit.APIInterface;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.json.JSONException;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PlayerActivity extends BaseActivity {
    AudioPlayer player;
    Recording recording;
    ImageButton playPause, resetPlaying;
    TextView recordingInfo;
    SeekBar playSeekBar;
    TextView playedTime, totalTime, show_transcription, audio_result;
    ImageView go_back;
    LinearLayout bottom_text;
    boolean userIsSeeking = false;
    LineBarVisualizer visualizer;
    AudioManager audioManager;
    int phoneVolume;
    Croller gainControl, volumeControl;
    final static int AUDIO_SESSION_ID = 0;
    final static String IS_PLAYING = "is_playing";
    final static String CURRENT_POS = "current_pos";
    final static int DENSITY_PORTRAIT = 70;
    final static int DENSITY_LANDSCAPE = 150;
    public static final String RECORDING_EXTRA = "recording_extra";

    public Fragment createFragment() {return null;}

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme();
        setContentView(R.layout.player_activity);

        Toolbar toolbar = findViewById(R.id.toolbar_player);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if(actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle(R.string.player_title);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        recording = getIntent().getParcelableExtra(ContactDetailFragment.RECORDING_EXTRA);
        visualizer = findViewById(R.id.visualizer);
        visualizer.setColor(getResources().getColor(R.color.colorAccentLighter));
        visualizer.setDensity(getResources().getConfiguration().orientation ==
                Configuration.ORIENTATION_PORTRAIT ? DENSITY_PORTRAIT : DENSITY_LANDSCAPE);
        //crash report nr. 886:
        try {
            visualizer.setPlayer(AUDIO_SESSION_ID);
        }
        catch (Exception exc) {
            CrLog.log(CrLog.ERROR, "Error initializing visualizer.");
            visualizer = null;
        }
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        playPause = findViewById(R.id.test_player_play_pause);
        resetPlaying = findViewById(R.id.test_player_reset);
        playSeekBar = findViewById(R.id.play_seekbar);
        playedTime = findViewById(R.id.test_play_time_played);
        totalTime = findViewById(R.id.test_play_total_time);
        show_transcription = findViewById(R.id.show_transcription);
        audio_result = findViewById(R.id.audio_result);
        bottom_text = findViewById(R.id.bottom_text);
        go_back = findViewById(R.id.go_back);

        show_transcription.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    audio_result.setText("Waiting for transcription...");
                    show_transcription.setBackground(getApplicationContext().getDrawable(R.drawable.rounded_corner_disable));
                    show_transcription.setPadding(50, 30, 50 ,30);
                    show_transcription.setEnabled(false);
                    sendFile(recording.getPath());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                /*Intent playIntent = new Intent(PlayerActivity.this, VoskActivity.class);
                playIntent.putExtra(RECORDING_EXTRA, recording);
                startActivity(playIntent);*/
            }
        });

        playPause.setOnClickListener((view) -> {
                if(player.getPlayerState() == PlayerAdapter.State.PLAYING) {
                    player.pause();
                    playPause.setBackground(getResources().getDrawable(R.drawable.player_play));
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
                else if(player.getPlayerState() == PlayerAdapter.State.PAUSED ||
                        player.getPlayerState() == PlayerAdapter.State.INITIALIZED){
                    player.play();
                    playPause.setBackground(getResources().getDrawable(R.drawable.player_pause));
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
        });

        resetPlaying.setOnClickListener((view) -> {
                if(player.getPlayerState() == PlayerAdapter.State.PLAYING)
                    playPause.setBackground(getResources().getDrawable(R.drawable.player_play));
                player.reset();
        });

        go_back.setOnClickListener(view -> onBackPressed());

        playSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int userSelectedPosition = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if(fromUser)
                    userSelectedPosition = progress;
                playedTime.setText(Util.getDurationHuman(progress, false));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { userIsSeeking = true; }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                userIsSeeking = false;
                player.seekTo(userSelectedPosition);
            }
        });

        gainControl = findViewById(R.id.gain_control);
        gainControl.setOnProgressChangedListener((progress) ->
                player.setGain((float) progress)
        );

        volumeControl = findViewById(R.id.volume_control);
        if(audioManager != null) {
            volumeControl.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
            phoneVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            volumeControl.setProgress(phoneVolume);
        }
        volumeControl.setOnProgressChangedListener( (progress) ->
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0)
        );

        recordingInfo = findViewById(R.id.recording_info);
        recordingInfo.setText(recording.getName());


        //recordingInfo.setText(String.format(getResources().getString(R.string.recording_info),
          //      recording.getName(), recording.getHumanReadingFormat(getApplicationContext())));
//        Log.wtf(TAG, "Available width: " + getResources().getDisplayMetrics().widthPixels);
//        Log.wtf(TAG, "Density: " + getResources().getDisplayMetrics().density);
//        Log.wtf(TAG, "Density dpi: " + getResources().getDisplayMetrics().densityDpi);
//        Log.wtf(TAG, "Density scaled: " + getResources().getDisplayMetrics().scaledDensity);
    }

    //necesar pentru că dacă apăs pur și simplu pe săgeata back îmi apelează onCreate al activității contactdetail
    //fără un obiect Contact valid.
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        else
            return super.onOptionsItemSelected(item);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if(visualizer != null) {
            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
                visualizer.setDensity(DENSITY_LANDSCAPE);
            else
                visualizer.setDensity(DENSITY_PORTRAIT);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
//        if(player.getPlayerState() == PlayerAdapter.State.UNINITIALIZED ||
//                player.getPlayerState() == PlayerAdapter.State.STOPPED) {
        player = new AudioPlayer(new PlaybackListener());
        playedTime.setText("00:00");
        if(!player.loadMedia(recording.getPath()))
            return ;

        totalTime.setText(Util.getDurationHuman(player.getTotalDuration(), false));
        player.setGain(gainControl.getProgress());
//        }
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        int currentPosition = pref.getInt(CURRENT_POS, 0);
        boolean isPlaying = pref.getBoolean(IS_PLAYING, true);
        if(!player.setMediaPosition(currentPosition)) {
            return ;
        }

        if(isPlaying) {
            playPause.setBackground(getResources().getDrawable(R.drawable.player_pause));
            player.play();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
        else {
            playPause.setBackground(getResources().getDrawable(R.drawable.player_play));
            player.setPlayerState(PlayerAdapter.State.PAUSED);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();
        editor.putInt(CURRENT_POS, player.getCurrentPosition());
        editor.putBoolean(IS_PLAYING, player.getPlayerState() == PlayerAdapter.State.PLAYING);
        editor.apply();
        player.stopPlayer();
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); //e necesar?
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = pref.edit();
        editor.remove(IS_PLAYING);
        editor.remove(CURRENT_POS);
        editor.apply();
        if(visualizer != null)
            visualizer.release();
        if(audioManager != null)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, phoneVolume, 0);
    }

    class PlaybackListener implements PlaybackListenerInterface {
        @Override
        public void onDurationChanged(int duration) {
            playSeekBar.setMax(duration);
        }

        @Override
        public void onPositionChanged(int position) {
            if(!userIsSeeking) {
                if(Build.VERSION.SDK_INT >= 24)
                    playSeekBar.setProgress(position, true);
                else
                    playSeekBar.setProgress(position);
            }
        }

        @Override
        public void onPlaybackCompleted() {
            //a trebuit să folosesc asta pentru că în lolipop crăpa zicînd că nu am voie să updatez UI din thread secundar.
            playPause.post(() ->
                    playPause.setBackground(getResources().getDrawable(R.drawable.player_play))
            );
            player.reset();
        }

        @Override
        public void onError() {
            playPause.setBackground(getResources().getDrawable(R.drawable.player_play));
            playPause.setEnabled(false);
            resetPlaying.setEnabled(false);
            totalTime.setText("00:00");
            playSeekBar.setEnabled(false);
            recordingInfo.setText(getResources().getString(R.string.player_error));
            recordingInfo.setTextColor(getResources().getColor(R.color.red));
            volumeControl.setEnabled(false);
            gainControl.setEnabled(false);
        }

        @Override
        public void onReset() {
            player = new AudioPlayer(new PlaybackListener());
            if(player.loadMedia(recording.getPath()))
                player.setGain(gainControl.getProgress());
        }
    }


    public void sendFile(String mediaPath) throws IOException, URISyntaxException {
        // Map is used to multipart the file using okhttp3.RequestBody
        //etResources().getAssets().open(  "10001-90210-01803.wav").toString()
        // File file = new File(getResources().getAssets().open("10001-90210-01803.wav").toString());
        System.out.println("-=====--" + mediaPath);
        File file = new File(mediaPath);

        // Parsing any Media type file
        RequestBody requestBody = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        MultipartBody.Part fileToUpload = MultipartBody.Part.createFormData("file", file.getName(), requestBody);
        RequestBody filename = RequestBody.create(MediaType.parse("*/*"), file.getName());

        APIInterface getResponse = APIClient.getClient().create(APIInterface.class);
        Call<ServerResponse> call = getResponse.uploadFile(fileToUpload, filename);
        call.enqueue(new Callback<ServerResponse>() {
            @Override
            public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                Toast.makeText(getApplicationContext(), response.message(), Toast.LENGTH_SHORT).show();
                 //audio_result.setText("");
                //audio_result.setVisibility(View.GONE);
                bottom_text.setVisibility(View.GONE);
                System.out.println(response.body());
                printValues(response.body());
            }

            @Override
            public void onFailure(Call<ServerResponse> call, Throwable t) {
                Toast.makeText(getApplicationContext(), t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }


    public void printValues(ServerResponse responseValue) {
        try {
            audio_result.setText("");
            ServerResponse sr = responseValue;
            JSONObject jj = new JSONObject(String.valueOf(sr.getData()));
           // JSONObject obj = new JSONObject(jj1.toString());
           // JSONObject jj = obj.getJSONObject("minutes");

            for (Iterator<String> it = jj.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = jj.getString(key);
                String[] finalValues = value.split("(?=[0-9])");
                StringBuilder sb = new StringBuilder();
                for(String part : finalValues) {
                    String pattern = "\\d+\\.";

                    Pattern regexPattern = Pattern.compile(pattern);
                    Matcher matcher = regexPattern.matcher(part);
                    if(matcher.find()) {
                        part += "<br>";
                    }
                    sb.append(part);
                }
                audio_result.setVisibility(View.VISIBLE);
                bottom_text.setVisibility(View.VISIBLE);
                String keyValue = "<b>" + getCapsSentences(key.replace("_" , " ")) + "</b> ";
                audio_result.append(Html.fromHtml("<br>" + keyValue + " : <br>" +  sb.toString() + "<br>"));
                show_transcription.setBackground(getApplicationContext().getDrawable(R.drawable.rounded_corner));
                show_transcription.setPadding(50, 30, 50 ,30);
                show_transcription.setEnabled(true);
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void loadDefaultValues() {

    }


    private String getCapsSentences(String tagName) {
        String[] splits = tagName.toLowerCase().split(" ");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < splits.length; i++) {
            String eachWord = splits[i];
            if (i > 0 && !eachWord.isEmpty()) {
                sb.append(" ");
            }
            String cap = eachWord.substring(0, 1).toUpperCase()
                    + eachWord.substring(1);
            sb.append(cap);
        }
        return sb.toString();
    }
}

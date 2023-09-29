// Copyright 2019 Alpha Cephei Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//       http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.synapticweb.callrecorder.player;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.gson.Gson;

import net.synapticweb.callrecorder.BaseActivity;
import net.synapticweb.callrecorder.R;
import net.synapticweb.callrecorder.contactdetail.ContactDetailFragment;
import net.synapticweb.callrecorder.data.DataObject;
import net.synapticweb.callrecorder.data.Recording;
import net.synapticweb.callrecorder.data.ServerResponse;
import net.synapticweb.callrecorder.retrofit.APIClient;
import net.synapticweb.callrecorder.retrofit.APIInterface;

import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONStringer;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import org.vosk.android.SpeechStreamService;
import org.vosk.android.StorageService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


/*
url
http://192.168.152.1:5001/health


curl --location 'http://10.114.73.170:5001/processAudio' \
--header 'Content-Type: multipart/form-data' \
--form 'audioFiles=@"/Users/clinton/Desktop/MOM/temp copy.wav"'

curl --location 'http://10.114.73.170:5001/health'


* */
public class VoskActivity extends BaseActivity implements
        RecognitionListener {

    static private final int STATE_START = 0;
    static private final int STATE_READY = 1;
    static private final int STATE_DONE = 2;
    static private final int STATE_FILE = 3;
    static private final int STATE_MIC = 4;

    /* Used to handle permission request */
    private static final int PERMISSIONS_REQUEST_RECORD_AUDIO = 1;

    private Model model;
    private SpeechService speechService;
    private SpeechStreamService speechStreamService;
    private TextView resultView, audio_result;
    Recording recording;

    APIInterface apiInterface;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.main);

        // Setup layout
        resultView = findViewById(R.id.result_text);
        audio_result = findViewById(R.id.audio_result);
        setUiState(STATE_START);

        //localFile
        findViewById(R.id.localFile).setOnClickListener(view -> recognizeLocalFile());
        findViewById(R.id.check_api).setOnClickListener(view -> checkAPI());
        findViewById(R.id.recognize_file).setOnClickListener(view -> recognizeFile());
        findViewById(R.id.recognize_mic).setOnClickListener(view -> recognizeMicrophone());


        findViewById(R.id.post_file).setOnClickListener(view -> {
            try {
                sendFile(recording.getPath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });
        ((ToggleButton) findViewById(R.id.pause)).setOnCheckedChangeListener((view, isChecked) -> pause(isChecked));
        apiInterface = APIClient.getClient().create(APIInterface.class);
        LibVosk.setLogLevel(LogLevel.INFO);

        // Check if user has given permission to record audio, init the model after permission is granted
        int permissionCheck = ContextCompat.checkSelfPermission(getApplicationContext(), Manifest.permission.RECORD_AUDIO);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSIONS_REQUEST_RECORD_AUDIO);
        } else {
            initModel();
        }

        //printValues(new ServerResponse());
        recording = getIntent().getParcelableExtra(ContactDetailFragment.RECORDING_EXTRA);
    }

    private void initModel() {
        StorageService.unpack(this, "model-en-us", "model",
                (model) -> {
                    this.model = model;
                    setUiState(STATE_READY);
                },
                (exception) -> setErrorState("Failed to unpack the model" + exception.getMessage()));
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_REQUEST_RECORD_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Recognizer initialization is a time-consuming and it involves IO,
                // so we execute it in async task
                initModel();
            } else {
                finish();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (speechService != null) {
            speechService.stop();
            speechService.shutdown();
        }

        if (speechStreamService != null) {
            speechStreamService.stop();
        }
    }

    @Override
    public void onResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }

    @Override
    public void onFinalResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
        setUiState(STATE_DONE);
        if (speechStreamService != null) {
            speechStreamService = null;
        }
    }

    @Override
    public void onPartialResult(String hypothesis) {
        resultView.append(hypothesis + "\n");
    }

    @Override
    public void onError(Exception e) {
        setErrorState(e.getMessage());
    }

    @Override
    public void onTimeout() {
        setUiState(STATE_DONE);
    }

    private void setUiState(int state) {
        switch (state) {
            case STATE_START:
                resultView.setText(R.string.preparing);
                resultView.setMovementMethod(new ScrollingMovementMethod());
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_READY:
                resultView.setText(R.string.ready);
                ((TextView) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_DONE:
                ((TextView) findViewById(R.id.recognize_file)).setText(R.string.recognize_file);
                ((TextView) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_FILE:
                ((TextView) findViewById(R.id.recognize_file)).setText(R.string.stop_file);
                resultView.setText(getString(R.string.starting));
                findViewById(R.id.recognize_mic).setEnabled(false);
                findViewById(R.id.recognize_file).setEnabled(true);
                findViewById(R.id.pause).setEnabled((false));
                break;
            case STATE_MIC:
                ((TextView) findViewById(R.id.recognize_mic)).setText(R.string.stop_microphone);
                resultView.setText(getString(R.string.say_something));
                findViewById(R.id.recognize_file).setEnabled(false);
                findViewById(R.id.recognize_mic).setEnabled(true);
                findViewById(R.id.pause).setEnabled((true));
                break;
            default:
                throw new IllegalStateException("Unexpected value: " + state);
        }
    }

    private void setErrorState(String message) {
        resultView.setText(message);
        ((TextView) findViewById(R.id.recognize_mic)).setText(R.string.recognize_microphone);
        findViewById(R.id.recognize_file).setEnabled(false);
        findViewById(R.id.recognize_mic).setEnabled(false);
    }

    private void recognizeLocalFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            setUiState(STATE_FILE);
            try {
                Recognizer rec = new Recognizer(model, 16000.f, "[\"one zero zero zero one\", " +
                        "\"oh zero one two three four five six seven eight nine\", \"[unk]\"]");

                InputStream ais = getAssets().open("10001-90210-01803.wav");
                if (ais.skip(44) != 44) throw new IOException("File too short");

                speechStreamService = new SpeechStreamService(rec, ais, 16000);
                speechStreamService.start(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void recognizeFile() {
        if (speechStreamService != null) {
            setUiState(STATE_DONE);
            speechStreamService.stop();
            speechStreamService = null;
        } else {
            setUiState(STATE_FILE);
            try {
                Recognizer rec = new Recognizer(model, 16000.f, "[\"one zero zero zero one\", " +
                        "\"oh zero one two three four five six seven eight nine\", \"[unk]\"]");

                InputStream ais = null;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    ais = Files.newInputStream(Paths.get(recording.getPath()));
                }
                //getAssets().open(recording.getPath());
                //getAssets().open(  "10001-90210-01803.wav");
                if (ais.skip(44) != 44) throw new IOException("File too short");

                speechStreamService = new SpeechStreamService(rec, ais, 16000);
                speechStreamService.start(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }

    private void recognizeMicrophone() {
        if (speechService != null) {
            setUiState(STATE_DONE);
            speechService.stop();
            speechService = null;
        } else {
            setUiState(STATE_MIC);
            try {
                Recognizer rec = new Recognizer(model, 16000.0f);
                speechService = new SpeechService(rec, 16000.0f);
                speechService.startListening(this);
            } catch (IOException e) {
                setErrorState(e.getMessage());
            }
        }
    }


    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    private void pause(boolean checked) {
        if (speechService != null) {
            speechService.setPause(checked);
        }
    }

    @Override
    protected Fragment createFragment() {
        return null;
    }


    public void checkAPI() {
        APIInterface getResponse = APIClient.getClient().create(APIInterface.class);
        Call<ServerResponse> call = getResponse.checkAPIworking();

        call.enqueue(new Callback<ServerResponse>() {
            @Override
            public void onResponse(Call<ServerResponse> call, Response<ServerResponse> response) {
                ServerResponse serverResponse = response.body();
                if (serverResponse != null) {
                    if (serverResponse.getStatusCode() == 200) {
                        Toast.makeText(getApplicationContext(), serverResponse.getMessage(), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(getApplicationContext(), serverResponse.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    assert serverResponse != null;
                    Log.v("Response", serverResponse.toString());
                }

            }

            @Override
            public void onFailure(Call<ServerResponse> call, Throwable t) {

            }
        });
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
                printValues(response.body());
            }

            @Override
            public void onFailure(Call<ServerResponse> call, Throwable t) {
                Toast.makeText(getApplicationContext(), t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

    }


    public void printValues(ServerResponse serverValue) {
        try {

            /*ServerResponse sr = new ServerResponse();
            DataObject dto = new DataObject();
            JSONObject jj = new JSONObject("{" +
                    "      \"summary\": \"hello how are u\"," +
                    "      \"Action_items\": \"call me at 7\"" +
                    "    }");
            dto.setMinutes(jj);
            sr.setData(dto);
            sr.setMessage("success");
            sr.setStatus(true);
            sr.setStatusCode(200);
            sr.setSuccess(true);


            {"statusCode": 200, "status": true, "message": "success", "data": {"minutes": {"Summary_Meeting": "Credit cards to be explained, bonus points are explained", "Action_Items": "1. Send brochures\n2. Schedule meeting at 2 pm\n3.Webiste link to be shared", "Minutes_of_Meeting": "Jade wants to apply for credit card on his salary and a card on his FD"}}}

            */

            ServerResponse sr = serverValue;

            System.out.println(" ==--jsonobj" + sr.toString());

            JSONObject jj = new JSONObject(serverValue.getData().getMinutes().toString());

            System.out.println(" ==--jsonobj" + jj);
            //loadJSONFromAsset()
            //JSONObject obj = new JSONObject(jsonObject.toString());
            //JSONObject dataObject = obj.getJSONObject("data");
            JSONObject minutesObject = serverValue.getData().getMinutes(); //.getJSONObject("minutes");
            for (Iterator<String> it = minutesObject.keys(); it.hasNext(); ) {
                String key = it.next();
                String value = minutesObject.getString(key);
                System.out.println(key + " : " + value);
                audio_result.append(key + " : " + value + " \n\n");
            }

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }


    public String loadJSONFromAsset() {
        String json = null;
        try {
            InputStream is = getApplicationContext().getAssets().open("audio_convertion.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, "UTF-8");
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
    }


}

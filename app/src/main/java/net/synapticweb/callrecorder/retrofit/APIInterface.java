package net.synapticweb.callrecorder.retrofit;

import net.synapticweb.callrecorder.data.ServerResponse;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import org.json.JSONObject;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.HEAD;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface APIInterface {

    @POST("processAudio")
    @Multipart
    Call<ServerResponse>   uploadFile(
            @Part MultipartBody.Part file,
            @Part("audioFiles") RequestBody name);

     @GET("health")
    Call<ServerResponse> checkAPIworking();

}

package ml.docilealligator.infinityforreddit.apis;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.HEAD;
import retrofit2.http.HeaderMap;
import retrofit2.http.Path;

public interface RedgifsAPI {
    @GET("/v2/gifs/{id}")
    Call<String> getRedgifsData(@HeaderMap Map<String, String> headers, @Path("id") String id);

    @GET("/v2/auth/temporary")
    Call<String> getTemporaryAccessToken();

    @FormUrlEncoded
    @POST("/v2/oauth/client")
    Call<String> getRedgifsAccessToken(@FieldMap Map<String, String> params);
}

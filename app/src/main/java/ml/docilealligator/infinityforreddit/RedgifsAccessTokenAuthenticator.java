package ml.docilealligator.infinityforreddit;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import ml.docilealligator.infinityforreddit.apis.RedgifsAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

public class RedgifsAccessTokenAuthenticator implements Interceptor {
    private static final String TAG = "RedgifsAuth";
    private SharedPreferences mCurrentAccountSharedPreferences;

    public RedgifsAccessTokenAuthenticator(SharedPreferences defaultSharedPreferences) {
        this.mDefaultSharedPreferences = defaultSharedPreferences;
    }

    /**
     * Fetches a fresh temporary Redgifs access token using the public /v2/auth/temporary endpoint.
     * This endpoint requires no client credentials and is the only reliable strategy since
     * Redgifs revoked all third-party OAuth client credentials.
     */
    public String refreshAccessToken() {
        Log.d(TAG, "Refreshing Redgifs access token via /v2/auth/temporary");
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(APIUtils.REDGIFS_API_BASE_URI)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build();
        RedgifsAPI api = retrofit.create(RedgifsAPI.class);

        try {
            retrofit2.Response<String> tempResponse = api.getTemporaryAccessToken().execute();
            if (tempResponse.isSuccessful() && tempResponse.body() != null) {
                JSONObject jsonObject = new JSONObject(tempResponse.body());
                // Response uses "token" key per current Redgifs API spec; accept fallback keys defensively
                String newAccessToken;
                if (jsonObject.has("token")) {
                    newAccessToken = jsonObject.getString("token");
                } else if (jsonObject.has("access_token")) {
                    newAccessToken = jsonObject.getString("access_token");
                } else if (jsonObject.has("accessToken")) {
                    newAccessToken = jsonObject.getString("accessToken");
                } else {
                    newAccessToken = "";
                }

                if (!newAccessToken.isEmpty()) {
                    mCurrentAccountSharedPreferences.edit()
                            .putString(SharedPreferencesUtils.REDGIFS_ACCESS_TOKEN, newAccessToken)
                            .apply();
                    Log.d(TAG, "Redgifs temporary token refresh succeeded");
                    return newAccessToken;
                }

                Log.w(TAG, "Redgifs /v2/auth/temporary response had no recognised token key. Body: " + tempResponse.body());
            } else {
                String errorBody = "";
                try {
                    if (tempResponse.errorBody() != null) errorBody = tempResponse.errorBody().string();
                } catch (IOException ignored) {}
                Log.e(TAG, "Redgifs /v2/auth/temporary failed, code=" + tempResponse.code() + " body=" + errorBody);
            }
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Redgifs temporary token fetch threw an exception", e);
        }

        Log.e(TAG, "Unable to obtain a Redgifs access token");
        return "";
    }

    private String extractAccessToken(String accessTokenHeader) {
        if (accessTokenHeader == null) {
            return "";
        }

        if (accessTokenHeader.toLowerCase().startsWith("bearer ")) {
            return accessTokenHeader.substring("bearer ".length()).trim();
        }

        return accessTokenHeader.trim();
    }

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        okhttp3.Request request = chain.request();
        String requestAuthHeader = request.header(APIUtils.AUTHORIZATION_KEY);
        String requestToken = extractAccessToken(requestAuthHeader);

        if (requestAuthHeader != null && requestToken.isEmpty()) {
            synchronized (this) {
                String sharedToken = mCurrentAccountSharedPreferences.getString(SharedPreferencesUtils.REDGIFS_ACCESS_TOKEN, "");
                if (sharedToken == null || sharedToken.isEmpty()) {
                    sharedToken = refreshAccessToken();
                }

                if (sharedToken != null && !sharedToken.isEmpty()) {
                    Log.d(TAG, "Bootstrapping empty Redgifs token before request");
                    request = request.newBuilder().headers(Headers.of(APIUtils.getRedgifsOAuthHeader(sharedToken))).build();
                }
            }
        }

        Response response = chain.proceed(request);
        if (response.code() == 401 || response.code() == 400) {
            Log.w(TAG, "Redgifs request unauthorized, code=" + response.code());
            String accessTokenHeader = response.request().header(APIUtils.AUTHORIZATION_KEY);
            if (accessTokenHeader == null) {
                return response;
            }

            String accessToken = extractAccessToken(accessTokenHeader);
            synchronized (this) {
                String accessTokenFromSharedPreferences = mDefaultSharedPreferences.getString(SharedPreferencesUtils.REDGIFS_ACCESS_TOKEN, "");
                if (accessToken.equals(accessTokenFromSharedPreferences)) {
                    String newAccessToken = refreshAccessToken();
                    if (!newAccessToken.equals("")) {
                        response.close();
                        Log.d(TAG, "Retrying Redgifs request with refreshed token");
                        return chain.proceed(response.request().newBuilder().headers(Headers.of(APIUtils.getRedgifsOAuthHeader(newAccessToken))).build());
                    } else {
                        Log.e(TAG, "Redgifs token refresh failed after unauthorized response");
                        return response;
                    }
                } else {
                    response.close();
                    Log.d(TAG, "Retrying Redgifs request with token already updated by another thread");
                    return chain.proceed(response.request().newBuilder().headers(Headers.of(APIUtils.getRedgifsOAuthHeader(accessTokenFromSharedPreferences))).build());
                }
            }
        }
        return response;
    }
}

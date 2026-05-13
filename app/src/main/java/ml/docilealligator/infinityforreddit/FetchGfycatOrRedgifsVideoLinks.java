package ml.docilealligator.infinityforreddit;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.apis.GfycatAPI;
import ml.docilealligator.infinityforreddit.apis.RedgifsAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchGfycatOrRedgifsVideoLinks {
    private static final String TAG = "GfycatRedgifsFetch";

    public interface FetchGfycatOrRedgifsVideoLinksListener {
        void success(String webm, String mp4);

        void failed(int errorCode);
    }

    public interface FetchRedgifsV2VideoLinksListener {
        void success(String url, String mp4, Boolean useFallback);

        void failed(int errorCode);
    }

    public static void fetchGfycatVideoLinks(Executor executor, Handler handler, Retrofit gfycatRetrofit,
                                             String gfycatId,
                                             FetchGfycatOrRedgifsVideoLinksListener fetchGfycatOrRedgifsVideoLinksListener) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Fetching Gfycat video links for id=" + gfycatId);
                Response<String> response = gfycatRetrofit.create(GfycatAPI.class).getGfycatData(gfycatId).execute();
                if (response.isSuccessful()) {
                    Log.d(TAG, "Gfycat response successful for id=" + gfycatId);
                    parseGfycatVideoLinks(handler, response.body(), fetchGfycatOrRedgifsVideoLinksListener);
                } else {
                    Log.w(TAG, "Gfycat response failed for id=" + gfycatId + ", code=" + response.code());
                    handler.post(() -> fetchGfycatOrRedgifsVideoLinksListener.failed(response.code()));
                }
            } catch (IOException e) {
                Log.e(TAG, "Gfycat request failed for id=" + gfycatId, e);
                handler.post(() -> fetchGfycatOrRedgifsVideoLinksListener.failed(-1));
            }
        });
    }

    public static void fetchRedgifsVideoLinks(Context context, Executor executor, Handler handler, Retrofit redgifsRetrofit,
                                              SharedPreferences currentAccountSharedPreferences,
                                              String gfycatId,
                                              FetchGfycatOrRedgifsVideoLinksListener fetchGfycatOrRedgifsVideoLinksListener) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Fetching Redgifs video links for id=" + gfycatId);
                Response<String> response = redgifsRetrofit.create(RedgifsAPI.class).getRedgifsData(APIUtils.getRedgifsOAuthHeader(currentAccountSharedPreferences.getString(SharedPreferencesUtils.REDGIFS_ACCESS_TOKEN, "")),
                        gfycatId).execute();
                if (response.isSuccessful()) {
                    Log.d(TAG, "Redgifs response successful for id=" + gfycatId);
                    parseRedgifsVideoLinks(handler, response.body(), fetchGfycatOrRedgifsVideoLinksListener);
                } else {
                    Log.w(TAG, "Redgifs response failed for id=" + gfycatId + ", code=" + response.code());
                    handler.post(() -> fetchGfycatOrRedgifsVideoLinksListener.failed(response.code()));
                }
            } catch (IOException e) {
                Log.e(TAG, "Redgifs request failed for id=" + gfycatId, e);
                handler.post(() -> fetchGfycatOrRedgifsVideoLinksListener.failed(-1));
            }
        });
    }

    public static void fetchRedgifsV2VideoLinks(Context context, Executor executor, Handler handler, Retrofit redgifsRetrofit,
                                                SharedPreferences defaultSharedPreferences,
                                                String rgId, FetchRedgifsV2VideoLinksListener fetchRedgifsV2VideoLinksListener) {
        executor.execute(() -> {
            try {
                String rgToken = defaultSharedPreferences.getString(SharedPreferencesUtils.REDGIFS_ACCESS_TOKEN, "");
                RedgifsAPI rgRetrofit = redgifsRetrofit.create(RedgifsAPI.class);
                Response<String> dataResponse = rgRetrofit.getRedgifsData(APIUtils.getRedgifsOAuthHeader(rgToken), rgId).execute();

                if (dataResponse.isSuccessful()) {
                    String mp4 = new JSONObject(dataResponse.body()).getJSONObject(JSONUtils.GIF_KEY).getJSONObject(JSONUtils.URLS_KEY)
                            .getString(JSONUtils.HD_KEY);

                    String url = String.format("https://api.redgifs.com/v2/gifs/%s/hd.m3u8", rgId);

                    Response<Void> hlsResponse = rgRetrofit.testHLS(APIUtils.getRedgifsOAuthHeader(rgToken), rgId).execute();
                    if(!hlsResponse.isSuccessful()){
                        handler.post(() -> fetchRedgifsV2VideoLinksListener.success(mp4, mp4,false));
                    }else{
                        handler.post(() -> fetchRedgifsV2VideoLinksListener.success(url, mp4,false));
                    }
                }else{
                    handler.post(() -> fetchRedgifsV2VideoLinksListener.success("", "", true));
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
                handler.post(() -> fetchRedgifsV2VideoLinksListener.failed(-1));
            }
        });
    }

    public static void fetchGfycatOrRedgifsVideoLinksInRecyclerViewAdapter(Executor executor, Handler handler,
                                                                           Call<String> gfycatCall,
                                                                           boolean isGfycatVideo,
                                                                           boolean automaticallyTryRedgifs,
                                                                           FetchGfycatOrRedgifsVideoLinksListener fetchGfycatOrRedgifsVideoLinksListener) {
        executor.execute(() -> {
            try {
                Response<String> response = gfycatCall.execute();
                if (response.isSuccessful()) {
                    Log.d(TAG, (isGfycatVideo ? "Gfycat" : "Redgifs") + " response successful in adapter flow");
                    if (isGfycatVideo) {
                        parseGfycatVideoLinks(handler, response.body(), fetchGfycatOrRedgifsVideoLinksListener);
                    } else {
                        parseRedgifsVideoLinks(handler, response.body(), fetchGfycatOrRedgifsVideoLinksListener);
                    }
                } else {
                    if (response.code() == 404 && isGfycatVideo && automaticallyTryRedgifs) {
                        Log.i(TAG, "Gfycat returned 404, retrying via Redgifs");
                        fetchGfycatOrRedgifsVideoLinksInRecyclerViewAdapter(executor, handler, gfycatCall.clone(),
                                false, false, fetchGfycatOrRedgifsVideoLinksListener);
                    } else {
                        Log.w(TAG, "Adapter flow request failed, code=" + response.code() + ", isGfycatVideo=" + isGfycatVideo);
                        handler.post(() -> fetchGfycatOrRedgifsVideoLinksListener.failed(response.code()));
                    }
                }
            } catch (IOException e) {
                Log.e(TAG, "Adapter flow request failed with exception", e);
                handler.post(() -> fetchGfycatOrRedgifsVideoLinksListener.failed(-1));
            }
        });
    }

    private static void parseGfycatVideoLinks(Handler handler, String response,
                                              FetchGfycatOrRedgifsVideoLinksListener fetchGfycatOrRedgifsVideoLinksListener) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            String mp4 = jsonObject.getJSONObject(JSONUtils.GFY_ITEM_KEY).has(JSONUtils.MP4_URL_KEY) ?
                    jsonObject.getJSONObject(JSONUtils.GFY_ITEM_KEY).getString(JSONUtils.MP4_URL_KEY)
                    : jsonObject.getJSONObject(JSONUtils.GFY_ITEM_KEY)
                    .getJSONObject(JSONUtils.CONTENT_URLS_KEY)
                    .getJSONObject(JSONUtils.MP4_KEY)
                    .getString(JSONUtils.URL_KEY);
            String webm;
            if (jsonObject.getJSONObject(JSONUtils.GFY_ITEM_KEY).has(JSONUtils.WEBM_URL_KEY)) {
                webm = jsonObject.getJSONObject(JSONUtils.GFY_ITEM_KEY).getString(JSONUtils.WEBM_URL_KEY);
            } else if (jsonObject.getJSONObject(JSONUtils.GFY_ITEM_KEY).getJSONObject(JSONUtils.CONTENT_URLS_KEY).has(JSONUtils.WEBM_KEY)) {
                webm = jsonObject.getJSONObject(JSONUtils.GFY_ITEM_KEY)
                        .getJSONObject(JSONUtils.CONTENT_URLS_KEY)
                        .getJSONObject(JSONUtils.WEBM_KEY)
                        .getString(JSONUtils.URL_KEY);
            } else {
                webm = mp4;
            }
            handler.post(() -> fetchGfycatOrRedgifsVideoLinksListener.success(webm, mp4));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse Gfycat response", e);
            handler.post(() -> fetchGfycatOrRedgifsVideoLinksListener.failed(-1));
        }
    }

    private static void parseRedgifsVideoLinks(Handler handler, String response,
                                               FetchGfycatOrRedgifsVideoLinksListener fetchGfycatOrRedgifsVideoLinksListener) {
        try {
            String mp4 = new JSONObject(response).getJSONObject(JSONUtils.GIF_KEY).getJSONObject(JSONUtils.URLS_KEY)
                    .getString(JSONUtils.HD_KEY);
            handler.post(() -> fetchGfycatOrRedgifsVideoLinksListener.success(mp4, mp4));
        } catch (JSONException e) {
            Log.e(TAG, "Failed to parse Redgifs response", e);
            handler.post(() -> fetchGfycatOrRedgifsVideoLinksListener.failed(-1));
        }
    }
}

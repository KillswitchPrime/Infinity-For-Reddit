package ml.docilealligator.infinityforreddit.subreddit;

import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import ml.docilealligator.infinityforreddit.apis.GqlRequestBody;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class ParseSubredditData {
    private static final String TAG = "ParseSubredditData";
    public static void parseSubredditData(String response, ParseSubredditDataListener parseSubredditDataListener) {
        new ParseSubredditDataAsyncTask(response, parseSubredditDataListener).execute();
    }

    public static void parseSubredditDataOld(String response, ParseSubredditDataListener parseSubredditDataListener) {
        new ParseSubredditDataAsyncTask(response, parseSubredditDataListener).execute();
    }

    public static void parseSubredditListingData(String response, boolean nsfw, ParseSubredditListingDataListener parseSubredditListingDataListener) {
        new ParseSubredditListingDataAsyncTask(response, nsfw, parseSubredditListingDataListener).execute();
    }
    @Nullable
    private static SubredditData parseSubredditData(JSONObject subredditDataJsonObject, boolean nsfw) throws JSONException {
        boolean isNSFW = subredditDataJsonObject.getBoolean("isNsfw");
        if (!nsfw && isNSFW) {
            return null;
        }
        String id = subredditDataJsonObject.getString(JSONUtils.NAME_KEY);
        String subredditFullName = subredditDataJsonObject.getString(JSONUtils.DISPLAY_NAME_KEY);
        String description = subredditDataJsonObject.getString(JSONUtils.PUBLIC_DESCRIPTION_KEY).trim();
        String sidebarDescription = Utils.modifyMarkdown(subredditDataJsonObject.getString(JSONUtils.DESCRIPTION_KEY).trim());
        long createdUTC = subredditDataJsonObject.getLong(JSONUtils.CREATED_UTC_KEY) * 1000;
        String suggestedCommentSort = subredditDataJsonObject.isNull(JSONUtils.SUGGESTED_COMMENT_SORT_KEY)
                ? null : subredditDataJsonObject.optString(JSONUtils.SUGGESTED_COMMENT_SORT_KEY, null);

        String bannerImageUrl;
        if (subredditDataJsonObject.isNull(JSONUtils.BANNER_BACKGROUND_IMAGE_KEY)) {
            bannerImageUrl = "";
        } else {
            bannerImageUrl = subredditDataJsonObject.getString(JSONUtils.BANNER_BACKGROUND_IMAGE_KEY);
        }
        if (bannerImageUrl.equals("") && !subredditDataJsonObject.isNull(JSONUtils.BANNER_IMG_KEY)) {
            bannerImageUrl = subredditDataJsonObject.getString(JSONUtils.BANNER_IMG_KEY);
        }

        String iconUrl;
        if (subredditDataJsonObject.getJSONObject("styles").isNull("icon")) {
            iconUrl = "";
        } else {
            iconUrl = subredditDataJsonObject.getJSONObject("styles").getString("icon");
        }
        if (iconUrl.equals("") && !subredditDataJsonObject.isNull(JSONUtils.ICON_IMG_KEY)) {
            iconUrl = subredditDataJsonObject.getString(JSONUtils.ICON_IMG_KEY);
        }

        int nSubscribers = 0;
        if (!subredditDataJsonObject.isNull("subscribersCount")) {
            nSubscribers = subredditDataJsonObject.getInt("subscribersCount");
        }

        return new SubredditData(id, subredditFullName, iconUrl, bannerImageUrl, description,
                sidebarDescription, nSubscribers, createdUTC, suggestedCommentSort, isNSFW);
    }

    @Nullable
    private static SubredditData parseSubredditDataSingle(JSONObject subredditDataJsonObject, boolean nsfw) throws JSONException {
        boolean isNSFW = subredditDataJsonObject.getBoolean("isNsfw");
        if (!nsfw && isNSFW) {
            return null;
        }
        String id = subredditDataJsonObject.getString("id");
        String subredditFullName = subredditDataJsonObject.getString("name");
        String description = "";
        if(!subredditDataJsonObject.isNull("publicDescriptionText")){
            description = subredditDataJsonObject.getString("publicDescriptionText").trim();
        }

        String sidebarDescription = Utils.modifyMarkdown(subredditDataJsonObject.getJSONObject(JSONUtils.DESCRIPTION_KEY).getString("markdown").trim());
        long createdUTC = GqlRequestBody.getUnixTime(subredditDataJsonObject.getString("createdAt"));

        String bannerImageUrl;
        if (subredditDataJsonObject.getJSONObject("styles").isNull("bannerBackgroundImage")) {
            bannerImageUrl = "";
        } else {
            bannerImageUrl = subredditDataJsonObject.getJSONObject("styles").getString("bannerBackgroundImage");
        }
        if (bannerImageUrl.equals("") && !subredditDataJsonObject.getJSONObject("styles").isNull("mobileBannerImage")) {
            bannerImageUrl = subredditDataJsonObject.getJSONObject("styles").getString("mobileBannerImage");
        }

        String iconUrl;
        if (subredditDataJsonObject.getJSONObject("styles").isNull("icon")) {
            iconUrl = "";
        } else {
            iconUrl = subredditDataJsonObject.getJSONObject("styles").getString("icon");
        }
        if (iconUrl.equals("") && !subredditDataJsonObject.getJSONObject("styles").isNull("legacyIcon")) {
            iconUrl = subredditDataJsonObject.getJSONObject("styles").getJSONObject("legacyIcon").getString("url");
        }

        int nSubscribers = 0;
        if (!subredditDataJsonObject.isNull("subscribersCount")) {
            nSubscribers = subredditDataJsonObject.getInt("subscribersCount");
        }

        return new SubredditData(id, subredditFullName, iconUrl, bannerImageUrl, description,
                sidebarDescription, nSubscribers, createdUTC, null, isNSFW);
    }

    interface ParseSubredditDataListener {
        void onParseSubredditDataSuccess(SubredditData subredditData, int nCurrentOnlineSubscribers);

        void onParseSubredditDataFail();
    }

    public interface ParseSubredditListingDataListener {
        void onParseSubredditListingDataSuccess(ArrayList<SubredditData> subredditData, String after);

        void onParseSubredditListingDataFail();
    }

    private static class ParseSubredditDataAsyncTask extends AsyncTask<Void, Void, Void> {
        private JSONObject jsonResponse;
        private boolean parseFailed;
        private ParseSubredditDataListener parseSubredditDataListener;
        private SubredditData subredditData;
        private int mNCurrentOnlineSubscribers;

        ParseSubredditDataAsyncTask(String response, ParseSubredditDataListener parseSubredditDataListener) {
            this.parseSubredditDataListener = parseSubredditDataListener;
            try {
                jsonResponse = new JSONObject(response);
                parseFailed = false;
            } catch (JSONException e) {
                e.printStackTrace();
                parseFailed = true;
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            if (parseFailed) return null;
            try {
                JSONObject data = jsonResponse.getJSONObject(JSONUtils.DATA_KEY);
                mNCurrentOnlineSubscribers = data.optInt(JSONUtils.ACTIVE_USER_COUNT_KEY, 0);
                subredditData = parseSubredditData(data, true);
                Log.d(TAG, "parseSubredditData success, subredditData=" + (subredditData != null ? subredditData.getName() : "null"));
            } catch (JSONException e) {
                parseFailed = true;
                Log.e(TAG, "parseSubredditData JSONException", e);
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (!parseFailed && subredditData != null) {
                parseSubredditDataListener.onParseSubredditDataSuccess(subredditData, mNCurrentOnlineSubscribers);
            } else {
                parseSubredditDataListener.onParseSubredditDataFail();
            }
        }
    }

    private static class ParseSubredditListingDataAsyncTask extends AsyncTask<Void, Void, Void> {
        private JSONObject jsonResponse;
        private boolean nsfw;
        private boolean parseFailed;
        private ParseSubredditListingDataListener parseSubredditListingDataListener;
        private ArrayList<SubredditData> subredditListingData;
        private String after;

        ParseSubredditListingDataAsyncTask(String response, boolean nsfw, ParseSubredditListingDataListener parseSubredditListingDataListener) {
            this.parseSubredditListingDataListener = parseSubredditListingDataListener;
            try {
                jsonResponse = new JSONObject(response);
                this.nsfw = nsfw;
                parseFailed = false;
                subredditListingData = new ArrayList<>();
            } catch (JSONException e) {
                e.printStackTrace();
                parseFailed = true;
            }
        }

        @Override
        protected Void doInBackground(Void... voids) {
            try {
                if (!parseFailed) {
                    JSONArray children;
                    boolean isGeneralSearch = jsonResponse.getJSONObject(JSONUtils.DATA_KEY).getJSONObject("search").has("general");
                    if (isGeneralSearch){
                        children = jsonResponse.getJSONObject(JSONUtils.DATA_KEY).getJSONObject("search").getJSONObject("general").getJSONObject("communities").getJSONArray("edges");
                        JSONObject pageInfo = jsonResponse.getJSONObject(JSONUtils.DATA_KEY).getJSONObject("search").getJSONObject("general").getJSONObject("communities").getJSONObject("pageInfo");
                        if (pageInfo.getBoolean("hasNextPage")){
                            after = pageInfo.getString("endCursor");
                        }else{
                            after = null;
                        }
                    }else{
                        children = jsonResponse.getJSONObject(JSONUtils.DATA_KEY).getJSONObject("search").getJSONObject("typeaheadByType").getJSONArray("subreddits");
                        after = null;
                    }

                    for (int i = 0; i < children.length(); i++) {
                        JSONObject data;
                        if(isGeneralSearch){
                            data = children.getJSONObject(i).getJSONObject("node");
                        }else{
                            data = children.getJSONObject(i);
                        }
                        SubredditData subredditData = parseSubredditData(data, nsfw);
                        if (subredditData != null) {
                            subredditListingData.add(subredditData);
                        }
                    }
                }
            } catch (JSONException e) {
                e.printStackTrace();
                parseFailed = true;
                parseSubredditListingDataListener.onParseSubredditListingDataFail();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if (!parseFailed) {
                parseSubredditListingDataListener.onParseSubredditListingDataSuccess(subredditListingData, after);
            } else {
                parseSubredditListingDataListener.onParseSubredditListingDataFail();
            }
        }
    }
}

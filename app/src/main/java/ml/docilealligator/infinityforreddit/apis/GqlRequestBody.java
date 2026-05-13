package ml.docilealligator.infinityforreddit.apis;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.ApiStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import ml.docilealligator.infinityforreddit.utils.APIUtils;
import okhttp3.RequestBody;

public class GqlRequestBody {

    public static RequestBody subredditAutocompleteBody(String query, boolean nsfw){

        JSONObject data = new JSONObject();
        try {
            data.put("operationName", "SearchTypeaheadByType");
            JSONObject variables = new JSONObject();
            variables.put("query", query);

            JSONArray filters = new JSONArray();
            JSONObject nsfwFilter = new JSONObject();

            String nsfwVal = nsfw ? "1" : "0";
            nsfwFilter.put("key", "nsfw");
            nsfwFilter.put("value", nsfwVal);
            filters.put(nsfwFilter);

            variables.put("filters", filters);

            variables.put("productSurface", "android");
            variables.put("limit", 5);
            variables.put("includeUsers", false);
            variables.put("includeEligibleMoment", false);

            data.put("variables", variables);
            data.put("extensions", createExtensionsObject("fe372c97004a67146b1af1310e411e5573252159b35515db24528920b5c9a5bf"));

        }catch (JSONException e){

        }
        return RequestBody.create(data.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));
    }

    public static RequestBody subredditSearchBody(String query, boolean nsfw, String afterCursor){

        JSONObject data = new JSONObject();
        try {
            data.put("operationName", "SearchCommunities");
            JSONObject variables = new JSONObject();
            variables.put("query", query);

            JSONArray filters = new JSONArray();
            JSONObject nsfwFilter = new JSONObject();

            String nsfwVal = nsfw ? "1" : "0";
            nsfwFilter.put("key", "nsfw");
            nsfwFilter.put("value", nsfwVal);
            filters.put(nsfwFilter);

            variables.put("filters", filters);

            variables.put("productSurface", "android");
            variables.put("afterCursor", afterCursor);

            JSONObject searchInput = new JSONObject();
            searchInput.put("originPageType", "home");
            searchInput.put("structureType", "search");
            searchInput.put("pane", "posts");
            searchInput.put("isClientPrefNsfw", nsfw);

            variables.put("searchInput", searchInput);

            variables.put("includeDynamicModifiers", false);

            data.put("variables", variables);
            data.put("extensions", createExtensionsObject("effbf83b871ba83e7eecca32efab3cbccc74cce2e4fd73595707c223a03b3ae1"));

        }catch (JSONException e){

        }
        return RequestBody.create(data.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));
    }

    public static RequestBody subredditDataBody(String subredditName){

        JSONObject data = new JSONObject();
        try {
            data.put("operationName", "SubredditInfoByName");
            JSONObject variables = new JSONObject();
            variables.put("subredditName", subredditName);
            variables.put("loggedOutIsOptedIn", false);
            variables.put("filterGated", true);
            variables.put("includeRecapFields", false);

            data.put("variables", variables);
            data.put("extensions", createExtensionsObject("7e47c4c17a0fd51f306f02d8880b9d8797985affbe874bb2215ceb386c880e53"));

        }catch (JSONException e){

        }
        return RequestBody.create(data.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));
    }

    public static RequestBody subscribeBody(String subredditId, String action){

        JSONObject data = new JSONObject();
        try {
            data.put("operationName", "UpdateSubredditSubscriptions");
            JSONObject variables = new JSONObject();

            JSONObject input = new JSONObject();
            JSONArray inputs = new JSONArray();
            JSONObject subInput = new JSONObject();
            subInput.put("subredditId", subredditId);
            subInput.put("subscribeState", action);
            inputs.put(subInput);
            input.put("inputs", inputs);

            variables.put("input", input);


            data.put("variables", variables);
            data.put("extensions", createExtensionsObject("5de46969ea6ea4ba6ec17ad2f7174498982453f96b1b9cc1172b63c18f2a7f2f"));

        }catch (JSONException e){

        }
        return RequestBody.create(data.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));
    }

    public static RequestBody updatePostVoteStateBody(String postId, String state){

        JSONObject data = new JSONObject();
        try {
            data.put("operationName", "UpdatePostVoteState");
            JSONObject variables = new JSONObject();

            JSONObject input = new JSONObject();
            input.put("postId", postId);

            String voteState;
            switch(state){
                case "0":
                    voteState = APIUtils.VOTESTATE_NONE;
                    break;
                case "1":
                    voteState = APIUtils.VOTESTATE_UP;
                    break;
                case "-1":
                    voteState = APIUtils.VOTESTATE_DOWN;
                    break;
                default:
                    voteState = APIUtils.VOTESTATE_NONE;
                    break;
            }

            input.put("voteState", voteState);

            variables.put("input", input);

            data.put("variables", variables);
            data.put("extensions", createExtensionsObject("2523ed203f983488aab97d84f12b46910008244b79a61073b419e33ff98cb7e5"));

        }catch (JSONException e){

        }
        return RequestBody.create(data.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));
    }
    public static RequestBody updateCommentVoteStateBody(String postId, String state){

        JSONObject data = new JSONObject();
        try {
            data.put("operationName", "UpdateCommentVoteState");
            JSONObject variables = new JSONObject();

            JSONObject input = new JSONObject();
            input.put("commentId", postId);

            String voteState;
            switch(state){
                case "0":
                    voteState = APIUtils.VOTESTATE_NONE;
                    break;
                case "1":
                    voteState = APIUtils.VOTESTATE_UP;
                    break;
                case "-1":
                    voteState = APIUtils.VOTESTATE_DOWN;
                    break;
                default:
                    voteState = APIUtils.VOTESTATE_NONE;
                    break;
            }

            input.put("voteState", voteState);

            variables.put("input", input);

            data.put("variables", variables);
            data.put("extensions", createExtensionsObject("a353fb29741128e9346380c4706af0bf59fc5767608ea6848039c57ae0b08fec"));

        }catch (JSONException e){

        }
        return RequestBody.create(data.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));
    }

    private static JSONObject createExtensionsObject(String sha256Hash){
        JSONObject data = new JSONObject();
        JSONObject persistedQuery = new JSONObject();
        try{
            persistedQuery.put("version", 1);
            persistedQuery.put("sha256Hash", sha256Hash);
            data.put("persistedQuery", persistedQuery);
        }catch (JSONException e){

        }
        return data;
    }

    public static long getUnixTime(String timestamp) {
        String pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ";
        SimpleDateFormat formatter = new SimpleDateFormat(pattern);
        try {
            return formatter.parse(timestamp).getTime();
        } catch (ParseException e) {
            return new Date().getTime();
        }
    }
}

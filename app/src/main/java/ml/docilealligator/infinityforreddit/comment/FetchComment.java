package ml.docilealligator.infinityforreddit.comment;

import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.SortType;
import ml.docilealligator.infinityforreddit.apis.GqlAPI;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;

public class FetchComment {
    private static final String TAG = "FetchComment";

    public static void fetchComments(Executor executor, Handler handler, Retrofit retrofit,
                                     @Nullable String accessToken, String article,
                                     String commentId, SortType.Type sortType, String contextNumber, boolean expandChildren,
                                     Locale locale, FetchCommentListener fetchCommentListener) {
        Log.d(TAG, "Fetching comments for article=" + article + ", commentId=" + commentId + ", sort=" + sortType);
        RedditAPI api = retrofit.create(RedditAPI.class);
        GqlAPI gqlAPI = retrofit.create(GqlAPI.class);

        Call<String> comments;
        if (accessToken == null) {
            if (commentId == null) {
                comments = api.getPostAndCommentsById(article, sortType);
            } else {
                comments = api.getPostAndCommentsSingleThreadById(article, commentId, sortType, contextNumber);
            }
        } else {
            if (commentId == null) {
                String fullArticle = "t3_" + article;
                RequestBody payload = createCommentVariables(fullArticle, sortType, null);
                comments = gqlAPI.getPostComments(APIUtils.getOAuthHeader(accessToken), payload);
            } else {
                comments = api.getPostAndCommentsSingleThreadByIdOauth(article, commentId, sortType, contextNumber,
                        APIUtils.getOAuthHeader(accessToken));
            }
        }

        comments.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "Comments fetch succeeded for article=" + article + ", parsing response");
                    ParseComment.parseComment(executor, handler, response.body(),
                            expandChildren, new ParseComment.ParseCommentListener() {
                                @Override
                                public void onParseCommentSuccess(ArrayList<Comment> topLevelComments,
                                                                  ArrayList<Comment> expandedComments,
                                                                  String parentId, ArrayList<String> moreChildrenIds) {
                                    Log.d(TAG, "Comments parsed successfully for article=" + article + ", expandedCount=" + expandedComments.size());
                                    fetchCommentListener.onFetchCommentSuccess(expandedComments, parentId,
                                            moreChildrenIds);
                                }

                                @Override
                                public void onParseCommentFailed() {
                                    Log.e(TAG, "Comments parse failed for article=" + article);
                                    fetchCommentListener.onFetchCommentFailed();
                                }
                            });
                } else {
                    Log.w(TAG, "Comments fetch failed for article=" + article + ", code=" + response.code());
                    fetchCommentListener.onFetchCommentFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                Log.e(TAG, "Comments fetch request failed for article=" + article, t);
                fetchCommentListener.onFetchCommentFailed();
            }
        });
    }

    public static void fetchMoreComment(Executor executor, Handler handler, Retrofit retrofit,
                                        @Nullable String accessToken,
                                        ArrayList<String> allChildren,
                                        boolean expandChildren, String postFullName,
                                        SortType.Type sortType, String authorName,
                                        FetchMoreCommentListener fetchMoreCommentListener) {
        if (allChildren == null) {
            Log.w(TAG, "More-comments fetch skipped because children list is null");
            return;
        }

        String childrenIds = String.join(",", allChildren);

        if (childrenIds.isEmpty()) {
            Log.w(TAG, "More-comments fetch skipped because children list is empty");
            return;
        }

        Log.d(TAG, "Fetching more comments for post=" + postFullName + ", childCount=" + allChildren.size());

        RedditAPI api = retrofit.create(RedditAPI.class);
        GqlAPI gqlAPI = retrofit.create(GqlAPI.class);

        Call<String> moreComments;
        if (accessToken == null) {
            moreComments = api.moreChildren(postFullName, childrenIds, sortType);
        } else {
            //moreComments = api.moreChildrenOauth(postFullName, childrenIds,
            //        sortType, APIUtils.getOAuthHeader(accessToken));
            RequestBody payload = createCommentVariables(postFullName, sortType, allChildren.get(0));
            moreComments = gqlAPI.getPostComments(APIUtils.getOAuthHeader(accessToken), payload);
        }

        moreComments.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull Response<String> response) {
                if (response.isSuccessful()) {
                    Log.d(TAG, "More-comments fetch succeeded for post=" + postFullName + ", parsing response");
                    ParseComment.parseMoreComment(executor, handler, response.body(),
                            expandChildren, new ParseComment.ParseCommentListener() {
                                @Override
                                public void onParseCommentSuccess(ArrayList<Comment> topLevelComments,
                                                                  ArrayList<Comment> expandedComments,
                                                                  String parentId, ArrayList<String> moreChildrenIds) {
                                    Log.d(TAG, "More-comments parse succeeded for post=" + postFullName + ", topLevel=" + topLevelComments.size());
                                    fetchMoreCommentListener.onFetchMoreCommentSuccess(
                                            topLevelComments,expandedComments, moreChildrenIds);
                                }

                                @Override
                                public void onParseCommentFailed() {
                                    Log.e(TAG, "More-comments parse failed for post=" + postFullName);
                                    fetchMoreCommentListener.onFetchMoreCommentFailed();
                                }
                            });
                } else {
                    Log.w(TAG, "More-comments fetch failed for post=" + postFullName + ", code=" + response.code());
                    fetchMoreCommentListener.onFetchMoreCommentFailed();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                Log.e(TAG, "More-comments request failed for post=" + postFullName, t);
                fetchMoreCommentListener.onFetchMoreCommentFailed();
            }
        });
    }

    public interface FetchCommentListener {
        void onFetchCommentSuccess(ArrayList<Comment> expandedComments, String parentId, ArrayList<String> children);

        void onFetchCommentFailed();
    }

    public interface FetchMoreCommentListener {
        void onFetchMoreCommentSuccess(ArrayList<Comment> topLevelComments,
                                       ArrayList<Comment> expandedComments,
                                       ArrayList<String> moreChildrenIds);

        void onFetchMoreCommentFailed();
    }

    private static RequestBody createCommentVariables(String id, SortType.Type sortType, String afterKey) {
        /*
        {
            "operationName": "PostComments",
            "variables": {
                "id": "t3_1d8zdwf",
                "sortType": "CONTROVERSIAL",
                "maxDepth": 10,
                "count": 8,
                "includeAwards": true,
                "includeTranslation": false,
                "includeCurrentUserAwards": false,
                "preTranslate": false,
                "preTranslationTargetLanguage": "en",
                "includeCommentsHtmlField": true,
                "truncate": 0,
                "includeIsGildable": true,
                "includeAdEligibility": false
            },
            "extensions": {
                "persistedQuery": {
                    "version": 1,
                    "sha256Hash": "dc1b502b203313fb568ab4b63b88c218eacfce4e3d1298a6facd00e9b3226d9c"
                }
            }
        }
         */
        JSONObject payload = new JSONObject();
        JSONObject variables = new JSONObject();

        try {
            variables.put("id", id);
            variables.put("sortType", sortType.value.toUpperCase(Locale.ROOT));
            variables.put("maxDepth", 10);
            variables.put("count", 200);
            variables.put("includeAwards", true);
            variables.put("includeTranslation", false);
            variables.put("includeCurrentUserAwards", false);
            variables.put("preTranslate", false);
            variables.put("preTranslationTargetLanguage", "en");
            variables.put("includeCommentsHtmlField", true);
            variables.put("truncate", 0);
            variables.put("includeIsGildable", true);
            variables.put("includeAdEligibility", false);

            if (afterKey != null) {
                variables.put("after", afterKey);
            }

            payload.put("operationName", "PostComments");
            payload.put("variables", variables);

            JSONObject extensions = createExtensionsObject("dc1b502b203313fb568ab4b63b88c218eacfce4e3d1298a6facd00e9b3226d9c");

            payload.put("extensions", extensions);

        } catch (JSONException e) {
            throw new RuntimeException(e);
        }

        return RequestBody.create(payload.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));
    }

    private static JSONObject createExtensionsObject(String sha256Hash) {
        JSONObject data = new JSONObject();
        JSONObject persistedQuery = new JSONObject();
        try {
            persistedQuery.put("version", 1);
            persistedQuery.put("sha256Hash", sha256Hash);
            data.put("persistedQuery", persistedQuery);
        } catch (JSONException e) {

        }
        return data;
    }
}

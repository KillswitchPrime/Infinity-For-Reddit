package ml.docilealligator.infinityforreddit;

import android.content.Context;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

import ml.docilealligator.infinityforreddit.apis.GqlAPI;
import ml.docilealligator.infinityforreddit.apis.GqlRequestBody;
import ml.docilealligator.infinityforreddit.apis.RedditAPI;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;

/**
 * Created by alex on 3/14/18.
 */

public class VoteThing {

    public static boolean isPost(String id){
        if (id.startsWith("t3_")){
            return true;
        }
        return false;
    }

    public static void voteThing(Context context, final Retrofit retrofit, String accessToken,
                                 final VoteThingListener voteThingListener, final String fullName,
                                 final String point, final int position) {

        GqlAPI api = retrofit.create(GqlAPI.class);
        Call<String> voteThingCall;

        if (isPost(fullName)) {
            RequestBody body = GqlRequestBody.updatePostVoteStateBody(fullName, point);
            voteThingCall = api.updatePostVoteState(APIUtils.getOAuthHeader(accessToken), body);
        }else{
            RequestBody body = GqlRequestBody.updateCommentVoteStateBody(fullName, point);
            voteThingCall = api.updateCommentVoteState(APIUtils.getOAuthHeader(accessToken), body);
        }

        voteThingCall.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull retrofit2.Response<String> response) {
                if (response.isSuccessful()) {
                    voteThingListener.onVoteThingSuccess(position);
                } else {
                    voteThingListener.onVoteThingFail(position);
                    Toast.makeText(context, "Code " + response.code() + " Body: " + response.body(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                voteThingListener.onVoteThingFail(position);
                Toast.makeText(context, "Network error " + "Body: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    public static void voteThing(Context context, final Retrofit retrofit, String accessToken,
                                 final VoteThingWithoutPositionListener voteThingWithoutPositionListener,
                                 final String fullName, final String point) {
        GqlAPI api = retrofit.create(GqlAPI.class);
        Call<String> voteThingCall;

        if (isPost(fullName)) {
            RequestBody body = GqlRequestBody.updatePostVoteStateBody(fullName, point);
            voteThingCall = api.updatePostVoteState(APIUtils.getOAuthHeader(accessToken), body);
        }else{
            RequestBody body = GqlRequestBody.updateCommentVoteStateBody(fullName, point);
            voteThingCall = api.updateCommentVoteState(APIUtils.getOAuthHeader(accessToken), body);
        }
        voteThingCall.enqueue(new Callback<String>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull retrofit2.Response<String> response) {
                if (response.isSuccessful()) {
                    voteThingWithoutPositionListener.onVoteThingSuccess();
                } else {
                    voteThingWithoutPositionListener.onVoteThingFail();
                    Toast.makeText(context, "Code " + response.code() + " Body: " + response.body(), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                voteThingWithoutPositionListener.onVoteThingFail();
                Toast.makeText(context, "Network error " + "Body: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    public interface VoteThingListener {
        void onVoteThingSuccess(int position);

        void onVoteThingFail(int position);
    }

    public interface VoteThingWithoutPositionListener {
        void onVoteThingSuccess();

        void onVoteThingFail();
    }
}

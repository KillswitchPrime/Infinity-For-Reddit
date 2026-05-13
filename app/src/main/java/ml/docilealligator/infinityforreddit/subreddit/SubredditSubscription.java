package ml.docilealligator.infinityforreddit.subreddit;

import android.os.Handler;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.RedditDataRoomDatabase;
import ml.docilealligator.infinityforreddit.account.Account;
import ml.docilealligator.infinityforreddit.apis.GqlAPI;
import ml.docilealligator.infinityforreddit.apis.GqlRequestBody;
import ml.docilealligator.infinityforreddit.subscribedsubreddit.SubscribedSubredditData;
import ml.docilealligator.infinityforreddit.utils.APIUtils;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;

public class SubredditSubscription {
    public static void subscribeToSubreddit(Executor executor, Handler handler, Retrofit oauthRetrofit,
                                            Retrofit retrofit, String accessToken, String subredditName,String subredditId,
                                            String accountName, RedditDataRoomDatabase redditDataRoomDatabase,
                                            SubredditSubscriptionListener subredditSubscriptionListener) {
        subredditSubscription(executor, handler, oauthRetrofit, retrofit, accessToken, subredditName, subredditId,
                accountName, APIUtils.ACTION_SUB, redditDataRoomDatabase, subredditSubscriptionListener);
    }

    public static void anonymousSubscribeToSubreddit(Executor executor, Handler handler, String accessToken, Retrofit retrofit,
                                                     RedditDataRoomDatabase redditDataRoomDatabase,
                                                     String subredditName,
                                                     SubredditSubscriptionListener subredditSubscriptionListener) {
        FetchSubredditData.fetchSubredditData(retrofit, null, subredditName, accessToken, new FetchSubredditData.FetchSubredditDataListener() {
            @Override
            public void onFetchSubredditDataSuccess(SubredditData subredditData, int nCurrentOnlineSubscribers) {
                insertSubscription(executor, handler, redditDataRoomDatabase,
                        subredditData, "-", subredditSubscriptionListener);
            }

            @Override
            public void onFetchSubredditDataFail(boolean isQuarantined) {
                subredditSubscriptionListener.onSubredditSubscriptionFail();
            }
        });
    }

    public static void unsubscribeToSubreddit(Executor executor, Handler handler, Retrofit oauthRetrofit,
                                              String accessToken, String subredditName,String subredditId, String accountName,
                                              RedditDataRoomDatabase redditDataRoomDatabase,
                                              SubredditSubscriptionListener subredditSubscriptionListener) {
        subredditSubscription(executor, handler, oauthRetrofit, null, accessToken, subredditName, subredditId,
                accountName, APIUtils.ACTION_UNSUB, redditDataRoomDatabase, subredditSubscriptionListener);
    }

    public static void anonymousUnsubscribeToSubreddit(Executor executor, Handler handler,
                                                       RedditDataRoomDatabase redditDataRoomDatabase,
                                                       String subredditName,
                                                       SubredditSubscriptionListener subredditSubscriptionListener) {
        removeSubscription(executor, handler, redditDataRoomDatabase, subredditName, "-", subredditSubscriptionListener);
    }

    private static void subredditSubscription(Executor executor, Handler handler, Retrofit oauthRetrofit,
                                              Retrofit retrofit, String accessToken, String subredditName, String subredditId,
                                              String accountName, String action,
                                              RedditDataRoomDatabase redditDataRoomDatabase,
                                              SubredditSubscriptionListener subredditSubscriptionListener) {
        GqlAPI api = oauthRetrofit.create(GqlAPI.class);

        Call<String> subredditSubscriptionCall = api.subredditSubscription(APIUtils.getOAuthHeader(accessToken), GqlRequestBody.subscribeBody(subredditId, action));
        subredditSubscriptionCall.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<String> call, @NonNull retrofit2.Response<String> response) {
                if (response.isSuccessful()) {
                    if (action.equals(APIUtils.ACTION_SUB)) {
                        FetchSubredditData.fetchSubredditData(oauthRetrofit, retrofit, subredditName, accessToken, new FetchSubredditData.FetchSubredditDataListener() {
                            @Override
                            public void onFetchSubredditDataSuccess(SubredditData subredditData, int nCurrentOnlineSubscribers) {
                                insertSubscription(executor, handler, redditDataRoomDatabase,
                                        subredditData, accountName, subredditSubscriptionListener);
                            }

                            @Override
                            public void onFetchSubredditDataFail(boolean isQuarantined) {

                            }
                        });
                    } else {
                        removeSubscription(executor, handler, redditDataRoomDatabase, subredditName,
                                accountName, subredditSubscriptionListener);
                    }
                } else {
                    subredditSubscriptionListener.onSubredditSubscriptionFail();
                }
            }

            @Override
            public void onFailure(@NonNull Call<String> call, @NonNull Throwable t) {
                subredditSubscriptionListener.onSubredditSubscriptionFail();
            }
        });
    }

    public interface SubredditSubscriptionListener {
        void onSubredditSubscriptionSuccess();

        void onSubredditSubscriptionFail();
    }

    private static void insertSubscription(Executor executor, Handler handler,
                                           RedditDataRoomDatabase redditDataRoomDatabase,
                                           SubredditData subredditData, String accountName,
                                           SubredditSubscriptionListener subredditSubscriptionListener) {
        executor.execute(() -> {
            SubscribedSubredditData subscribedSubredditData = new SubscribedSubredditData(subredditData.getId(), subredditData.getName(),
                    subredditData.getIconUrl(), accountName, false);
            if (accountName.equals("-")) {
                if (!redditDataRoomDatabase.accountDao().isAnonymousAccountInserted()) {
                    redditDataRoomDatabase.accountDao().insert(Account.getAnonymousAccount());
                }
            }
            redditDataRoomDatabase.subscribedSubredditDao().insert(subscribedSubredditData);
            handler.post(subredditSubscriptionListener::onSubredditSubscriptionSuccess);
        });
    }

    private static void removeSubscription(Executor executor, Handler handler,
                                           RedditDataRoomDatabase redditDataRoomDatabase,
                                           String subredditName, String accountName,
                                           SubredditSubscriptionListener subredditSubscriptionListener) {
        executor.execute(() -> {
            if (accountName.equals("-")) {
                if (!redditDataRoomDatabase.accountDao().isAnonymousAccountInserted()) {
                    redditDataRoomDatabase.accountDao().insert(Account.getAnonymousAccount());
                }
            }
            redditDataRoomDatabase.subscribedSubredditDao().deleteSubscribedSubreddit(subredditName, accountName);
            handler.post(subredditSubscriptionListener::onSubredditSubscriptionSuccess);
        });
    }
}

package ml.docilealligator.infinityforreddit.activities;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.BitmapImageViewTarget;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.github.piasy.biv.BigImageViewer;
import com.github.piasy.biv.loader.ImageLoader;
import com.github.piasy.biv.loader.glide.GlideImageLoader;
import com.github.piasy.biv.view.BigImageView;
import com.github.piasy.biv.view.GlideImageViewFactory;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.bottomappbar.BottomAppBar;

import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.Executor;

import javax.inject.Inject;
import javax.inject.Named;

import butterknife.BindView;
import butterknife.ButterKnife;
import ml.docilealligator.infinityforreddit.BuildConfig;
import ml.docilealligator.infinityforreddit.CustomFontReceiver;
import ml.docilealligator.infinityforreddit.Infinity;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.SetAsWallpaperCallback;
import ml.docilealligator.infinityforreddit.WallpaperSetter;
import ml.docilealligator.infinityforreddit.asynctasks.SaveBitmapImageToFile;
import ml.docilealligator.infinityforreddit.asynctasks.SaveGIFToFile;
import ml.docilealligator.infinityforreddit.bottomsheetfragments.SetAsWallpaperBottomSheetFragment;
import ml.docilealligator.infinityforreddit.customviews.slidr.Slidr;
import ml.docilealligator.infinityforreddit.customviews.slidr.model.SlidrConfig;
import ml.docilealligator.infinityforreddit.customviews.slidr.model.SlidrPosition;
import ml.docilealligator.infinityforreddit.font.ContentFontFamily;
import ml.docilealligator.infinityforreddit.font.ContentFontStyle;
import ml.docilealligator.infinityforreddit.font.FontFamily;
import ml.docilealligator.infinityforreddit.font.FontStyle;
import ml.docilealligator.infinityforreddit.font.TitleFontFamily;
import ml.docilealligator.infinityforreddit.font.TitleFontStyle;
import ml.docilealligator.infinityforreddit.services.DownloadMediaService;
import ml.docilealligator.infinityforreddit.utils.SharedPreferencesUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class ViewImageOrGifActivity extends AppCompatActivity implements SetAsWallpaperCallback, CustomFontReceiver {
    private static final String TAG = "ViewImageOrGif";

    public static final String EXTRA_IMAGE_URL_KEY = "EIUK";
    public static final String EXTRA_GIF_URL_KEY = "EGUK";
    public static final String EXTRA_FILE_NAME_KEY = "EFNK";
    public static final String EXTRA_SUBREDDIT_OR_USERNAME_KEY = "ESOUK";
    public static final String EXTRA_POST_TITLE_KEY = "EPTK";
    public static final String EXTRA_IS_NSFW = "EIN";
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 0;
    @BindView(R.id.progress_bar_view_image_or_gif_activity)
    ProgressBar mProgressBar;
    @BindView(R.id.image_view_view_image_or_gif_activity)
    BigImageView mImageView;
    @BindView(R.id.gif_image_view_view_image_or_gif_activity)
    PhotoView mGifImageView;
    @BindView(R.id.gif_player_view_view_image_or_gif_activity)
    GifImageView mGifPlayerView;
    @BindView(R.id.video_view_view_image_or_gif_activity)
    android.widget.VideoView mVideoView;
    @BindView(R.id.load_image_error_linear_layout_view_image_or_gif_activity)
    LinearLayout mLoadErrorLinearLayout;
    @BindView(R.id.bottom_navigation_view_image_or_gif_activity)
    BottomAppBar bottomAppBar;
    @BindView(R.id.title_text_view_view_image_or_gif_activity)
    TextView titleTextView;
    @BindView(R.id.download_image_view_view_image_or_gif_activity)
    ImageView downloadImageView;
    @BindView(R.id.share_image_view_view_image_or_gif_activity)
    ImageView shareImageView;
    @BindView(R.id.wallpaper_image_view_view_image_or_gif_activity)
    ImageView wallpaperImageView;
    @Inject
    @Named("default")
    SharedPreferences mSharedPreferences;
    @Inject
    Executor mExecutor;
    private boolean isActionBarHidden = false;
    private boolean isDownloading = false;
    private RequestManager glide;
    private String mImageUrl;
    private String mImageFileName;
    private String mSubredditName;
    private boolean isGif = true;
    private boolean isNsfw;
    private Typeface typeface;
    private Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate: savedInstanceState=" + (savedInstanceState != null));

        ((Infinity) getApplication()).getAppComponent().inject(this);

        getTheme().applyStyle(R.style.Theme_Normal, true);

        getTheme().applyStyle(FontStyle.valueOf(mSharedPreferences
                .getString(SharedPreferencesUtils.FONT_SIZE_KEY, FontStyle.Normal.name())).getResId(), true);

        getTheme().applyStyle(TitleFontStyle.valueOf(mSharedPreferences
                .getString(SharedPreferencesUtils.TITLE_FONT_SIZE_KEY, TitleFontStyle.Normal.name())).getResId(), true);

        getTheme().applyStyle(ContentFontStyle.valueOf(mSharedPreferences
                .getString(SharedPreferencesUtils.CONTENT_FONT_SIZE_KEY, ContentFontStyle.Normal.name())).getResId(), true);

        getTheme().applyStyle(FontFamily.valueOf(mSharedPreferences
                .getString(SharedPreferencesUtils.FONT_FAMILY_KEY, FontFamily.Default.name())).getResId(), true);

        getTheme().applyStyle(TitleFontFamily.valueOf(mSharedPreferences
                .getString(SharedPreferencesUtils.TITLE_FONT_FAMILY_KEY, TitleFontFamily.Default.name())).getResId(), true);

        getTheme().applyStyle(ContentFontFamily.valueOf(mSharedPreferences
                .getString(SharedPreferencesUtils.CONTENT_FONT_FAMILY_KEY, ContentFontFamily.Default.name())).getResId(), true);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request original = chain.request();
                        if (original.url().host().equals("files.catbox.moe")) {
                            Request request = original.newBuilder().removeHeader("user-agent")
                                    .method(original.method(), original.body())
                                    .build();
                            return chain.proceed(request);
                        }
                        return chain.proceed(original);
                    }
                })
                .build();
        BigImageViewer.initialize(GlideImageLoader.with(this.getApplicationContext(), client));

        setContentView(R.layout.activity_view_image_or_gif);

        ButterKnife.bind(this);

        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        if (mSharedPreferences.getBoolean(SharedPreferencesUtils.SWIPE_VERTICALLY_TO_GO_BACK_FROM_MEDIA, true)) {
            Slidr.attach(this, new SlidrConfig.Builder().position(SlidrPosition.VERTICAL).distanceThreshold(0.125f).build());
        }

        glide = Glide.with(this);

        handler = new Handler();

        Intent intent = getIntent();
        mImageUrl = intent.getStringExtra(EXTRA_GIF_URL_KEY);
        if (mImageUrl == null) {
            isGif = false;
            mImageUrl = intent.getStringExtra(EXTRA_IMAGE_URL_KEY);
        }
        mImageFileName = intent.getStringExtra(EXTRA_FILE_NAME_KEY);
        String postTitle = intent.getStringExtra(EXTRA_POST_TITLE_KEY);
        mSubredditName = intent.getStringExtra(EXTRA_SUBREDDIT_OR_USERNAME_KEY);
        isNsfw = intent.getBooleanExtra(EXTRA_IS_NSFW, false);
        Log.d(TAG, "Media request url=" + mImageUrl + ", isGif=" + isGif + ", fileName=" + mImageFileName);

        boolean useBottomAppBar = mSharedPreferences.getBoolean(SharedPreferencesUtils.USE_BOTTOM_TOOLBAR_IN_MEDIA_VIEWER, false);
        if (postTitle != null) {
            Spanned title = Html.fromHtml(String.format("<font color=\"#FFFFFF\"><small>%s</small></font>", postTitle));
            if (useBottomAppBar) {
                titleTextView.setText(title);
            } else {
                setTitle(Utils.getTabTextWithCustomFont(typeface, title));
            }
        } else {
            if (!useBottomAppBar) {
                setTitle("");
            }
        }

        if (useBottomAppBar) {
            ActionBar supportActionBar = getSupportActionBar();
            if (supportActionBar != null) {
                supportActionBar.hide();
            }
            bottomAppBar.setVisibility(View.VISIBLE);
            downloadImageView.setOnClickListener(view -> {
                if (isDownloading) {
                    return;
                }
                isDownloading = true;
                requestPermissionAndDownload();
            });
            shareImageView.setOnClickListener(view -> {
                if (isGif)
                    shareGif();
                else
                    shareImage();
            });
            wallpaperImageView.setOnClickListener(view -> {
                setWallpaper();
            });
        } else {
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                Drawable upArrow = getResources().getDrawable(R.drawable.ic_arrow_back_white_24dp);
                actionBar.setHomeAsUpIndicator(upArrow);
                actionBar.setBackgroundDrawable(new ColorDrawable(getResources().getColor(R.color.transparentActionBarAndExoPlayerControllerColor)));
            }
        }

        mLoadErrorLinearLayout.setOnClickListener(view -> {
            mLoadErrorLinearLayout.setVisibility(View.GONE);
            loadImage();
        });

        View.OnClickListener toggleBarsListener = view -> {
            if (isActionBarHidden) {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
                isActionBarHidden = false;
                if (useBottomAppBar) {
                    bottomAppBar.setVisibility(View.VISIBLE);
                }
            } else {
                getWindow().getDecorView().setSystemUiVisibility(
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE);
                isActionBarHidden = true;
                if (useBottomAppBar) {
                    bottomAppBar.setVisibility(View.GONE);
                }
            }
        };
        mImageView.setOnClickListener(toggleBarsListener);
        mGifImageView.setOnPhotoTapListener((view, x, y) -> toggleBarsListener.onClick(view));
        mGifPlayerView.setOnClickListener(toggleBarsListener);
        mVideoView.setOnClickListener(toggleBarsListener);

        mImageView.setImageViewFactory(new GlideImageViewFactory());

        mImageView.setImageLoaderCallback(new ImageLoader.Callback() {
            @Override
            public void onCacheHit(int imageType, File image) {

            }

            @Override
            public void onCacheMiss(int imageType, File image) {

            }

            @Override
            public void onStart() {

            }

            @Override
            public void onProgress(int progress) {

            }

            @Override
            public void onFinish() {

            }

            @Override
            public void onSuccess(File image) {
                mProgressBar.setVisibility(View.GONE);

                final SubsamplingScaleImageView view = mImageView.getSSIV();

                if (view != null) {
                    view.setOnImageEventListener(new SubsamplingScaleImageView.DefaultOnImageEventListener() {
                        @Override
                        public void onImageLoaded() {
                            view.setMinimumDpi(80);
                            view.setDoubleTapZoomDpi(240);
                            view.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_FIXED);
                            view.setQuickScaleEnabled(true);
                            view.resetScaleAndCenter();
                        }
                    });
                }
            }

            @Override
            public void onFail(Exception error) {
                mProgressBar.setVisibility(View.GONE);
                mLoadErrorLinearLayout.setVisibility(View.VISIBLE);
            }
        });

        loadImage();

        // Fixes #383
        // Not having a background will cause visual glitches on some devices.
        FrameLayout slidablePanel = findViewById(R.id.slidable_panel);
        if (slidablePanel != null) {
            slidablePanel.setBackgroundColor(getResources().getColor(android.R.color.black));
        }
    }

    private void loadImage() {
        Log.d(TAG, "Loading media. isGif=" + isGif + ", url=" + mImageUrl);
        if (mImageUrl == null || mImageUrl.isEmpty()) {
            Log.w(TAG, "Cannot load media because URL is empty");
            mProgressBar.setVisibility(View.GONE);
            mLoadErrorLinearLayout.setVisibility(View.VISIBLE);
            return;
        }

        mProgressBar.setVisibility(View.VISIBLE);
        mLoadErrorLinearLayout.setVisibility(View.GONE);
        mImageView.setVisibility(View.GONE);
        mGifImageView.setVisibility(View.GONE);
        mGifPlayerView.setVisibility(View.GONE);
        mVideoView.setVisibility(View.GONE);

        if (isGif) {
            // Check if URL is an MP4 — play it with VideoView instead
            String lowerUrl = mImageUrl.toLowerCase();
            int qi = lowerUrl.indexOf('?');
            String cleanUrl = qi >= 0 ? lowerUrl.substring(0, qi) : lowerUrl;
            if (cleanUrl.endsWith(".mp4")) {
                Log.d(TAG, "Detected mp4 media in gif viewer path");
                mVideoView.setVisibility(View.VISIBLE);
                mVideoView.setVideoURI(Uri.parse(mImageUrl));
                mVideoView.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    mProgressBar.setVisibility(View.GONE);
                    mVideoView.start();
                    Log.d(TAG, "MP4 media prepared and started");
                });
                mVideoView.setOnErrorListener((mp, what, extra) -> {
                    Log.e(TAG, "MP4 playback error. what=" + what + ", extra=" + extra);
                    mProgressBar.setVisibility(View.GONE);
                    mLoadErrorLinearLayout.setVisibility(View.VISIBLE);
                    return true;
                });
                mVideoView.requestFocus();
            } else {
                mGifPlayerView.setVisibility(View.VISIBLE);
                // Use OkHttp directly to download raw GIF bytes — Glide's disk cache
                // wraps files in its own journal format which GifDrawable cannot parse.
                new Thread(() -> {
                    try {
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                        okhttp3.Request request = new okhttp3.Request.Builder().url(mImageUrl).build();
                        okhttp3.Response response = client.newCall(request).execute();
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d(TAG, "GIF bytes downloaded successfully");
                            byte[] bytes = response.body().bytes();
                            GifDrawable gifDrawable = new GifDrawable(bytes);
                            runOnUiThread(() -> {
                                mProgressBar.setVisibility(View.GONE);
                                mGifPlayerView.setImageDrawable(gifDrawable);
                                gifDrawable.start();
                            });
                        } else {
                            Log.w(TAG, "GIF download failed with code=" + response.code());
                            runOnUiThread(() -> {
                                mProgressBar.setVisibility(View.GONE);
                                mLoadErrorLinearLayout.setVisibility(View.VISIBLE);
                            });
                        }
                    } catch (java.io.IOException e) {
                        Log.w(TAG, "GIF download failed, falling back to static image", e);
                        runOnUiThread(() -> {
                            mProgressBar.setVisibility(View.GONE);
                            loadAsStaticImage();
                        });
                    }
                }).start();
            }
        } else {
            loadAsStaticImage();
        }
    }

    private void loadAsStaticImage() {
        Log.d(TAG, "Loading media as static image");
        mGifPlayerView.setVisibility(View.GONE);
        mGifImageView.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(mImageUrl)
                .listener(new com.bumptech.glide.request.RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e,
                                                Object model,
                                                com.bumptech.glide.request.target.Target<Drawable> target,
                                                boolean isFirstResource) {
                        Log.e(TAG, "Static image load failed", e);
                        runOnUiThread(() -> {
                            mProgressBar.setVisibility(View.GONE);
                            mLoadErrorLinearLayout.setVisibility(View.VISIBLE);
                        });
                        return false;
                    }
                    @Override
                    public boolean onResourceReady(Drawable resource,
                                                   Object model,
                                                   com.bumptech.glide.request.target.Target<Drawable> target,
                                                   com.bumptech.glide.load.DataSource dataSource,
                                                   boolean isFirstResource) {
                        Log.d(TAG, "Static image loaded from " + dataSource);
                        runOnUiThread(() -> mProgressBar.setVisibility(View.GONE));
                        return false;
                    }
                })
                .into(mGifImageView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.view_image_or_gif_activity, menu);
        for (int i = 0; i < menu.size(); i++) {
            Utils.setTitleWithCustomFontToMenuItem(typeface, menu.getItem(i), null);
        }
        if (!isGif) {
            menu.findItem(R.id.action_set_wallpaper_view_image_or_gif_activity).setVisible(true);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            finish();
            return true;
        } else if (itemId == R.id.action_download_view_image_or_gif_activity) {
            if (isDownloading) {
                return false;
            }
            isDownloading = true;
            requestPermissionAndDownload();
            return true;
        } else if (itemId == R.id.action_share_view_image_or_gif_activity) {
            if (isGif)
                shareGif();
            else
                shareImage();
            return true;
        } else if (itemId == R.id.action_set_wallpaper_view_image_or_gif_activity) {
            setWallpaper();
            return true;
        }

        return false;
    }

    private void requestPermissionAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                // Permission is not granted
                // No explanation needed; request the permission
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
            } else {
                // Permission has already been granted
                download();
            }
        } else {
            download();
        }
    }

    private void download() {
        isDownloading = false;
        Log.d(TAG, "Starting media download. isGif=" + isGif + ", url=" + mImageUrl);

        Intent intent = new Intent(this, DownloadMediaService.class);
        intent.putExtra(DownloadMediaService.EXTRA_URL, mImageUrl);
        intent.putExtra(DownloadMediaService.EXTRA_MEDIA_TYPE, isGif ? DownloadMediaService.EXTRA_MEDIA_TYPE_GIF : DownloadMediaService.EXTRA_MEDIA_TYPE_IMAGE);
        intent.putExtra(DownloadMediaService.EXTRA_FILE_NAME, mImageFileName);
        intent.putExtra(DownloadMediaService.EXTRA_SUBREDDIT_NAME, mSubredditName);
        intent.putExtra(DownloadMediaService.EXTRA_IS_NSFW, isNsfw);
        ContextCompat.startForegroundService(this, intent);
        Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show();
    }

    private void shareImage() {
        Log.d(TAG, "Sharing image from url=" + mImageUrl);
        glide.asBitmap().load(mImageUrl).into(new CustomTarget<Bitmap>() {

            @Override
            public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                if (getExternalCacheDir() != null) {
                    Toast.makeText(ViewImageOrGifActivity.this, R.string.save_image_first, Toast.LENGTH_SHORT).show();
                    SaveBitmapImageToFile.SaveBitmapImageToFile(mExecutor, handler, resource,
                            getExternalCacheDir().getPath(), mImageFileName,
                            new SaveBitmapImageToFile.SaveBitmapImageToFileListener() {
                                @Override
                                public void saveSuccess(File imageFile) {
                                    Uri uri = FileProvider.getUriForFile(ViewImageOrGifActivity.this,
                                            BuildConfig.APPLICATION_ID + ".provider", imageFile);
                                    Intent shareIntent = new Intent();
                                    shareIntent.setAction(Intent.ACTION_SEND);
                                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                                    shareIntent.setType("image/*");
                                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
                                }

                                @Override
                                public void saveFailed() {
                                    Log.e(TAG, "Failed to cache image for sharing");
                                    Toast.makeText(ViewImageOrGifActivity.this,
                                            R.string.cannot_save_image, Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    Toast.makeText(ViewImageOrGifActivity.this,
                            R.string.cannot_get_storage, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onLoadCleared(@Nullable Drawable placeholder) {

            }
        });
    }

    private void shareGif() {
        Log.d(TAG, "Sharing gif from url=" + mImageUrl);
        Toast.makeText(ViewImageOrGifActivity.this, R.string.save_gif_first, Toast.LENGTH_SHORT).show();
        glide.asGif().load(mImageUrl).listener(new RequestListener<com.bumptech.glide.load.resource.gif.GifDrawable>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                        Target<com.bumptech.glide.load.resource.gif.GifDrawable> target,
                                        boolean isFirstResource) {
                Log.e(TAG, "Failed to load gif for sharing", e);
                return false;
            }

            @Override
            public boolean onResourceReady(com.bumptech.glide.load.resource.gif.GifDrawable resource,
                                           Object model,
                                           Target<com.bumptech.glide.load.resource.gif.GifDrawable> target,
                                           DataSource dataSource,
                                           boolean isFirstResource) {
                if (getExternalCacheDir() != null) {
                    SaveGIFToFile.saveGifToFile(mExecutor, handler, resource, getExternalCacheDir().getPath(), mImageFileName,
                            new SaveGIFToFile.SaveGIFToFileAsyncTaskListener() {
                                @Override
                                public void saveSuccess(File imageFile) {
                                    Uri uri = FileProvider.getUriForFile(ViewImageOrGifActivity.this,
                                            BuildConfig.APPLICATION_ID + ".provider", imageFile);
                                    Intent shareIntent = new Intent();
                                    shareIntent.setAction(Intent.ACTION_SEND);
                                    shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                                    shareIntent.setType("image/gif");
                                    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share)));
                                }

                                @Override
                                public void saveFailed() {
                                    Log.e(TAG, "Failed to cache gif for sharing");
                                    Toast.makeText(ViewImageOrGifActivity.this,
                                            R.string.cannot_save_gif, Toast.LENGTH_SHORT).show();
                                }
                            });
                } else {
                    Toast.makeText(ViewImageOrGifActivity.this,
                            R.string.cannot_get_storage, Toast.LENGTH_SHORT).show();
                }
                return false;
            }
        }).submit();
    }

    private void setWallpaper() {
        if (!isGif) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SetAsWallpaperBottomSheetFragment setAsWallpaperBottomSheetFragment = new SetAsWallpaperBottomSheetFragment();
                setAsWallpaperBottomSheetFragment.show(getSupportFragmentManager(), setAsWallpaperBottomSheetFragment.getTag());
            } else {
                WallpaperSetter.set(mExecutor, handler, mImageUrl, WallpaperSetter.BOTH_SCREENS, this,
                        new WallpaperSetter.SetWallpaperListener() {
                            @Override
                            public void success() {
                                Toast.makeText(ViewImageOrGifActivity.this, R.string.wallpaper_set, Toast.LENGTH_SHORT).show();
                            }

                            @Override
                            public void failed() {
                                Toast.makeText(ViewImageOrGifActivity.this, R.string.error_set_wallpaper, Toast.LENGTH_SHORT).show();
                            }
                        });
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, R.string.no_storage_permission, Toast.LENGTH_SHORT).show();
                isDownloading = false;
            } else if (grantResults[0] == PackageManager.PERMISSION_GRANTED && isDownloading) {
                download();
            }
        }
    }

    @Override
    public void setToHomeScreen(int viewPagerPosition) {
        WallpaperSetter.set(mExecutor, handler, mImageUrl, WallpaperSetter.HOME_SCREEN, this,
                new WallpaperSetter.SetWallpaperListener() {
                    @Override
                    public void success() {
                        Toast.makeText(ViewImageOrGifActivity.this, R.string.wallpaper_set, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void failed() {
                        Toast.makeText(ViewImageOrGifActivity.this, R.string.error_set_wallpaper, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void setToLockScreen(int viewPagerPosition) {
        WallpaperSetter.set(mExecutor, handler, mImageUrl, WallpaperSetter.LOCK_SCREEN, this,
                new WallpaperSetter.SetWallpaperListener() {
                    @Override
                    public void success() {
                        Toast.makeText(ViewImageOrGifActivity.this, R.string.wallpaper_set, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void failed() {
                        Toast.makeText(ViewImageOrGifActivity.this, R.string.error_set_wallpaper, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void setToBoth(int viewPagerPosition) {
        WallpaperSetter.set(mExecutor, handler, mImageUrl, WallpaperSetter.BOTH_SCREENS, this,
                new WallpaperSetter.SetWallpaperListener() {
                    @Override
                    public void success() {
                        Toast.makeText(ViewImageOrGifActivity.this, R.string.wallpaper_set, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void failed() {
                        Toast.makeText(ViewImageOrGifActivity.this, R.string.error_set_wallpaper, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mVideoView != null && mVideoView.isPlaying()) {
            Log.d(TAG, "Pausing video playback");
            mVideoView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mVideoView != null && mVideoView.getVisibility() == View.VISIBLE) {
            Log.d(TAG, "Resuming video playback");
            mVideoView.start();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        BigImageViewer.imageLoader().cancelAll();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(TAG, "onConfigurationChanged orientation=" + newConfig.orientation);
        // Re-apply immersive flags after orientation switches to avoid UI-state related crashes.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void setCustomFont(Typeface typeface, Typeface titleTypeface, Typeface contentTypeface) {
        this.typeface = typeface;
    }
}

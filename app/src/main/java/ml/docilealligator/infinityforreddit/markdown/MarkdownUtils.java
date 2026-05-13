package ml.docilealligator.infinityforreddit.markdown;

import android.content.Context;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.text.util.Linkify;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.request.target.Target;

import org.commonmark.ext.gfm.tables.TableBlock;

import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonPlugin;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.image.AsyncDrawable;
import io.noties.markwon.image.glide.GlideImagesPlugin;
import io.noties.markwon.inlineparser.BangInlineProcessor;
import io.noties.markwon.inlineparser.HtmlInlineProcessor;
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin;
import io.noties.markwon.linkify.LinkifyPlugin;
import io.noties.markwon.movement.MovementMethodPlugin;
import io.noties.markwon.recycler.MarkwonAdapter;
import io.noties.markwon.recycler.table.TableEntry;
import io.noties.markwon.recycler.table.TableEntryPlugin;
import me.saket.bettermovementmethod.BetterLinkMovementMethod;
import ml.docilealligator.infinityforreddit.R;
import ml.docilealligator.infinityforreddit.customviews.CustomMarkwonAdapter;

public class MarkdownUtils {

    /**
     * Returns an animated spinning placeholder drawable to show while an inline image or GIF
     * is loading. The drawable is bundled as a resource so it never needs a network request.
     * Starts the animation immediately so users see movement right away.
     */
    @NonNull
    private static Drawable getLoadingPlaceholder(@NonNull Context context) {
        Drawable d = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_image_loading_animated);
        if (d instanceof Animatable) {
            ((Animatable) d).start();
        }
        return d != null ? d : new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT);
    }
    /**
     * Creates a Markwon instance with all the plugins required for processing Reddit's markdown.
     * @return configured Markwon instance
     */
    @NonNull
    public static Markwon createFullRedditMarkwon(@NonNull Context context,
                                                  @NonNull MarkwonPlugin miscPlugin,
                                                  int markdownColor,
                                                  int spoilerBackgroundColor,
                                                  @Nullable BetterLinkMovementMethod.OnLinkLongClickListener onLinkLongClickListener) {
        return createFullRedditMarkwon(context, miscPlugin, markdownColor, spoilerBackgroundColor,
                onLinkLongClickListener, null);
    }

    @NonNull
    public static Markwon createFullRedditMarkwon(@NonNull Context context,
                                                  @NonNull MarkwonPlugin miscPlugin,
                                                  int markdownColor,
                                                  int spoilerBackgroundColor,
                                                  @Nullable BetterLinkMovementMethod.OnLinkLongClickListener onLinkLongClickListener,
                                                  @Nullable SpoilerAwareMovementMethod.OnImageClickListener onImageClickListener) {
        return Markwon.builder(context)
                .usePlugin(MarkwonInlineParserPlugin.create(plugin -> {
                    plugin.excludeInlineProcessor(HtmlInlineProcessor.class);
                    // BangInlineProcessor enabled to allow inline image previews in comments
                }))
                .usePlugin(GlideImagesPlugin.create(new GlideImagesPlugin.GlideStore() {
                    // Max dimensions for inline images to prevent OOM / "bitmap too large" crashes
                    private static final int MAX_IMAGE_SIZE = 2048;

                    @NonNull
                    @Override
                    public RequestBuilder<Drawable> load(@NonNull AsyncDrawable drawable) {
                        String url = drawable.getDestination();
                        String lower = url.toLowerCase();
                        int q = lower.indexOf('?');
                        String clean = q >= 0 ? lower.substring(0, q) : lower;

                        // Giphy mp4 -> request gif version instead
                        if (clean.contains("giphy.com") && clean.endsWith(".mp4")) {
                            url = url.substring(0, url.length() - 4) + ".gif";
                            clean = clean.substring(0, clean.length() - 4) + ".gif";
                        }

                        try {
                            // Force asGif() for .gif URLs so Glide returns an Animatable GifDrawable
                            if (clean.endsWith(".gif")) {
                                //noinspection unchecked
                                return (RequestBuilder<Drawable>) (RequestBuilder<?>)
                                        Glide.with(context).asGif()
                                                .placeholder(getLoadingPlaceholder(context))
                                                .override(MAX_IMAGE_SIZE, MAX_IMAGE_SIZE)
                                                .load(url);
                            }
                            return Glide.with(context).load(url)
                                    .placeholder(getLoadingPlaceholder(context))
                                    .override(MAX_IMAGE_SIZE, MAX_IMAGE_SIZE);
                        } catch (IllegalArgumentException e) {
                            // Glide.with() throws if the activity/fragment is destroyed; return
                            // a no-op builder that will simply show nothing rather than crashing.
                            return Glide.with(context.getApplicationContext()).load(url)
                                    .placeholder(getLoadingPlaceholder(context))
                                    .override(MAX_IMAGE_SIZE, MAX_IMAGE_SIZE);
                        }
                    }

                    @SuppressWarnings({"unchecked", "rawtypes"})
                    @Override
                    public void cancel(@NonNull Target target) {
                        try {
                            Glide.with(context).clear(target);
                        } catch (IllegalArgumentException ignored) {
                            // Activity already destroyed — Glide request already cancelled
                        }
                    }
                }))
                .usePlugin(InlineGifPlugin.create())
                .usePlugin(miscPlugin)
                .usePlugin(SuperscriptPlugin.create())
                .usePlugin(SpoilerParserPlugin.create(markdownColor, spoilerBackgroundColor))
                .usePlugin(RedditHeadingPlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(MovementMethodPlugin.create(new SpoilerAwareMovementMethod()
                        .setOnImageClickListener(onImageClickListener)
                        .setOnLinkLongClickListener(onLinkLongClickListener)))
                .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
                .usePlugin(TableEntryPlugin.create(context))
                .usePlugin(ImagesPlugin.create(new ImagesPlugin.ImagesConfigure() {
                    @Override
                    public void configureImages(@NonNull ImagesPlugin plugin) {
                        plugin.placeholderProvider(drawable -> ResourcesCompat.getDrawable(context.getResources(), R.drawable.ic_image_24dp, context.getTheme()));

                    }
                }))
                .usePlugin(ClickImagePlugin.create(context))
                .build();
    }

    @NonNull
    public static Markwon createDescriptionMarkwon(Context context, MarkwonPlugin miscPlugin,
                                                   BetterLinkMovementMethod.OnLinkLongClickListener onLinkLongClickListener) {
        return Markwon.builder(context)
                .usePlugin(MarkwonInlineParserPlugin.create(plugin -> {
                    plugin.excludeInlineProcessor(HtmlInlineProcessor.class);
                    plugin.excludeInlineProcessor(BangInlineProcessor.class);
                }))
                .usePlugin(miscPlugin)
                .usePlugin(SuperscriptPlugin.create())
                .usePlugin(RedditHeadingPlugin.create())
                .usePlugin(StrikethroughPlugin.create())
                .usePlugin(MovementMethodPlugin.create(new SpoilerAwareMovementMethod()
                        .setOnLinkLongClickListener(onLinkLongClickListener)))
                .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
                .usePlugin(TableEntryPlugin.create(context))
                .build();
    }

    /**
     * Creates a Markwon instance that processes only the links.
     * @return configured Markwon instance
     */
    @NonNull
    public static Markwon createLinksOnlyMarkwon(@NonNull Context context,
                                                  @NonNull MarkwonPlugin miscPlugin,
                                                  @Nullable BetterLinkMovementMethod.OnLinkLongClickListener onLinkLongClickListener) {
        return Markwon.builder(context)
                .usePlugin(MarkwonInlineParserPlugin.create(plugin -> {
                    plugin.excludeInlineProcessor(HtmlInlineProcessor.class);
                    plugin.excludeInlineProcessor(BangInlineProcessor.class);
                }))
                .usePlugin(miscPlugin)
                .usePlugin(MovementMethodPlugin.create(BetterLinkMovementMethod.newInstance().setOnLinkLongClickListener(onLinkLongClickListener)))
                .usePlugin(LinkifyPlugin.create(Linkify.WEB_URLS))
                .build();
    }

    /**
     * Creates a MarkwonAdapter configured with support for tables.
     */
    @NonNull
    public static MarkwonAdapter createTablesAdapter() {
        return MarkwonAdapter.builder(R.layout.adapter_default_entry, R.id.text)
                .include(TableBlock.class, TableEntry.create(builder -> builder
                        .tableLayout(R.layout.adapter_table_block, R.id.table_layout)
                        .textLayoutIsRoot(R.layout.view_table_entry_cell)))
                .build();
    }

    /**
     * Creates a CustomMarkwonAdapter configured with support for tables.
     */
    @NonNull
    public static CustomMarkwonAdapter createCustomTablesAdapter() {
        return CustomMarkwonAdapter.builder(R.layout.adapter_default_entry, R.id.text)
                .include(TableBlock.class, TableEntry.create(builder -> builder
                        .tableLayout(R.layout.adapter_table_block, R.id.table_layout)
                        .textLayoutIsRoot(R.layout.view_table_entry_cell)))
                .build();
    }
}

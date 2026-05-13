package ml.docilealligator.infinityforreddit.markdown;

import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.text.Spanned;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.commonmark.node.Node;

import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.MarkwonVisitor;
import io.noties.markwon.image.AsyncDrawableScheduler;
import io.noties.markwon.image.AsyncDrawableSpan;

/**
 * Markwon plugin that converts links pointing to image/GIF URLs into inline images,
 * so GlideImagesPlugin renders them as previews rather than clickable text links.
 */
public class InlineGifPlugin extends AbstractMarkwonPlugin {

    private InlineGifPlugin() {}

    public static InlineGifPlugin create() {
        return new InlineGifPlugin();
    }

    public static boolean isImageUrl(String url) {
        if (url == null) return false;
        String clean = url.toLowerCase();
        int q = clean.indexOf('?');
        if (q != -1) clean = clean.substring(0, q);
        // Standard image/gif extensions
        if (clean.endsWith(".gif") || clean.endsWith(".jpg") || clean.endsWith(".jpeg")
                || clean.endsWith(".png") || clean.endsWith(".webp")) {
            return true;
        }
        // Giphy always treated as image (mp4 or not — we'll request the gif version)
        if (clean.contains("giphy.com")) return true;
        // Reddit image hosts
        return clean.contains("preview.redd.it") || clean.contains("i.redd.it");
    }

    // Matches bare http/https URLs
    private static final java.util.regex.Pattern BARE_URL_PATTERN =
            java.util.regex.Pattern.compile("https?://\\S+");

    /**
     * Pre-process markdown:
     * 1. Convert [text](image-url) -> ![text](image-url)
     * 2. Convert bare image URLs -> ![](url)
     * So GlideImagesPlugin handles them natively.
     */
    public static String preprocessMarkdown(String markdown) {
        if (markdown == null) return "";

        // Step 1: convert [text](image-url) -> ![text](image-url)
        StringBuilder sb = new StringBuilder(markdown);
        int i = 0;
        while (i < sb.length()) {
            int bracketStart = sb.indexOf("[", i);
            if (bracketStart == -1) break;
            if (bracketStart > 0 && sb.charAt(bracketStart - 1) == '!') {
                i = bracketStart + 1;
                continue;
            }
            int bracketEnd = sb.indexOf("](", bracketStart);
            if (bracketEnd == -1) break;
            int urlStart = bracketEnd + 2;
            int urlEnd = sb.indexOf(")", urlStart);
            if (urlEnd == -1) break;
            String url = sb.substring(urlStart, urlEnd);
            if (isImageUrl(url)) {
                sb.insert(bracketStart, '!');
                i = urlEnd + 2;
            } else {
                i = bracketEnd + 1;
            }
        }
        String result = sb.toString();

        // Step 2: convert bare image URLs -> ![](url)
        // Skip URLs already inside markdown link/image syntax (preceded by '(' )
        java.util.regex.Matcher m = BARE_URL_PATTERN.matcher(result);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String url = m.group(0);
            int start = m.start();
            // Skip if already inside []() — char before is '('
            char before = start > 0 ? result.charAt(start - 1) : 0;
            if (before == '(' || before == '<') {
                m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(url));
            } else if (isImageUrl(url)) {
                m.appendReplacement(out, "![](" + java.util.regex.Matcher.quoteReplacement(url) + ")");
            } else {
                m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(url));
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    @Override
    public void configureVisitor(@NonNull MarkwonVisitor.Builder builder) {
        // NOTE: We intentionally do NOT override the Link visitor here.
        //
        // Image links ([text](image-url)) are converted to markdown image syntax
        // (![text](image-url)) by preprocessMarkdown() before Markwon parses the text,
        // so GlideImagesPlugin handles them as images natively.
        //
        // Non-image links must fall through to Markwon's built-in Link visitor which
        // produces a proper URLSpan / ClickableSpan. Any override that calls
        // visitChildren() instead of the default handler silently discards the link
        // destination, causing all article/web links to disappear from comment text.
    }

    @Override
    public void beforeSetText(@NonNull TextView textView, @NonNull Spanned markdown) {
        AsyncDrawableScheduler.unschedule(textView);
    }

    @Override
    public void afterSetText(@NonNull TextView textView) {
        AsyncDrawableScheduler.schedule(textView);
    }

    /** Called by CustomMarkwonAdapter after the view is attached — start any loaded GIFs */
    public static void startGifs(TextView textView) {
        reattachAndStartGifs(textView);
    }

    /**
     * Re-attaches the invalidation callback on every AsyncDrawableSpan and (re)starts the
     * animation. Safe to call even when the GIF is already "running", because scrolling away
     * and back can silently break the callback chain without updating isRunning().
     */
    public static void reattachAndStartGifs(TextView textView) {
        if (!(textView.getText() instanceof Spanned)) return;
        Spanned spanned = (Spanned) textView.getText();
        for (AsyncDrawableSpan span : spanned.getSpans(0, spanned.length(), AsyncDrawableSpan.class)) {
            Drawable result = span.getDrawable().getResult();
            if (result instanceof Animatable) {
                // Always re-attach the callback so invalidation still works after unschedule.
                result.setCallback(new Drawable.Callback() {
                    @Override
                    public void invalidateDrawable(@NonNull Drawable who) {
                        textView.invalidate();
                    }
                    @Override
                    public void scheduleDrawable(@NonNull Drawable who, @NonNull Runnable what, long when) {
                        textView.postDelayed(what, when - android.os.SystemClock.uptimeMillis());
                    }
                    @Override
                    public void unscheduleDrawable(@NonNull Drawable who, @NonNull Runnable what) {
                        textView.removeCallbacks(what);
                    }
                });
                // Stop then start to reset any broken internal state in GifDrawable.
                ((Animatable) result).stop();
                ((Animatable) result).start();
            }
        }
    }
}

package ml.docilealligator.infinityforreddit.markdown;

import android.graphics.RectF;
import android.text.Layout;
import android.text.Spannable;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.noties.markwon.image.AsyncDrawableSpan;
import me.saket.bettermovementmethod.BetterLinkMovementMethod;

/**
 * Extension of {@link BetterLinkMovementMethod} that handles {@link SpoilerSpan}s
 * and inline image taps (opening them in a full-screen viewer).
 */
public class SpoilerAwareMovementMethod extends BetterLinkMovementMethod {

    /** Callback for when the user taps an inline image. */
    public interface OnImageClickListener {
        void onImageClick(TextView textView, String imageUrl);
    }

    private final RectF touchedLineBounds = new RectF();
    private OnImageClickListener mOnImageClickListener;

    public SpoilerAwareMovementMethod setOnImageClickListener(@Nullable OnImageClickListener listener) {
        mOnImageClickListener = listener;
        return this;
    }

    // Tracks whether the current touch sequence started on an image span.
    private String mTouchedImageUrl = null;
    private float mTouchDownX = 0;
    private float mTouchDownY = 0;
    // Slop in pixels beyond which we consider the gesture a scroll, not a tap.
    private static final int TAP_SLOP = 20;

    @Override
    public boolean onTouchEvent(@NonNull TextView widget, @NonNull Spannable buffer, @NonNull MotionEvent event) {
        if (mOnImageClickListener != null) {
            final int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                String imageUrl = findImageUrlUnderTouch(widget, buffer, event);
                mTouchedImageUrl = imageUrl;
                mTouchDownX = event.getX();
                mTouchDownY = event.getY();
                if (imageUrl != null) {
                    widget.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                if (mTouchedImageUrl != null) {
                    float dx = Math.abs(event.getX() - mTouchDownX);
                    float dy = Math.abs(event.getY() - mTouchDownY);
                    if (dx > TAP_SLOP || dy > TAP_SLOP) {
                        // Finger moved — release intercept lock so RecyclerView can scroll.
                        mTouchedImageUrl = null;
                        widget.getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }
            } else if (action == MotionEvent.ACTION_UP) {
                String downUrl = mTouchedImageUrl;
                mTouchedImageUrl = null;
                if (downUrl != null) {
                    float dx = Math.abs(event.getX() - mTouchDownX);
                    float dy = Math.abs(event.getY() - mTouchDownY);
                    if (dx <= TAP_SLOP && dy <= TAP_SLOP) {
                        // Confirmed tap (not a scroll) on an image span.
                        widget.getParent().requestDisallowInterceptTouchEvent(false);
                        mOnImageClickListener.onImageClick(widget, downUrl);
                        return true;
                    }
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                mTouchedImageUrl = null;
                if (widget.getParent() != null) {
                    widget.getParent().requestDisallowInterceptTouchEvent(false);
                }
            }
        }
        return super.onTouchEvent(widget, buffer, event);
    }

    /** Returns the image URL under the touch point, or null if no image span is there. */
    @Nullable
    private String findImageUrlUnderTouch(@NonNull TextView widget, @NonNull Spannable buffer,
                                          @NonNull MotionEvent event) {
        int touchX = (int) event.getX() - widget.getTotalPaddingLeft() + widget.getScrollX();
        int touchY = (int) event.getY() - widget.getTotalPaddingTop() + widget.getScrollY();

        Layout layout = widget.getLayout();
        if (layout == null) return null;

        int line = layout.getLineForVertical(touchY);
        int lineTop = layout.getLineTop(line);
        int lineBottom = layout.getLineBottom(line);
        // Reject touches outside the line vertically
        if (touchY < lineTop || touchY > lineBottom) return null;

        int offset = layout.getOffsetForHorizontal(line, touchX);
        // Search the full span range for any AsyncDrawableSpan that covers this offset
        AsyncDrawableSpan[] imageSpans = buffer.getSpans(0, buffer.length(), AsyncDrawableSpan.class);
        if (imageSpans == null) return null;
        for (AsyncDrawableSpan span : imageSpans) {
            int spanStart = buffer.getSpanStart(span);
            int spanEnd = buffer.getSpanEnd(span);
            if (offset >= spanStart && offset <= spanEnd) {
                String url = span.getDrawable().getDestination();
                if (url != null && !url.isEmpty()) return url;
            }
        }
        return null;
    }

    @Override
    protected ClickableSpan findClickableSpanUnderTouch(TextView textView, Spannable text, MotionEvent event) {
        // A copy of super method. Logic for selecting correct clickable span was moved to selectClickableSpan

        // So we need to find the location in text where touch was made, regardless of whether the TextView
        // has scrollable text. That is, not the entire text is currently visible.
        int touchX = (int) event.getX();
        int touchY = (int) event.getY();

        // Ignore padding.
        touchX -= textView.getTotalPaddingLeft();
        touchY -= textView.getTotalPaddingTop();

        // Account for scrollable text.
        touchX += textView.getScrollX();
        touchY += textView.getScrollY();

        final Layout layout = textView.getLayout();
        final int touchedLine = layout.getLineForVertical(touchY);
        final int touchOffset = layout.getOffsetForHorizontal(touchedLine, touchX);

        touchedLineBounds.left = layout.getLineLeft(touchedLine);
        touchedLineBounds.top = layout.getLineTop(touchedLine);
        touchedLineBounds.right = layout.getLineWidth(touchedLine) + touchedLineBounds.left;
        touchedLineBounds.bottom = layout.getLineBottom(touchedLine);

        if (touchedLineBounds.contains(touchX, touchY)) {
            // Find a ClickableSpan that lies under the touched area.
            final Object[] spans = text.getSpans(touchOffset, touchOffset, ClickableSpan.class);
            // BEGIN Infinity changed
            return selectClickableSpan(spans);
            // END Infinity changed
        } else {
            // Touch lies outside the line's horizontal bounds where no spans should exist.
            return null;
        }
    }

    /**
     * Select a span according to priorities:
     * 1. Hidden spoiler
     * 2. Non-spoiler span (i.e. link)
     * 3. Shown spoiler
     */
    @Nullable
    private ClickableSpan selectClickableSpan(@NonNull Object[] spans) {
        SpoilerSpan spoilerSpan = null;
        ClickableSpan nonSpoilerSpan = null;
        for (int i = spans.length - 1; i >= 0; i--) {
            final Object span = spans[i];
            if (span instanceof SpoilerSpan) {
                spoilerSpan = (SpoilerSpan) span;
            } else if (span instanceof ClickableSpan) {
                nonSpoilerSpan = (ClickableSpan) span;
            }
        }

        if (spoilerSpan != null && !spoilerSpan.isShowing()) {
            return spoilerSpan;
        } else if (nonSpoilerSpan != null){
            return nonSpoilerSpan;
        } else {
            return spoilerSpan;
        }
    }

    @Override
    protected void dispatchUrlLongClick(TextView textView, ClickableSpan clickableSpan) {
        if (clickableSpan instanceof SpoilerSpan) {
            ((SpoilerSpan) clickableSpan).onLongClick(textView);
            return;
        }
        super.dispatchUrlLongClick(textView, clickableSpan);
    }

    @Override
    protected void highlightUrl(TextView textView, ClickableSpan clickableSpan, Spannable text) {
        if (clickableSpan instanceof SpoilerSpan) {
            return;
        }
        super.highlightUrl(textView, clickableSpan, text);
    }
}

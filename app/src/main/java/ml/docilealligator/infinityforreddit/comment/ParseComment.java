package ml.docilealligator.infinityforreddit.comment;

import static ml.docilealligator.infinityforreddit.comment.Comment.VOTE_TYPE_DOWNVOTE;
import static ml.docilealligator.infinityforreddit.comment.Comment.VOTE_TYPE_NO_VOTE;
import static ml.docilealligator.infinityforreddit.comment.Comment.VOTE_TYPE_UPVOTE;

import android.os.Handler;
import android.text.Html;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

import ml.docilealligator.infinityforreddit.post.ParsePost;
import ml.docilealligator.infinityforreddit.utils.JSONUtils;
import ml.docilealligator.infinityforreddit.utils.Utils;

public class ParseComment {
    public static void parseComment(Executor executor, Handler handler, String response,
                                    boolean expandChildren,
                                    ParseCommentListener parseCommentListener) {
        executor.execute(() -> {
            try {
                JSONArray childrenArray = new JSONArray(response);
                String parentId = childrenArray.getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY)
                        .getJSONObject(0).getJSONObject(JSONUtils.DATA_KEY).getString(JSONUtils.NAME_KEY);
                childrenArray = childrenArray.getJSONObject(1).getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);

                ArrayList<Comment> expandedNewComments = new ArrayList<>();
                ArrayList<String> moreChildrenIds = new ArrayList<>();
                ArrayList<Comment> newComments = new ArrayList<>();

                parseCommentRecursion(childrenArray, newComments, moreChildrenIds, 0);
                expandChildren(newComments, expandedNewComments, expandChildren);

                ArrayList<Comment> commentData;
                if (expandChildren) {
                    commentData = expandedNewComments;
                } else {
                    commentData = newComments;
                }

                handler.post(() -> parseCommentListener.onParseCommentSuccess(newComments, commentData, parentId, moreChildrenIds));
            } catch (JSONException e) {
                e.printStackTrace();
                handler.post(parseCommentListener::onParseCommentFailed);
            }
        });
    }

    public static void parseCommentGQL(Executor executor, Handler handler, String response, String authorName,
                                       boolean expandChildren,
                                       ParseCommentListener parseCommentListener) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject(response).getJSONObject("data");

                String postId = data.getJSONObject("postInfoById").getString("id");
                String postType = data.getJSONObject("postInfoById").getString("__typename");
                String subredditName = "";
                if (postType.equals("ProfilePost")) {
                    subredditName= data.getJSONObject("postInfoById").getJSONObject("profile").getJSONObject("redditorInfo").getString("name");
                }else{
                    subredditName= data.getJSONObject("postInfoById").getJSONObject("subreddit").getString("name");
                }

                JSONArray childrenArray = data.getJSONObject("postInfoById").getJSONObject("commentForest").getJSONArray("trees");

                ArrayList<Comment> expandedNewComments = new ArrayList<>();
                ArrayList<Comment> newComments = new ArrayList<>();
                ArrayList<String> moreChildrenIds = new ArrayList<>();

                parseCommentRecursionGQL(childrenArray, newComments, moreChildrenIds, postId, subredditName, authorName);
                expandChildren(newComments, expandedNewComments, expandChildren);

                ArrayList<Comment> commentData;
                if (expandChildren) {
                    commentData = expandedNewComments;
                } else {
                    commentData = newComments;
                }

                handler.post(() -> parseCommentListener.onParseCommentSuccess(newComments, commentData, postId, moreChildrenIds));
            } catch (JSONException e) {
                e.printStackTrace();
                handler.post(parseCommentListener::onParseCommentFailed);
            }
        });
    }

    static void parseMoreComment(Executor executor, Handler handler, String response, boolean expandChildren,
                                 ParseCommentListener parseCommentListener) {
        executor.execute(() -> {
            try {
                JSONArray childrenArray = new JSONObject(response).getJSONObject(JSONUtils.JSON_KEY)
                        .getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.THINGS_KEY);

                ArrayList<Comment> newComments = new ArrayList<>();
                ArrayList<Comment> expandedNewComments = new ArrayList<>();
                ArrayList<String> moreChildrenIds = new ArrayList<>();

                // api response is a flat list of comments tree
                // process it in order and rebuild the tree
                for (int i = 0; i < childrenArray.length(); i++) {
                    JSONObject child = childrenArray.getJSONObject(i);
                    JSONObject childData = child.getJSONObject(JSONUtils.DATA_KEY);
                    if (child.getString(JSONUtils.KIND_KEY).equals(JSONUtils.KIND_VALUE_MORE)) {
                        String parentFullName = childData.getString(JSONUtils.PARENT_ID_KEY);
                        JSONArray childrenIds = childData.getJSONArray(JSONUtils.CHILDREN_KEY);

                        if (childrenIds.length() != 0) {
                            ArrayList<String> localMoreChildrenIds = new ArrayList<>(childrenIds.length());
                            for (int j = 0; j < childrenIds.length(); j++) {
                                localMoreChildrenIds.add(childrenIds.getString(j));
                            }

                            Comment parentComment = findCommentByFullName(newComments, parentFullName);
                            if (parentComment != null) {
                                parentComment.setHasReply(true);
                                parentComment.setMoreChildrenIds(localMoreChildrenIds);
                                parentComment.addChildren(new ArrayList<>()); // ensure children list is not null
                            } else {
                                // assume that it is parent of this call
                                moreChildrenIds.addAll(localMoreChildrenIds);
                            }
                        } else {
                            Comment continueThreadPlaceholder = new Comment(
                                    parentFullName,
                                    childData.getInt(JSONUtils.DEPTH_KEY),
                                    Comment.PLACEHOLDER_CONTINUE_THREAD
                            );

                            Comment parentComment = findCommentByFullName(newComments, parentFullName);
                            if (parentComment != null) {
                                parentComment.setHasReply(true);
                                parentComment.addChild(continueThreadPlaceholder, parentComment.getChildCount());
                                parentComment.setChildCount(parentComment.getChildCount() + 1);
                            } else {
                                // assume that it is parent of this call
                                newComments.add(continueThreadPlaceholder);
                            }
                        }
                    } else {
                        Comment comment = parseSingleComment(childData, 0);
                        String parentFullName = comment.getParentId();

                        Comment parentComment = findCommentByFullName(newComments, parentFullName);
                        if (parentComment != null) {
                            parentComment.setHasReply(true);
                            parentComment.addChild(comment, parentComment.getChildCount());
                            parentComment.setChildCount(parentComment.getChildCount() + 1);
                        } else {
                            // assume that it is parent of this call
                            newComments.add(comment);
                        }
                    }
                }

                updateChildrenCount(newComments);
                expandChildren(newComments, expandedNewComments, expandChildren);

                ArrayList<Comment> commentData;
                if (expandChildren) {
                    commentData = expandedNewComments;
                } else {
                    commentData = newComments;
                }

                handler.post(() -> parseCommentListener.onParseCommentSuccess(newComments, commentData, null, moreChildrenIds));
            } catch (JSONException e) {
                e.printStackTrace();
                handler.post(parseCommentListener::onParseCommentFailed);
            }
        });
    }

    static void parseMoreCommentGQL(Executor executor, Handler handler, String response, boolean expandChildren, String authorName,
                                    ParseCommentListener parseCommentListener) {
        executor.execute(() -> {
            try {
                JSONObject data = new JSONObject(response).getJSONObject("data");

                String postId = data.getJSONObject("postInfoById").getString("id");
                String postType = data.getJSONObject("postInfoById").getString("__typename");

                String subredditName = "";
                if (postType.equals("ProfilePost")) {
                    subredditName= data.getJSONObject("postInfoById").getJSONObject("profile").getJSONObject("redditorInfo").getString("name");
                }else{
                    subredditName= data.getJSONObject("postInfoById").getJSONObject("subreddit").getString("name");
                }
                JSONArray childrenArray = data.getJSONObject("postInfoById").getJSONObject("commentForest").getJSONArray("trees");

                ArrayList<Comment> newComments = new ArrayList<>();
                ArrayList<Comment> expandedNewComments = new ArrayList<>();
                ArrayList<String> moreChildrenIds = new ArrayList<>();

                parseMoreCommentRecursionGQL(childrenArray, newComments, moreChildrenIds, postId, subredditName, authorName);

                updateChildrenCount(newComments);
                expandChildren(newComments, expandedNewComments, expandChildren);

                ArrayList<Comment> commentData;
                if (expandChildren) {
                    commentData = expandedNewComments;
                } else {
                    commentData = newComments;
                }

                handler.post(() -> parseCommentListener.onParseCommentSuccess(newComments, commentData, null, moreChildrenIds));
            } catch (JSONException e) {
                e.printStackTrace();
                handler.post(parseCommentListener::onParseCommentFailed);
            }
        });
    }

    static void parseSentComment(Executor executor, Handler handler, String response, int depth,
                                 ParseSentCommentListener parseSentCommentListener) {
        executor.execute(() -> {
            try {
                JSONObject sentCommentData = new JSONObject(response);
                Comment comment = parseSingleComment(sentCommentData, depth);

                handler.post(() -> parseSentCommentListener.onParseSentCommentSuccess(comment));
            } catch (JSONException e) {
                e.printStackTrace();
                String errorMessage = parseSentCommentErrorMessage(response);
                handler.post(() -> parseSentCommentListener.onParseSentCommentFailed(errorMessage));
            }
        });
    }

    private static void parseCommentRecursion(JSONArray comments, ArrayList<Comment> newCommentData,
                                              ArrayList<String> moreChildrenIds, int depth) throws JSONException {
        int actualCommentLength;

        if (comments.length() == 0) {
            return;
        }

        JSONObject more = comments.getJSONObject(comments.length() - 1).getJSONObject(JSONUtils.DATA_KEY);

        //Maybe moreChildrenIds contain only commentsJSONArray and no more info
        if (more.has(JSONUtils.COUNT_KEY)) {
            JSONArray childrenArray = more.getJSONArray(JSONUtils.CHILDREN_KEY);

            for (int i = 0; i < childrenArray.length(); i++) {
                moreChildrenIds.add(childrenArray.getString(i));
            }

            actualCommentLength = comments.length() - 1;

            if (moreChildrenIds.isEmpty() && comments.getJSONObject(comments.length() - 1).getString(JSONUtils.KIND_KEY).equals(JSONUtils.KIND_VALUE_MORE)) {
                newCommentData.add(new Comment(more.getString(JSONUtils.PARENT_ID_KEY), more.getInt(JSONUtils.DEPTH_KEY), Comment.PLACEHOLDER_CONTINUE_THREAD));
                return;
            }
        } else {
            actualCommentLength = comments.length();
        }

        for (int i = 0; i < actualCommentLength; i++) {
            JSONObject data = comments.getJSONObject(i).getJSONObject(JSONUtils.DATA_KEY);
            Comment singleComment = parseSingleComment(data, depth);

            if (data.get(JSONUtils.REPLIES_KEY) instanceof JSONObject) {
                JSONArray childrenArray = data.getJSONObject(JSONUtils.REPLIES_KEY)
                        .getJSONObject(JSONUtils.DATA_KEY).getJSONArray(JSONUtils.CHILDREN_KEY);
                ArrayList<Comment> children = new ArrayList<>();
                ArrayList<String> nextMoreChildrenIds = new ArrayList<>();
                parseCommentRecursion(childrenArray, children, nextMoreChildrenIds, singleComment.getDepth());
                singleComment.addChildren(children);
                singleComment.setMoreChildrenIds(nextMoreChildrenIds);
                singleComment.setChildCount(getChildCount(singleComment));
            }

            newCommentData.add(singleComment);
        }
    }

    private static void parseCommentRecursionGQL(JSONArray comments, ArrayList<Comment> newCommentData,
                                                 ArrayList<String> moreChildrenIds, String postId, String subredditName, String authorName) throws JSONException {
        int actualCommentLength;

        if (comments.length() == 0) {
            return;
        }

        // last child data object
        JSONObject last = comments.getJSONObject(comments.length() - 1);

        //Maybe moreChildrenIds contain only commentsJSONArray and no more info
        if (!last.isNull("more")) {

            moreChildrenIds.add(last.getJSONObject("more").getString("cursor"));
            actualCommentLength = comments.length() - 1;

            if (moreChildrenIds.isEmpty() && !comments.getJSONObject(comments.length() - 1).isNull("more")) {
                newCommentData.add(new Comment(last.getString("parentId"), last.getInt(JSONUtils.DEPTH_KEY), Comment.PLACEHOLDER_CONTINUE_THREAD));
                return;
            }
        } else {
            actualCommentLength = comments.length();
        }

        HashMap<String, Comment> commentMap = new HashMap<>();
        JSONObject lastDeleted = new JSONObject();

        for (int i = 0; i < actualCommentLength; i++) {
            JSONObject data = comments.getJSONObject(i);
            boolean isHiddenChild = data.isNull("node");
            boolean isVisibleChild = !data.isNull("parentId") && !data.isNull("node");

            if (isHiddenChild) {
                String parentId = data.getString("parentId");
                if (commentMap.get(parentId) == null) {
                    continue;
                }

                String cursor = data.getJSONObject("more").getString("cursor");
                commentMap.get(parentId).addMoreChildrenId(cursor);
            } else if (isVisibleChild) {
                String parentId = data.getString("parentId");
                if (!commentMap.containsKey(parentId)) {
                    Comment deletedComment = createDeletedComment(lastDeleted, parentId, postId, subredditName);
                    commentMap.put(parentId, deletedComment);
                    if (deletedComment.getDepth() > 0) {
                        commentMap.get(deletedComment.getParentId()).addChildEnd(deletedComment);
                    } else {
                        newCommentData.add(deletedComment);
                    }
                }

                if (data.getJSONObject("node").getString("__typename").equals("DeletedComment")) {
                    lastDeleted = data;
                    continue;
                }

                String id = data.getJSONObject("node").getString("id");

                Comment singleComment = parseSingleCommentGQL(data, postId, subredditName, authorName);
                commentMap.put(id, singleComment);
                commentMap.get(parentId).addChildEnd(singleComment);
            } else {
                boolean isDeleted = data.getJSONObject("node").getString("__typename").equals("DeletedComment");
                if (isDeleted) {
                    lastDeleted = data;
                    continue;
                }

                String id = data.getJSONObject("node").getString("id");

                Comment singleComment = parseSingleCommentGQL(data, postId, subredditName, authorName);
                commentMap.put(id, singleComment);
                newCommentData.add(singleComment);
            }
        }
    }

    private static void parseMoreCommentRecursionGQL(JSONArray comments, ArrayList<Comment> newCommentData,
                                                 ArrayList<String> moreChildrenIds, String postId, String subredditName, String authorName) throws JSONException {
        int actualCommentLength = comments.length();

        if (comments.length() == 0) {
            return;
        }

        // last child data object
        JSONObject last = comments.getJSONObject(comments.length() - 1);

        //Maybe moreChildrenIds contain only commentsJSONArray and no more info
        /*
        if (!last.isNull("more")) {

            moreChildrenIds.add(last.getJSONObject("more").getString("cursor"));
            actualCommentLength = comments.length() - 1;

            if (moreChildrenIds.isEmpty() && !comments.getJSONObject(comments.length() - 1).isNull("more")) {
                newCommentData.add(new Comment(last.getString("parentId"), last.getInt(JSONUtils.DEPTH_KEY), Comment.PLACEHOLDER_CONTINUE_THREAD));
                return;
            }
        } else {
            actualCommentLength = comments.length();
        }
        */

        HashMap<String, Comment> commentMap = new HashMap<>();
        JSONObject lastDeleted = new JSONObject();
        int topDepth = comments.getJSONObject(0).getInt("depth");

        for (int i = 0; i < actualCommentLength; i++) {
            JSONObject data = comments.getJSONObject(i);
            boolean isHiddenChild = data.isNull("node");
            boolean isVisibleChild = !data.isNull("parentId") && !data.isNull("node");

            if (isHiddenChild) {
                String parentId = data.getString("parentId");
                if (commentMap.get(parentId) == null) {
                    continue;
                }

                String cursor = data.getJSONObject("more").getString("cursor");
                commentMap.get(parentId).addMoreChildrenId(cursor);
            } else if (isVisibleChild) {
                String parentId = data.getString("parentId");
                if (topDepth < data.getInt("depth") && !commentMap.containsKey(parentId)) {
                    Comment deletedComment = createDeletedComment(lastDeleted, parentId, postId, subredditName);
                    commentMap.put(parentId, deletedComment);
                    if (deletedComment.getDepth() > topDepth) {
                        commentMap.get(deletedComment.getParentId()).addChildEnd(deletedComment);
                    } else {
                        newCommentData.add(deletedComment);
                    }
                }

                boolean isTopLevel = topDepth == data.getInt("depth");

                if (data.getJSONObject("node").getString("__typename").equals("DeletedComment")) {
                    lastDeleted = data;
                    continue;
                }

                String id = data.getJSONObject("node").getString("id");

                Comment singleComment = parseSingleCommentGQL(data, postId, subredditName, authorName);
                commentMap.put(id, singleComment);
                if(isTopLevel){
                    newCommentData.add(singleComment);
                }else{
                    commentMap.get(parentId).addChildEnd(singleComment);
                }
            } else {
                boolean isDeleted = data.getJSONObject("node").getString("__typename").equals("DeletedComment");
                if (isDeleted) {
                    lastDeleted = data;
                    continue;
                }

                String id = data.getJSONObject("node").getString("id");

                Comment singleComment = parseSingleCommentGQL(data, postId, subredditName, authorName);
                commentMap.put(id, singleComment);
                newCommentData.add(singleComment);
            }
        }
    }


    private static int getChildCount(Comment comment) {
        if (comment.getChildren() == null) {
            return 0;
        }
        int count = 0;
        for (Comment c : comment.getChildren()) {
            count += getChildCount(c);
        }
        return comment.getChildren().size() + count;
    }

    private static void expandChildren(ArrayList<Comment> comments, ArrayList<Comment> visibleComments,
                                       boolean setExpanded) {
        for (Comment c : comments) {
            visibleComments.add(c);
            if (c.hasReply()) {
                if (setExpanded) {
                    c.setExpanded(true);
                }
                expandChildren(c.getChildren(), visibleComments, setExpanded);
            } else {
                c.setExpanded(true);
            }
            if (c.hasMoreChildrenIds() && !c.getMoreChildrenIds().isEmpty()) {
                //Add a load more placeholder
                Comment placeholder = new Comment(c.getFullName(), c.getDepth() + 1, Comment.PLACEHOLDER_LOAD_MORE_COMMENTS);
                visibleComments.add(placeholder);
                c.addChild(placeholder, c.getChildren().size());
            }
        }
    }

    static Comment parseSingleComment(JSONObject singleCommentData, int depth) throws JSONException {
        String id = singleCommentData.getString(JSONUtils.ID_KEY);
        String fullName = singleCommentData.getString(JSONUtils.NAME_KEY);
        String author = singleCommentData.getString(JSONUtils.AUTHOR_KEY);
        StringBuilder authorFlairHTMLBuilder = new StringBuilder();
        if (singleCommentData.has(JSONUtils.AUTHOR_FLAIR_RICHTEXT_KEY)) {
            JSONArray flairArray = singleCommentData.getJSONArray(JSONUtils.AUTHOR_FLAIR_RICHTEXT_KEY);
            for (int i = 0; i < flairArray.length(); i++) {
                JSONObject flairObject = flairArray.getJSONObject(i);
                String e = flairObject.getString(JSONUtils.E_KEY);
                if (e.equals("text")) {
                    authorFlairHTMLBuilder.append(Html.escapeHtml(flairObject.getString(JSONUtils.T_KEY)));
                } else if (e.equals("emoji")) {
                    authorFlairHTMLBuilder.append("<img src=\"").append(Html.escapeHtml(flairObject.getString(JSONUtils.U_KEY))).append("\">");
                }
            }
        }
        String authorFlair = singleCommentData.isNull(JSONUtils.AUTHOR_FLAIR_TEXT_KEY) ? "" : singleCommentData.getString(JSONUtils.AUTHOR_FLAIR_TEXT_KEY);
        String linkAuthor = singleCommentData.has(JSONUtils.LINK_AUTHOR_KEY) ? singleCommentData.getString(JSONUtils.LINK_AUTHOR_KEY) : null;
        String linkId = singleCommentData.getString(JSONUtils.LINK_ID_KEY).substring(3);
        String subredditName = singleCommentData.getString(JSONUtils.SUBREDDIT_KEY);
        String parentId = singleCommentData.getString(JSONUtils.PARENT_ID_KEY);
        boolean isSubmitter = singleCommentData.getBoolean(JSONUtils.IS_SUBMITTER_KEY);
        String distinguished = singleCommentData.getString(JSONUtils.DISTINGUISHED_KEY);
        String commentMarkdown = "";
        if (!singleCommentData.isNull(JSONUtils.BODY_KEY)) {
            String body = singleCommentData.getString(JSONUtils.BODY_KEY);
            commentMarkdown = Utils.modifyMarkdown(Utils.trimTrailingWhitespace(body));
            if (!singleCommentData.isNull(JSONUtils.MEDIA_METADATA_KEY)) {
                JSONObject mediaMetadataObject = singleCommentData.getJSONObject(JSONUtils.MEDIA_METADATA_KEY);
                JSONObject expressionAssetData = null;
                if (!singleCommentData.isNull(JSONUtils.EXPRESSION_ASSET_KEY)) {
                    expressionAssetData = singleCommentData.getJSONObject(JSONUtils.EXPRESSION_ASSET_KEY);
                }
                commentMarkdown = Utils.inlineImages(commentMarkdown, mediaMetadataObject);
                commentMarkdown = Utils.parseInlineEmotesAndGifs(commentMarkdown, mediaMetadataObject, expressionAssetData);
            }
        }
        String commentRawText = Utils.trimTrailingWhitespace(
                Html.fromHtml(singleCommentData.getString(JSONUtils.BODY_HTML_KEY))).toString();
        String permalink = Html.fromHtml(singleCommentData.getString(JSONUtils.PERMALINK_KEY)).toString();
        StringBuilder awardingsBuilder = new StringBuilder();
        JSONArray awardingsArray = singleCommentData.getJSONArray(JSONUtils.ALL_AWARDINGS_KEY);
        for (int i = 0; i < awardingsArray.length(); i++) {
            JSONObject award = awardingsArray.getJSONObject(i);
            int count = award.getInt(JSONUtils.COUNT_KEY);
            JSONArray icons = award.getJSONArray(JSONUtils.RESIZED_ICONS_KEY);
            if (icons.length() > 4) {
                String iconUrl = icons.getJSONObject(3).getString(JSONUtils.URL_KEY);
                awardingsBuilder.append("<img src=\"").append(Html.escapeHtml(iconUrl)).append("\"> ").append("x").append(count).append(" ");
            } else if (icons.length() > 0) {
                String iconUrl = icons.getJSONObject(icons.length() - 1).getString(JSONUtils.URL_KEY);
                awardingsBuilder.append("<img src=\"").append(Html.escapeHtml(iconUrl)).append("\"> ").append("x").append(count).append(" ");
            }
        }
        int score = singleCommentData.getInt(JSONUtils.SCORE_KEY);
        int voteType;
        if (singleCommentData.isNull(JSONUtils.LIKES_KEY)) {
            voteType = VOTE_TYPE_NO_VOTE;
        } else {
            voteType = singleCommentData.getBoolean(JSONUtils.LIKES_KEY) ? VOTE_TYPE_UPVOTE : VOTE_TYPE_DOWNVOTE;
            score -= voteType;
        }
        long submitTime = singleCommentData.getLong(JSONUtils.CREATED_UTC_KEY) * 1000;
        boolean scoreHidden = singleCommentData.getBoolean(JSONUtils.SCORE_HIDDEN_KEY);
        boolean saved = singleCommentData.getBoolean(JSONUtils.SAVED_KEY);

        if (singleCommentData.has(JSONUtils.DEPTH_KEY)) {
            depth = singleCommentData.getInt(JSONUtils.DEPTH_KEY);
        }

        boolean collapsed = singleCommentData.getBoolean(JSONUtils.COLLAPSED_KEY);
        boolean hasReply = !(singleCommentData.get(JSONUtils.REPLIES_KEY) instanceof String);

        // this key can either be a bool (false) or a long (edited timestamp)
        long edited = singleCommentData.optLong(JSONUtils.EDITED_KEY) * 1000;

        return new Comment(id, fullName, author, authorFlair, authorFlairHTMLBuilder.toString(),
                linkAuthor, submitTime, commentMarkdown, commentRawText,
                linkId, subredditName, parentId, score, voteType, isSubmitter, distinguished,
                permalink, awardingsBuilder.toString(), depth, collapsed, hasReply, scoreHidden, saved, edited);
    }

    static Comment parseSingleCommentGQL(JSONObject singleCommentData, String postId, String subredditName, String authorName) throws JSONException {
        JSONObject node = singleCommentData.getJSONObject("node");

        boolean isRemoved = node.getBoolean("isRemoved");
        String id = node.getString(JSONUtils.ID_KEY).substring(3);
        String fullName = node.getString(JSONUtils.ID_KEY);
        String author = "[deleted]";
        String authorIconUrl = "";

        if (!node.isNull("authorInfo")) {
            JSONObject authorObj = node.getJSONObject("authorInfo");
            if (authorObj.getString("__typename").equals("UnavailableRedditor") || authorObj.getString("__typename").equals("DeletedRedditor")) {
                double r = Math.ceil(Math.random() * 7);
                authorIconUrl = String.format("https://www.redditstatic.com/avatars/defaults/v2/avatar_default_%d.png", (int) r);
            } else {
                authorIconUrl = authorObj.getJSONObject("iconSmall").getString("url");
            }
            author = authorObj.getString("name");
        }
        JSONObject authorFlairObj = node.isNull("authorFlair") ? null : node.getJSONObject("authorFlair");

        StringBuilder authorFlairHTMLBuilder = new StringBuilder();
        if (authorFlairObj != null) {
            if (!authorFlairObj.isNull("richtext")) {
                String flairArrayStr = authorFlairObj.getString("richtext");
                JSONArray flairArray = new JSONArray(flairArrayStr);
                for (int i = 0; i < flairArray.length(); i++) {
                    JSONObject flairObject = flairArray.getJSONObject(i);
                    String e = flairObject.getString(JSONUtils.E_KEY);
                    if (e.equals("text")) {
                        authorFlairHTMLBuilder.append(Html.escapeHtml(flairObject.getString(JSONUtils.T_KEY)));
                    } else if (e.equals("emoji")) {
                        authorFlairHTMLBuilder.append("<img src=\"").append(Html.escapeHtml(flairObject.getString(JSONUtils.U_KEY))).append("\">");
                    }
                }
            }

        }
        String authorFlair = authorFlairObj == null ? "" : authorFlairObj.getString("text");

        String linkAuthor = null;
        String linkId = postId.substring(3);
        String parentId = postId;
        if (!singleCommentData.isNull("parentId")) {
            parentId = singleCommentData.getString("parentId");
        }

        boolean isSubmitter = author.equals(authorName);
        String distinguished = node.isNull("distinguishedAs") ? null : node.getString("distinguishedAs").toLowerCase();
        String commentMarkdown = "";
        String commentRawText = "";
        if (!node.isNull("content")) {
            JSONObject content = node.getJSONObject("content");
            String body = content.getString("markdown");
            commentMarkdown = Utils.modifyMarkdown(Utils.trimTrailingWhitespace(body));
            if (!isRemoved) {
                JSONArray mediaMetadata = content.getJSONArray("richtextMedia");
                for (int i = 0; i < mediaMetadata.length(); i++) {
                    JSONObject mediaObj = mediaMetadata.getJSONObject(i);
                    String typename = mediaObj.getString("__typename");
                    if (typename.equals("ImageAsset")) {
                        String mediaId = mediaObj.getString("id");
                        String mediaUrl = mediaObj.getString("url");
                        commentMarkdown = commentMarkdown.replace(mediaId, mediaUrl);
                    } else if (typename.equals("ExpressionMediaAsset")) {
                        String mediaId = mediaObj.getString("id");
                        commentMarkdown = commentMarkdown.replace(String.format("![img](%s)", mediaId), "*This comment contains a Collectible Expression which are not available on old Reddit.*\n");
                    }
                }
            }

            commentRawText = Utils.trimTrailingWhitespace(
                    Html.fromHtml(content.getString("html"))).toString();
        }


        String permalink = Html.fromHtml(node.getString(JSONUtils.PERMALINK_KEY)).toString();
        StringBuilder awardingsBuilder = new StringBuilder();
        JSONArray awardingsArray = node.getJSONArray("awardings");
        for (int i = 0; i < awardingsArray.length(); i++) {
            JSONObject award = awardingsArray.getJSONObject(i);
            int count = award.getInt("total");
            String iconUrl = award.getJSONObject("award").getJSONObject("static_icon_24").getString(JSONUtils.URL_KEY);
            awardingsBuilder.append("<img src=\"").append(Html.escapeHtml(iconUrl)).append("\"> ").append("x").append(count).append(" ");
        }

        int score = node.getInt(JSONUtils.SCORE_KEY);
        int voteType;
        String voteState = node.getString("voteState");

        if (voteState.equals("NONE")) {
            voteType = VOTE_TYPE_NO_VOTE;
        } else {
            if (voteState.equals("UP")) {
                voteType = VOTE_TYPE_UPVOTE;
            } else {
                voteType = VOTE_TYPE_DOWNVOTE;
            }
            score -= voteType;
        }
        long submitTime = ParsePost.getUnixTime(node.getString("createdAt"));
        boolean scoreHidden = node.getBoolean("isScoreHidden");
        boolean saved = node.getBoolean("isSaved");

        int depth = singleCommentData.getInt(JSONUtils.DEPTH_KEY);

        boolean collapsed = node.getBoolean("isInitiallyCollapsed");
        boolean hasReply = singleCommentData.getInt("childCount") > 0;

        // this key can either be a bool (false) or a long (edited timestamp)
        long edited = 0;

        if (!node.isNull("editedAt")) {
            edited = ParsePost.getUnixTime(node.getString("editedAt"));
        }
        Comment newComment = new Comment(id, fullName, author, authorFlair, authorFlairHTMLBuilder.toString(),
                linkAuthor, submitTime, commentMarkdown, commentRawText,
                linkId, subredditName, parentId, score, voteType, isSubmitter, distinguished,
                permalink, awardingsBuilder.toString(), depth, collapsed, hasReply, scoreHidden, saved, edited);
        newComment.addChildren(new ArrayList<>());
        newComment.setAuthorIconUrl(authorIconUrl);
        return newComment;
    }

    private static Comment createDeletedComment(JSONObject data, String id, String postId, String subredditName) throws JSONException {
        String linkId = postId.substring(3);

        long createdAt = 0;
        int depth = 0;
        int childCount = 0;
        String parentId = data.isNull("parentId") ? postId : data.getString("parentId");
        createdAt = ParsePost.getUnixTime(data.getJSONObject("node").getString("createdAt"));
        depth = data.getInt("depth");
        childCount = data.getInt("childCount");

        Comment newComment = new Comment(id, "deleted", "[deleted]", "", "",
                null, createdAt, "[deleted]", "[deleted]",
                linkId, subredditName, parentId, 0, VOTE_TYPE_NO_VOTE, false, null,
                "", "", depth, true, childCount > 0, true, false, 0);
        newComment.addChildren(new ArrayList<>());
        newComment.setAuthorIconUrl("https://www.redditstatic.com/avatars/defaults/v2/avatar_default_1.png");

        return newComment;
    }

    @Nullable
    private static String parseSentCommentErrorMessage(String response) {
        try {
            JSONObject responseObject = new JSONObject(response).getJSONObject(JSONUtils.JSON_KEY);

            if (responseObject.getJSONArray(JSONUtils.ERRORS_KEY).length() != 0) {
                JSONArray error = responseObject.getJSONArray(JSONUtils.ERRORS_KEY)
                        .getJSONArray(responseObject.getJSONArray(JSONUtils.ERRORS_KEY).length() - 1);
                if (error.length() != 0) {
                    String errorString;
                    if (error.length() >= 2) {
                        errorString = error.getString(1);
                    } else {
                        errorString = error.getString(0);
                    }
                    return errorString.substring(0, 1).toUpperCase() + errorString.substring(1);
                } else {
                    return null;
                }
            } else {
                return null;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Nullable
    private static Comment findCommentByFullName(@NonNull List<Comment> comments, @NonNull String fullName) {
        for (Comment comment : comments) {
            if (comment.getFullName().equals(fullName) &&
                    comment.getPlaceholderType() == Comment.NOT_PLACEHOLDER) {
                return comment;
            }
            if (comment.getChildren() != null) {
                Comment result = findCommentByFullName(comment.getChildren(), fullName);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    private static void updateChildrenCount(@NonNull List<Comment> comments) {
        for (Comment comment : comments) {
            comment.setChildCount(getChildCount(comment));
            if (comment.getChildren() != null) {
                updateChildrenCount(comment.getChildren());
            }
        }
    }

    public interface ParseCommentListener {
        void onParseCommentSuccess(ArrayList<Comment> topLevelComments, ArrayList<Comment> expandedComments, String parentId,
                                   ArrayList<String> moreChildrenIds);

        void onParseCommentFailed();
    }

    interface ParseSentCommentListener {
        void onParseSentCommentSuccess(Comment comment);

        void onParseSentCommentFailed(@Nullable String errorMessage);
    }
}

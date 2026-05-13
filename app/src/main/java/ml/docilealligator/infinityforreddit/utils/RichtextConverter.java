package ml.docilealligator.infinityforreddit.utils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Converts Reddit's selftext_richtext JSON document format to Markdown.
 *
 * Reddit richtext document structure:
 * {
 *   "document": [
 *     { "e": "par", "c": [ { "e": "text", "t": "hello", "f": [[1,0,5]] }, ... ] },
 *     { "e": "h", "l": 1, "c": [ ... ] },
 *     { "e": "list", "o": false, "c": [ { "e": "li", "c": [ { "e": "par", "c": [...] } ] } ] },
 *     { "e": "blockquote", "c": [ ... ] },
 *     { "e": "code", "c": [ { "e": "raw", "t": "..." } ] },
 *     { "e": "hr" },
 *     { "e": "img", "id": "...", "c": [ { "e": "raw", "t": "caption" } ] },
 *     { "e": "table", "h": [ ... ], "c": [ ... ] }
 *   ]
 * }
 *
 * Inline element types:
 *   text  - plain text, "f" array describes formatting: [[flags, start, length], ...]
 *              flags: 1=bold, 2=italic, 8=strikethrough, 32=superscript, 64=inline-code, 256=spoiler
 *   link  - hyperlink, "u" = url, "t" = text (or "c" for children)
 *   br    - line break
 *   u/    - subreddit link, "t" = subreddit name
 *   spoilertext - spoiler, "c" = children
 */
public class RichtextConverter {

    public static String convert(JSONObject richtextDoc) {
        if (richtextDoc == null) return "";
        try {
            JSONArray document = richtextDoc.getJSONArray("document");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < document.length(); i++) {
                JSONObject block = document.getJSONObject(i);
                convertBlock(block, sb, 0, false);
                // Ensure separation between blocks
                if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') {
                    sb.append('\n');
                }
                sb.append('\n');
            }
            return sb.toString().trim();
        } catch (JSONException e) {
            return "";
        }
    }

    private static void convertBlock(JSONObject block, StringBuilder sb, int listDepth, boolean ordered) throws JSONException {
        String e = block.optString("e", "");
        switch (e) {
            case "par":
                convertInlineChildren(block.optJSONArray("c"), sb);
                break;

            case "h": {
                int level = block.optInt("l", 1);
                for (int i = 0; i < level; i++) sb.append('#');
                sb.append(' ');
                convertInlineChildren(block.optJSONArray("c"), sb);
                break;
            }

            case "list": {
                boolean isOrdered = block.optBoolean("o", false);
                JSONArray items = block.optJSONArray("c");
                if (items != null) {
                    int counter = 1;
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.getJSONObject(i);
                        // indent
                        for (int d = 0; d < listDepth; d++) sb.append("  ");
                        if (isOrdered) {
                            sb.append(counter++).append(". ");
                        } else {
                            sb.append("- ");
                        }
                        // li contains par children
                        JSONArray liChildren = item.optJSONArray("c");
                        if (liChildren != null) {
                            for (int j = 0; j < liChildren.length(); j++) {
                                JSONObject child = liChildren.getJSONObject(j);
                                String childE = child.optString("e", "");
                                if (childE.equals("list")) {
                                    sb.append('\n');
                                    convertBlock(child, sb, listDepth + 1, isOrdered);
                                } else if (childE.equals("par")) {
                                    convertInlineChildren(child.optJSONArray("c"), sb);
                                } else {
                                    convertBlock(child, sb, listDepth + 1, isOrdered);
                                }
                            }
                        }
                        sb.append('\n');
                    }
                }
                return; // already added newlines
            }

            case "blockquote": {
                StringBuilder inner = new StringBuilder();
                JSONArray children = block.optJSONArray("c");
                if (children != null) {
                    for (int i = 0; i < children.length(); i++) {
                        convertBlock(children.getJSONObject(i), inner, 0, false);
                        if (inner.length() > 0 && inner.charAt(inner.length() - 1) != '\n') {
                            inner.append('\n');
                        }
                    }
                }
                // Prefix each line with >
                String[] lines = inner.toString().split("\n", -1);
                for (String line : lines) {
                    sb.append("> ").append(line).append('\n');
                }
                return;
            }

            case "code": {
                sb.append("```\n");
                JSONArray children = block.optJSONArray("c");
                if (children != null) {
                    for (int i = 0; i < children.length(); i++) {
                        JSONObject child = children.getJSONObject(i);
                        sb.append(child.optString("t", ""));
                    }
                }
                sb.append("\n```");
                break;
            }

            case "hr":
                sb.append("---");
                break;

            case "img": {
                // Inline image - show as link to image
                String id = block.optString("id", "");
                String caption = "";
                JSONArray cap = block.optJSONArray("c");
                if (cap != null && cap.length() > 0) {
                    caption = cap.getJSONObject(0).optString("t", "");
                }
                if (!id.isEmpty()) {
                    String url = "https://i.redd.it/" + id;
                    sb.append("![").append(caption).append("](").append(url).append(")");
                }
                break;
            }

            case "table": {
                // Header row
                JSONArray headers = block.optJSONArray("h");
                if (headers != null) {
                    sb.append("| ");
                    for (int i = 0; i < headers.length(); i++) {
                        convertInlineChildren(headers.getJSONObject(i).optJSONArray("c"), sb);
                        sb.append(" | ");
                    }
                    sb.append('\n');
                    sb.append("| ");
                    for (int i = 0; i < headers.length(); i++) sb.append("--- | ");
                    sb.append('\n');
                }
                // Body rows
                JSONArray rows = block.optJSONArray("c");
                if (rows != null) {
                    for (int i = 0; i < rows.length(); i++) {
                        JSONArray cells = rows.getJSONObject(i).optJSONArray("c");
                        sb.append("| ");
                        if (cells != null) {
                            for (int j = 0; j < cells.length(); j++) {
                                convertInlineChildren(cells.getJSONObject(j).optJSONArray("c"), sb);
                                sb.append(" | ");
                            }
                        }
                        sb.append('\n');
                    }
                }
                return;
            }

            default:
                // Unknown block - try rendering children inline
                convertInlineChildren(block.optJSONArray("c"), sb);
                break;
        }
    }

    private static void convertInlineChildren(JSONArray children, StringBuilder sb) throws JSONException {
        if (children == null) return;
        for (int i = 0; i < children.length(); i++) {
            JSONObject child = children.getJSONObject(i);
            convertInline(child, sb);
        }
    }

    private static void convertInline(JSONObject node, StringBuilder sb) throws JSONException {
        String e = node.optString("e", "");
        switch (e) {
            case "text": {
                String text = node.optString("t", "");
                JSONArray formats = node.optJSONArray("f");
                if (formats == null || formats.length() == 0) {
                    sb.append(escapeMarkdown(text));
                } else {
                    // Apply formatting. Each format: [flags, start, length]
                    // flags: 1=bold, 2=italic, 8=strike, 32=super, 64=code, 256=spoiler
                    // For simplicity, apply the first format's flags to the whole text segment
                    // (multi-format ranges are rare in practice)
                    int flags = formats.getJSONArray(0).getInt(0);
                    String inner = escapeMarkdown(text);
                    if ((flags & 64) != 0) {
                        sb.append('`').append(text).append('`'); // inline code — no escaping
                    } else {
                        if ((flags & 256) != 0) inner = ">!" + inner + "!<"; // spoiler
                        if ((flags & 8) != 0) inner = "~~" + inner + "~~";
                        if ((flags & 2) != 0) inner = "*" + inner + "*";
                        if ((flags & 1) != 0) inner = "**" + inner + "**";
                        if ((flags & 32) != 0) inner = "^" + inner;
                        sb.append(inner);
                    }
                }
                break;
            }

            case "link": {
                String url = node.optString("u", "");
                String text = node.optString("t", null);
                if (text == null) {
                    // children provide the text
                    StringBuilder linkText = new StringBuilder();
                    convertInlineChildren(node.optJSONArray("c"), linkText);
                    text = linkText.toString();
                }
                if (text.isEmpty()) text = url;
                sb.append('[').append(text).append("](").append(url).append(')');
                break;
            }

            case "br":
                sb.append("  \n");
                break;

            case "u/": {
                String subreddit = node.optString("t", "");
                sb.append("[r/").append(subreddit).append("](https://www.reddit.com/r/").append(subreddit).append(")");
                break;
            }

            case "r/": {
                String subreddit = node.optString("t", "");
                sb.append("[r/").append(subreddit).append("](https://www.reddit.com/r/").append(subreddit).append(")");
                break;
            }

            case "spoilertext": {
                StringBuilder inner = new StringBuilder();
                convertInlineChildren(node.optJSONArray("c"), inner);
                sb.append(">!").append(inner).append("!<");
                break;
            }

            case "raw":
                sb.append(node.optString("t", ""));
                break;

            default:
                // Try text fallback
                String t = node.optString("t", null);
                if (t != null) sb.append(escapeMarkdown(t));
                else convertInlineChildren(node.optJSONArray("c"), sb);
                break;
        }
    }

    private static String escapeMarkdown(String text) {
        // Escape characters that would accidentally trigger Markdown formatting
        return text.replace("\\", "\\\\")
                .replace("`", "\\`")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("{", "\\{")
                .replace("}", "\\}")
                .replace("[", "\\[")
                .replace("]", "\\]")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("#", "\\#")
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace(".", "\\.")
                .replace("!", "\\!");
    }
}


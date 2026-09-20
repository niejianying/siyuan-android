/*
 * SiYuan - From thought to insight, with agents
 * Copyright (c) 2020-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.siyuan.webdav;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebDAV 路径与资产 id 编解码工具（纯函数，便于单测）。
 * 平移自 niejianying 参考实现 {@code webdav_path_utils.dart}。
 */
public final class WebDavPathUtils {

    private static final Pattern SLUG_KEEP = Pattern.compile("[^a-z0-9._\\u4e00-\\u9fff-]+");
    private static final Pattern SLUG_SLASH = Pattern.compile("[/\\\\]");
    private static final Pattern SLUG_DASHES = Pattern.compile("-+");
    private static final Pattern SLUG_EDGES = Pattern.compile("^-+|-+$");
    private static final Pattern ASSET_ID_PATTERN =
            Pattern.compile("__([A-Za-z0-9_-]+)(?:\\.[^.]+)?$");

    private WebDavPathUtils() {
    }

    /**
     * 将相册或文件名转为 slug。
     */
    public static String slugify(final String name) {
        String slug = name == null ? "" : name.toLowerCase();
        slug = SLUG_SLASH.matcher(slug).replaceAll("-");
        slug = SLUG_KEEP.matcher(slug).replaceAll("-");
        slug = SLUG_DASHES.matcher(slug).replaceAll("-");
        slug = SLUG_EDGES.matcher(slug).replaceAll("");
        if (slug.isEmpty()) {
            slug = "untitled";
        }
        return slug;
    }

    /**
     * 在已使用 slug 集合中生成唯一 slug：冲突追加 {@code -2}、{@code -3} …
     */
    public static String uniqueSlug(final String base, final java.util.Set<String> used) {
        String slug = slugify(base);
        if (used.add(slug)) {
            return slug;
        }
        int i = 2;
        while (used.contains(slug + "-" + i)) {
            i++;
        }
        final String unique = slug + "-" + i;
        used.add(unique);
        return unique;
    }

    /**
     * Base64 URL 编码（去填充），作为相册资产 id 编码方式。
     * 使用 {@link java.util.Base64}（Android API 26+ / desktop JVM 通用）。
     */
    public static String encodeAssetId(final String assetId) {
        return java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(assetId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public static String decodeAssetId(final String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            return null;
        }
        try {
            final String pad = "====".substring(0, (4 - encoded.length() % 4) % 4);
            final byte[] bytes = java.util.Base64.getUrlDecoder().decode(encoded + pad);
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        } catch (final Exception e) {
            return null;
        }
    }

    /**
     * 生成文件名：{@code <slug>__<encodedAssetId>.<ext>}。空 ext 省略。
     */
    public static String buildFileName(final String title, final String assetId, final String ext) {
        final String base = slugify(title == null || title.isEmpty() ? "file" : title);
        final String encodedId = encodeAssetId(assetId);
        String cleanExt = ext == null ? "" : ext;
        if (cleanExt.startsWith(".")) {
            cleanExt = cleanExt.substring(1);
        }
        cleanExt = cleanExt.toLowerCase();
        if (cleanExt.isEmpty()) {
            return base + "__" + encodedId;
        }
        return base + "__" + encodedId + "." + cleanExt;
    }

    public static String parseAssetIdFromFileName(final String fileName) {
        if (fileName == null) {
            return null;
        }
        final Matcher m = ASSET_ID_PATTERN.matcher(fileName);
        if (!m.find()) {
            return null;
        }
        return decodeAssetId(m.group(1));
    }

    /**
     * 规范化 DAV 相对路径：去首尾斜杠、折叠 {@code ..} 与 {@code .}。
     */
    public static String normalizeRelativePath(final String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        final String[] segments = path.split("/");
        final java.util.List<String> out = new java.util.ArrayList<>();
        for (final String seg : segments) {
            if (seg.isEmpty() || ".".equals(seg)) {
                continue;
            }
            if ("..".equals(seg)) {
                if (!out.isEmpty()) {
                    out.remove(out.size() - 1);
                }
                continue;
            }
            out.add(seg);
        }
        return join(out, "/");
    }

    public static String joinHref(final String base, final String segment) {
        final String b = base.endsWith("/") ? base : base + "/";
        final String s = segment.startsWith("/") ? segment.substring(1) : segment;
        return b + s;
    }

    public static String childSegment(final String href) {
        if (href.endsWith("/")) {
            return href;
        }
        final int slash = href.indexOf('/');
        if (slash < 0) {
            return href;
        }
        return href.substring(slash + 1);
    }

    public static String urlEncode(final String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (final UnsupportedEncodingException e) {
            return s;
        }
    }

    private static String join(final java.util.List<String> parts, final String sep) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                sb.append(sep);
            }
            sb.append(parts.get(i));
        }
        return sb.toString();
    }
}

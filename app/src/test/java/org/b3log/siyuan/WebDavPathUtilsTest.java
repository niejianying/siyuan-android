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
package org.b3log.siyuan;

import org.b3log.siyuan.webdav.WebDavPathUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * WebDavPathUtils 单元自测（plain main，对齐 KeyboardContentHeightTest）。
 */
public final class WebDavPathUtilsTest {

    public static void main(final String[] args) {
        check("slugify ascii", "hello-world", WebDavPathUtils.slugify("hello world"));
        check("slugify cn", "我的相册", WebDavPathUtils.slugify("我的相册"));
        check("slugify slash", "a-b", WebDavPathUtils.slugify("A/B"));
        check("slugify empty", "untitled", WebDavPathUtils.slugify(""));
        check("slugify trim dash", "x", WebDavPathUtils.slugify("-x-"));

        final Set<String> used = new HashSet<>();
        check("unique slug first", "camera", WebDavPathUtils.uniqueSlug("Camera", used));
        check("unique slug collide", "camera-2", WebDavPathUtils.uniqueSlug("Camera", used));
        check("unique slug again", "camera-3", WebDavPathUtils.uniqueSlug("Camera", used));
        check("unique slug new", "photos", WebDavPathUtils.uniqueSlug("Photos", used));

        check("encode/decode roundtrip", "abc/def 12",
                WebDavPathUtils.decodeAssetId(WebDavPathUtils.encodeAssetId("abc/def 12")));
        check("decode invalid returns null", null, WebDavPathUtils.decodeAssetId("@@@@"));

        check("buildFileName",
                "img_001__" + WebDavPathUtils.encodeAssetId("123") + ".jpg",
                WebDavPathUtils.buildFileName("IMG_001", "123", ".JPG"));
        check("buildFileName empty title",
                "file__" + WebDavPathUtils.encodeAssetId("42") + ".mp4",
                WebDavPathUtils.buildFileName("", "42", ".mp4"));
        check("buildFileName no ext",
                "shot__" + WebDavPathUtils.encodeAssetId("9"),
                WebDavPathUtils.buildFileName("shot", "9", ""));

        check("parseAssetId",
                WebDavPathUtils.decodeAssetId(WebDavPathUtils.encodeAssetId("99")),
                WebDavPathUtils.parseAssetIdFromFileName("shot__" + WebDavPathUtils.encodeAssetId("99") + ".jpg"));
        check("parseAssetId no ext",
                WebDavPathUtils.decodeAssetId(WebDavPathUtils.encodeAssetId("77")),
                WebDavPathUtils.parseAssetIdFromFileName("shot__" + WebDavPathUtils.encodeAssetId("77")));
        check("parseAssetId invalid", null, WebDavPathUtils.parseAssetIdFromFileName("no-id-file.jpg"));

        check("normalize empty", "", WebDavPathUtils.normalizeRelativePath(""));
        check("normalize simple", "a/b/c", WebDavPathUtils.normalizeRelativePath("a/b/c"));
        check("normalize leading slash", "a/b", WebDavPathUtils.normalizeRelativePath("/a/b"));
        check("normalize dotdot", "b", WebDavPathUtils.normalizeRelativePath("a/../b"));
        check("normalize dots", "a/b", WebDavPathUtils.normalizeRelativePath("a/./b"));
        check("normalize root dotdot", "a", WebDavPathUtils.normalizeRelativePath("../a"));

        check("joinHref", "http://ip:8080/dav/photos/album/file.jpg",
                WebDavPathUtils.joinHref("http://ip:8080/dav/photos/", "/album/file.jpg"));
        check("joinHref trailing", "http://ip:8080/dav/photos/",
                WebDavPathUtils.joinHref("http://ip:8080/dav", "photos/"));

        System.out.println("WebDavPathUtilsTest: all checks passed");
    }

    private static void check(final String name, final Object expected, final Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }

    private static void check(final String name, final Object expected, final Object actual, final Object... ignored) {
        check(name, expected, actual);
    }
}

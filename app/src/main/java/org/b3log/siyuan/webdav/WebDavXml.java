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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * RFC 4918 PROPFIND multistatus XML 与 RFC 1123 HTTP 日期格式化。
 * 平移自 niejianying 参考实现 {@code webdav_xml.dart}。
 */
public final class WebDavXml {

    private WebDavXml() {
    }

    public static String multistatus(final java.util.List<WebDavResource> resources) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        sb.append("<d:multistatus xmlns:d=\"DAV:\">");
        for (final WebDavResource r : resources) {
            sb.append(responseXml(r));
        }
        sb.append("</d:multistatus>");
        return sb.toString();
    }

    private static String responseXml(final WebDavResource r) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<d:response><d:href>");
        sb.append(xmlEscape(r.href));
        sb.append("</d:href><d:propstat><d:prop>");
        sb.append("<d:displayname>");
        sb.append(xmlEscape(r.name));
        sb.append("</d:displayname>");
        if (r.isCollection) {
            sb.append("<d:resourcetype><d:collection/></d:resourcetype>");
        } else {
            sb.append("<d:resourcetype/>");
        }
        if (!r.isCollection && r.lastModifiedMillis > 0) {
            sb.append("<d:getlastmodified>");
            sb.append(httpDate(r.lastModifiedMillis));
            sb.append("</d:getlastmodified>");
        }
        if (!r.isCollection && r.contentLength >= 0) {
            sb.append("<d:getcontentlength>");
            sb.append(r.contentLength);
            sb.append("</d:getcontentlength>");
        }
        if (!r.isCollection && r.mimeType != null) {
            sb.append("<d:getcontenttype>");
            sb.append(xmlEscape(r.mimeType));
            sb.append("</d:getcontenttype>");
        }
        if (!r.isCollection && r.etag != null) {
            sb.append("<d:getetag>&quot;");
            sb.append(xmlEscape(r.etag));
            sb.append("&quot;</d:getetag>");
        }
        sb.append("</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>");
        return sb.toString();
    }

    /**
     * 输出 RFC 1123 / 7231 HTTP 日期（GMT）。
     */
    public static String httpDate(final long millis) {
        // Fixed-format GMT; 不依赖设备时区，确保远端客户端时区无关。
        final SimpleDateFormat fmt = new SimpleDateFormat(
                "EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        return fmt.format(new Date(millis));
    }

    public static String xmlEscape(final String s) {
        if (s == null) {
            return "";
        }
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }
}

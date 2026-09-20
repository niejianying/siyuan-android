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

/**
 * WebDAV 虚拟资源模型。映射 Flutter 参考实现中的 {@code WebDavResource}。
 */
public final class WebDavResource {

    public final String name;
    public final String href;
    public final boolean isCollection;
    public final long contentLength;
    public final long lastModifiedMillis;
    public final String mimeType;
    public final String etag;

    WebDavResource(final String name, final String href, final boolean isCollection,
                   final long contentLength, final long lastModifiedMillis,
                   final String mimeType, final String etag) {
        this.name = name;
        this.href = href;
        this.isCollection = isCollection;
        this.contentLength = contentLength;
        this.lastModifiedMillis = lastModifiedMillis;
        this.mimeType = mimeType;
        this.etag = etag;
    }

    public static WebDavResource collection(final String name, final String href) {
        return new WebDavResource(name, href, true, -1, -1, null, null);
    }

    public static WebDavResource file(final String name, final String href, final long length,
                                      final long lastModifiedMillis, final String mime, final String etag) {
        return new WebDavResource(name, href, false, length, lastModifiedMillis, mime, etag);
    }
}

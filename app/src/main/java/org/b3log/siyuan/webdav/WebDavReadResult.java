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

import java.io.InputStream;

/**
 * 可读媒体流结果，供 GET/HEAD 使用。
 */
public final class WebDavReadResult {

    public final InputStream stream;
    public final long length;
    public final String mimeType;
    public final long lastModifiedMillis;
    public final String etag;

    public WebDavReadResult(final InputStream stream, final long length, final String mimeType,
                            final long lastModifiedMillis, final String etag) {
        this.stream = stream;
        this.length = length;
        this.mimeType = mimeType;
        this.lastModifiedMillis = lastModifiedMillis;
        this.etag = etag;
    }
}

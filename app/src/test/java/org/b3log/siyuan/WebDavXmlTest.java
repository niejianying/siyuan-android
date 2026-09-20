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

import org.b3log.siyuan.webdav.WebDavResource;
import org.b3log.siyuan.webdav.WebDavXml;

import java.util.Arrays;

/**
 * WebDavXml 单元自测（plain main）。
 */
public final class WebDavXmlTest {

    public static void main(final String[] args) {
        checkXmlEscaping();
        checkMultistatus();
        checkHttpDate();
        System.out.println("WebDavXmlTest: all checks passed");
    }

    private static void checkXmlEscaping() {
        check("escape &", "&amp;", WebDavXml.xmlEscape("&"));
        check("escape <", "&lt;", WebDavXml.xmlEscape("<"));
        check("escape >", "&gt;", WebDavXml.xmlEscape(">"));
        check("escape \"", "&quot;", WebDavXml.xmlEscape("\""));
        check("escape mixed", "a&amp;b&lt;c", WebDavXml.xmlEscape("a&b<c"));
        check("escape null", "", WebDavXml.xmlEscape(null));
    }

    private static void checkMultistatus() {
        final WebDavResource collection = WebDavResource.collection("photos", "/dav/photos/");
        final String xml = WebDavXml.multistatus(Arrays.asList(collection));
        check("contains root", true, xml.contains("<d:multistatus"));
        check("contains href", true, xml.contains("<d:href>/dav/photos/</d:href>"));
        check("contains displayname", true, xml.contains("<d:displayname>photos</d:displayname>"));
        check("contains collection resourcetype", true,
                xml.contains("<d:resourcetype><d:collection/></d:resourcetype>"));
        check("contains OK", true, xml.contains("HTTP/1.1 200 OK"));
    }

    private static void checkHttpDate() {
        // 2024-01-02 03:04:05 UTC
        final String date = WebDavXml.httpDate(1704164645000L);
        check("date non-empty", true, !date.isEmpty());
        check("date ends GMT", true, date.endsWith(" GMT"));
        System.out.println("date sample: " + date);
    }

    private static void check(final String name, final Object expected, final Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(name + ": expected " + expected + ", got " + actual);
        }
    }
}

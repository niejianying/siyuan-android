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

import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;
import com.koushikdutta.async.http.server.HttpServerRequestCallback;

import org.json.JSONObject;

import java.util.List;

/**
 * 路由分派到根、{@code /api/status} 与 {@code /dav/}。
 * 实际 WebDAV 协议逻辑由 {@link WebDavServer} 的注册表承载，
 * 此处只负责按路径分发与方法分派。
 */
public final class WebDavHandler implements HttpServerRequestCallback {

    public static final String DAV_BASE = "/dav";

    @Override
    public void onRequest(final AsyncHttpServerRequest request,
                          final AsyncHttpServerResponse response) {
        try {
            final String path = request.getPath() == null ? "/" : request.getPath();
            final String method = request.getMethod() == null ? "GET" : request.getMethod().toUpperCase();
            final WebDavServer server = WebDavManager.current();
            if (server == null) {
                response.code(503);
                response.send("text/plain", "WebDAV service not initialized");
                return;
            }
            // 根与 /api/status：返回 JSON 状态
            if ("GET".equals(method) || "HEAD".equals(method)) {
                if ("/".equals(path) || "/api/status".equals(path)) {
                    sendStatusJson(response, server, "HEAD".equals(method));
                    return;
                }
            }
            // OPTIONS 任意路径
            if ("OPTIONS".equals(method)) {
                sendOptions(response);
                return;
            }
            // /dav 与 /dav/* 走 WebDAV
            if (path.equals(DAV_BASE) || path.startsWith(DAV_BASE + "/")) {
                handleDav(request, response, method, path);
                return;
            }
            // 其余未匹配
            response.code(404);
            response.send("text/plain", "Not Found");
        } catch (final Throwable t) {
            response.code(500);
            response.send("text/plain", "WebDAV internal error: " + t.getClass().getSimpleName());
        }
    }

    private void handleDav(final AsyncHttpServerRequest request,
                           final AsyncHttpServerResponse response,
                           final String method,
                           final String path) throws Exception {
        final WebDavServer server = WebDavManager.current();
        final WebDavRegistry registry = server.registry();
        final String davBase = DAV_BASE;
        switch (method) {
            case "OPTIONS":
                sendOptions(response);
                return;
            case "PROPFIND":
                handlePropfind(response, registry, path, davBase);
                return;
            case "GET":
                handleGet(response, registry, path, davBase, false);
                return;
            case "HEAD":
                handleGet(response, registry, path, davBase, true);
                return;
            case "LOCK":
                sendLock(response);
                return;
            case "UNLOCK":
                response.code(204);
                response.end();
                return;
            default:
                response.code(405);
                response.send("text/plain", "Method not allowed");
        }
    }

    private void handlePropfind(final AsyncHttpServerResponse response,
                                 final WebDavRegistry registry,
                                 final String path,
                                 final String davBase) throws Exception {
        final String depthHeader = "1";
        final String relative = relativeFromDavPath(path);
        final List<WebDavResource> resources;
        try {
            if ("0".equals(depthHeader)) {
                final WebDavResource self = registry.resolve(relative, davBase);
                if (self == null) {
                    response.code(404);
                    response.send("text/plain", "Not Found");
                    return;
                }
                resources = java.util.Collections.singletonList(self);
            } else if ("infinity".equals(depthHeader)) {
                final List<WebDavResource> collected = registry.collectRecursive(
                        relative, davBase, 3, 0);
                if (collected.isEmpty()) {
                    response.code(404);
                    response.send("text/plain", "Not Found");
                    return;
                }
                resources = collected;
            } else {
                final WebDavResource self = registry.resolve(relative, davBase);
                if (self == null) {
                    response.code(404);
                    response.send("text/plain", "Not Found");
                    return;
                }
                final List<WebDavResource> children = registry.listChildren(relative, davBase);
                final java.util.List<WebDavResource> all = new java.util.ArrayList<>(children.size() + 1);
                all.add(self);
                all.addAll(children);
                resources = all;
            }
        } catch (final Exception e) {
            response.code(500);
            response.send("text/plain", "PROPFIND failed: " + e.getMessage());
            return;
        }
        final String xml = WebDavXml.multistatus(resources);
        response.code(207);
        response.getHeaders().set("Content-Type", "application/xml; charset=utf-8");
        response.send("application/xml; charset=utf-8", xml);
    }

    private void handleGet(final AsyncHttpServerResponse response,
                            final WebDavRegistry registry,
                            final String path,
                            final String davBase,
                            final boolean headOnly) throws Exception {
        final String relative = relativeFromDavPath(path);
        final WebDavResource resource = registry.resolve(relative, davBase);
        if (resource == null) {
            response.code(404);
            response.send("text/plain", "Not Found");
            return;
        }
        if (resource.isCollection) {
            // 浏览器 GET 集合：渲染简易 HTML 列表
            final List<WebDavResource> children = registry.listChildren(relative, davBase);
            final StringBuilder sb = new StringBuilder();
            sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>")
                    .append(escapeHtml(resource.name))
                    .append("</title></head><body><h1>")
                    .append(escapeHtml(resource.name))
                    .append("</h1><p>WebDAV 根目录。Finder / 资源管理器请使用 WebDAV 协议挂载；浏览器可直接点击下方链接浏览。</p><ul>");
            for (final WebDavResource c : children) {
                final String safeName = escapeHtml(c.name);
                final String safeHref = escapeHtml(c.href);
                sb.append("<li><a href=\"").append(safeHref).append("\">").append(safeName).append("</a></li>");
            }
            sb.append("</ul></body></html>");
            response.code(200);
            response.getHeaders().set("Content-Type", "text/html; charset=utf-8");
            if (headOnly) {
                response.end();
            } else {
                response.send("text/html; charset=utf-8", sb.toString());
            }
            return;
        }
        final WebDavReadResult opened = registry.openRead(relative);
        if (opened == null) {
            response.code(404);
            response.send("text/plain", "Not Found");
            return;
        }
        final com.koushikdutta.async.http.Headers headers = response.getHeaders();
        if (opened.mimeType != null) {
            headers.set("Content-Type", opened.mimeType);
        }
        if (opened.length >= 0) {
            headers.set("Content-Length", String.valueOf(opened.length));
        }
        headers.set("Accept-Ranges", "none");
        if (opened.lastModifiedMillis > 0) {
            headers.set("Last-Modified", WebDavXml.httpDate(opened.lastModifiedMillis));
        }
        if (opened.etag != null) {
            headers.set("ETag", "\"" + opened.etag + "\"");
        }
        if (headOnly) {
            response.code(200);
            response.end();
            return;
        }
        response.code(200);
        response.sendStream(opened.stream, opened.length);
    }

    private void sendOptions(final AsyncHttpServerResponse response) {
        response.code(200);
        final com.koushikdutta.async.http.Headers headers = response.getHeaders();
        headers.set("DAV", "1, 2");
        headers.set("Allow", "OPTIONS, GET, HEAD, PROPFIND, LOCK, UNLOCK");
        headers.set("MS-Author-Via", "DAV");
        response.end();
    }

    private void sendLock(final AsyncHttpServerResponse response) {
        final String token = "opaquelocktoken:readonly";
        final String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<D:prop xmlns:D=\"DAV:\">\n"
                + "  <D:lockdiscovery>\n"
                + "    <D:activelock>\n"
                + "      <D:locktype><D:write/></D:locktype>\n"
                + "      <D:lockscope><D:exclusive/></D:lockscope>\n"
                + "      <D:depth>0</D:depth>\n"
                + "      <D:timeout>Second-3600</D:timeout>\n"
                + "      <D:locktoken><D:href>" + token + "</D:href></D:locktoken>\n"
                + "    </D:activelock>\n"
                + "  </D:lockdiscovery>\n"
                + "</D:prop>";
        response.code(200);
        response.getHeaders().set("Content-Type", "application/xml; charset=utf-8");
        response.getHeaders().set("Lock-Token", "<" + token + ">");
        response.send("application/xml; charset=utf-8", body);
    }

    private void sendStatusJson(final AsyncHttpServerResponse response, final WebDavServer server,
                                 final boolean headOnly) {
        try {
            final JSONObject data = new JSONObject();
            data.put("status", server.status().name());
            data.put("port", server.port());
            data.put("localIp", server.localIp() == null ? "" : server.localIp());
            data.put("hostname", server.hostname());
            data.put("webdavUrl", server.webdavUrl() == null ? "" : server.webdavUrl());
            data.put("timestamp", System.currentTimeMillis());
            final JSONObject payload = new JSONObject();
            payload.put("code", 0);
            payload.put("msg", "");
            payload.put("data", data);
            final String json = payload.toString();
            response.code(200);
            response.getHeaders().set("Content-Type", "application/json; charset=utf-8");
            response.getHeaders().set("Content-Length", String.valueOf(json.length()));
            if (headOnly) {
                response.end();
            } else {
                response.send("application/json; charset=utf-8", json);
            }
        } catch (final Exception e) {
            response.code(500);
            response.send("text/plain", "status failed: " + e.getMessage());
        }
    }

    private static String relativeFromDavPath(final String path) {
        if (path == null) {
            return "";
        }
        String p = path;
        if (p.startsWith(DAV_BASE + "/")) {
            p = p.substring(DAV_BASE.length() + 1);
        } else if (p.equals(DAV_BASE)) {
            return "";
        }
        return WebDavPathUtils.normalizeRelativePath(p);
    }

    private static String escapeHtml(final String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}

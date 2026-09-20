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

import android.util.Log;

import com.koushikdutta.async.AsyncServer;
import com.koushikdutta.async.http.server.AsyncHttpServer;
import com.koushikdutta.async.http.server.AsyncHttpServerRequest;
import com.koushikdutta.async.http.server.AsyncHttpServerResponse;

import org.b3log.siyuan.Utils;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

/**
 * 本机 HTTP 服务状态（相册局域网 WebDAV）。
 * 平移自 niejianying 参考实现 {@code note_web_server_service.dart}。
 */
enum WebDavStatus {
    stopped, starting, running, stopping, error
}

/**
 * 局域网 WebDAV 文件服务。
 *
 * <p>挂载 {@code /dav/photos/} 只读相册；其它内置端点 {@code /api/status} 与根 {@code /}。
 *
 * @author <a href="https://b3log.org">b3log.org</a>
 */
public final class WebDavServer {

    private static final String TAG = "WebDavServer";

    public static final String PREF_ENABLED = "webdav_enabled";

    private static final int DEFAULT_PORT = 8080;
    private static final int PORT_PROBE_ATTEMPTS = 10;
    private static final String DAV_BASE = "/dav";

    private final WebDavRegistry registry;
    private final WebDavHandler handler;

    private AsyncHttpServer server;
    private AsyncServer asyncServer;
    private WebDavStatus status = WebDavStatus.stopped;
    private int port = DEFAULT_PORT;
    private String localIp;
    private String hostname;

    public WebDavServer(final android.content.Context context) {
        this.handler = new WebDavHandler();
        this.registry = new WebDavRegistry(Collections.<WebDavBackend>singletonList(
                new PhotosWebDavBackend(context)));
    }

    public WebDavStatus status() {
        return status;
    }

    public int port() {
        return port;
    }

    public String localIp() {
        return localIp;
    }

    public String hostname() {
        return hostname == null || hostname.isEmpty() ? android.os.Build.MODEL : hostname;
    }

    public String webdavUrl() {
        if (localIp == null || status != WebDavStatus.running) {
            return null;
        }
        return "http://" + localIp + ":" + port + DAV_BASE + "/";
    }

    /**
     * 启动服务，端口从 8080 探测至 8089。
     */
    public synchronized boolean start() {
        if (status == WebDavStatus.running || status == WebDavStatus.starting) {
            return true;
        }
        setStatus(WebDavStatus.starting);
        try {
            detectLocalIp();
            server = new AsyncHttpServer();
            registerRoutes();
            int boundPort = -1;
            for (int attempt = 0; attempt < PORT_PROBE_ATTEMPTS; attempt++) {
                final int candidate = DEFAULT_PORT + attempt;
                if (!isPortFree(candidate)) {
                    continue;
                }
                try {
                    asyncServer = new AsyncServer();
                    asyncServer.listen(null, candidate, server.getListenCallback());
                    boundPort = candidate;
                    break;
                } catch (final Throwable t) {
                    Log.w(TAG, "bind port " + candidate + " failed: " + t.getMessage());
                }
            }
            if (boundPort < 0) {
                throw new RuntimeException("no available port in [" + DEFAULT_PORT + ","
                        + (DEFAULT_PORT + PORT_PROBE_ATTEMPTS - 1) + "]");
            }
            port = boundPort;
            setStatus(WebDavStatus.running);
            Log.i(TAG, "WebDAV started at http://" + localIp + ":" + port + DAV_BASE + "/");
            return true;
        } catch (final Throwable e) {
            Log.e(TAG, "start WebDAV failed: " + e.getMessage(), e);
            cleanup();
            setStatus(WebDavStatus.error);
            return false;
        }
    }

    /**
     * 停止服务。
     */
    public synchronized boolean stop() {
        if (status == WebDavStatus.stopped) {
            return true;
        }
        setStatus(WebDavStatus.stopping);
        cleanup();
        setStatus(WebDavStatus.stopped);
        return true;
    }

    public void restart() {
        stop();
        start();
    }

    /**
     * 渲染当前状态 JSON 字符串。
     */
    public String statusJson() {
        final org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("status", status.name());
            json.put("port", port);
            json.put("localIp", localIp == null ? "" : localIp);
            json.put("hostname", hostname());
            json.put("webdavUrl", webdavUrl() == null ? "" : webdavUrl());
            json.put("timestamp", System.currentTimeMillis());
        } catch (final org.json.JSONException e) {
            // JSONObject.put should not throw for these types; fall back to minimal.
            return "{\"status\":\"" + status.name() + "\"}";
        }
        return json.toString();
    }

    private void registerRoutes() {
        // OPTIONS 路由到根与 /dav（OPTIONS 不带 dav 前缀的也要回应）
        server.addAction("OPTIONS", ".*", handler);
        server.addAction("PROPFIND", ".*", handler);
        server.addAction("GET", ".*", handler);
        server.addAction("HEAD", ".*", handler);
        server.addAction("LOCK", ".*", handler);
        server.addAction("UNLOCK", ".*", handler);
        // 写方法一律 403
        server.addAction("PUT", ".*", writeMethodHandler);
        server.addAction("DELETE", ".*", writeMethodHandler);
        server.addAction("MKCOL", ".*", writeMethodHandler);
        server.addAction("MOVE", ".*", writeMethodHandler);
        server.addAction("COPY", ".*", writeMethodHandler);
        server.addAction("PROPPATCH", ".*", writeMethodHandler);
    }

    private final com.koushikdutta.async.http.server.HttpServerRequestCallback writeMethodHandler =
            new com.koushikdutta.async.http.server.HttpServerRequestCallback() {
                @Override
                public void onRequest(final AsyncHttpServerRequest request,
                                      final AsyncHttpServerResponse response) {
                    response.code(403);
                    response.getHeaders().set("Content-Type", "text/plain; charset=utf-8");
                    response.send("Read-only WebDAV");
                }
            };

    private void detectLocalIp() {
        try {
            final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                final NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback() || iface.isVirtual()) {
                    continue;
                }
                final Enumeration<java.net.InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    final java.net.InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        localIp = addr.getHostAddress();
                        return;
                    }
                }
            }
        } catch (final Exception e) {
            Log.w(TAG, "detect local IP failed: " + e.getMessage());
        }
        // 兜底：使用 Utils 提供的逗号分隔列表首个非回环 IPv4
        try {
            final android.content.Context ctx = WebDavManager.appContext();
            if (ctx != null) {
                final String list = Utils.getLANIPAddressList(ctx);
                if (list != null && !list.isEmpty()) {
                    for (final String ip : list.split(",")) {
                        final String trimmed = ip.trim();
                        if (!trimmed.isEmpty() && !"127.0.0.1".equals(trimmed)) {
                            localIp = trimmed;
                            return;
                        }
                    }
                }
            }
        } catch (final Throwable ignored) {
        }
        if (localIp == null) {
            localIp = "127.0.0.1";
        }
    }

    private void setStatus(final WebDavStatus s) {
        status = s;
    }

    private void cleanup() {
        try {
            if (server != null) {
                server.stop();
            }
        } catch (final Throwable ignored) {
        }
        server = null;
        try {
            if (asyncServer != null) {
                asyncServer.stop();
            }
        } catch (final Throwable ignored) {
        }
        asyncServer = null;
    }

    private static boolean isPortFree(final int port) {
        java.net.ServerSocket probe = null;
        try {
            probe = new java.net.ServerSocket(port);
            return true;
        } catch (final Exception e) {
            return false;
        } finally {
            if (probe != null) {
                try {
                    probe.close();
                } catch (final Exception ignored) {
                }
            }
        }
    }

    public WebDavRegistry registry() {
        return registry;
    }
}

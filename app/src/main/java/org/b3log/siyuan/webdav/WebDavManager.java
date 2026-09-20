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

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 进程内 {@link WebDavServer} 持有者；同时封装开关偏好持久化。
 * 单进程壳内访问，活动/桥透传同一实例。
 */
public final class WebDavManager {

    private static volatile WebDavServer server;
    private static volatile SharedPreferences prefs;
    private static volatile Context appContext;

    private WebDavManager() {
    }

    /**
     * 在 {@link android.app.Application#onCreate} 或首次访问时初始化。
     */
    public static synchronized void initialize(final Context context) {
        if (appContext == null) {
            appContext = context.getApplicationContext();
            prefs = appContext.getSharedPreferences("webdav", Context.MODE_PRIVATE);
        }
    }

    /**
     * 返回当前活跃的 {@link WebDavServer}，未初始化时返回 {@code null}。
     */
    public static WebDavServer current() {
        if (server != null) {
            return server;
        }
        if (appContext == null) {
            return null;
        }
        synchronized (WebDavManager.class) {
            if (server == null) {
                server = new WebDavServer(appContext);
            }
        }
        return server;
    }

    public static Context appContext() {
        return appContext;
    }

    /**
     * 由 UI 流程（Activity 回调）启动服务。
     */
    public static boolean start() {
        ensureInitialized();
        final WebDavServer s = current();
        if (s == null) {
            return false;
        }
        final boolean ok = s.start();
        if (ok) {
            prefs.edit().putBoolean(WebDavServer.PREF_ENABLED, true).apply();
        }
        return ok;
    }

    /**
     * 由 UI 流程停止服务。
     */
    public static boolean stop() {
        ensureInitialized();
        final WebDavServer s = current();
        if (s == null) {
            return false;
        }
        final boolean ok = s.stop();
        if (ok) {
            prefs.edit().putBoolean(WebDavServer.PREF_ENABLED, false).apply();
        }
        return ok;
    }

    /**
     * 启动时调用：若偏好开启则返回 true（实际启动由 Activity 决定；
     * 因权限校验需要 Activity，所以这里只同步偏好值）。
     */
    public static boolean restoreIfEnabled() {
        ensureInitialized();
        if (prefs.getBoolean(WebDavServer.PREF_ENABLED, false)) {
            return start();
        }
        return false;
    }

    /**
     * 释放资源；{@link android.app.Activity#onDestroy} 调用。
     */
    public static void shutdown() {
        synchronized (WebDavManager.class) {
            if (server != null) {
                server.stop();
                server = null;
            }
        }
    }

    public static String statusJson() {
        final WebDavServer s = current();
        return s == null ? "{\"status\":\"stopped\"}" : s.statusJson();
    }

    public static boolean isEnabledPref() {
        ensureInitialized();
        return prefs.getBoolean(WebDavServer.PREF_ENABLED, false);
    }

    private static void ensureInitialized() {
        if (appContext == null) {
            throw new IllegalStateException("WebDavManager.initialize() not called");
        }
    }
}

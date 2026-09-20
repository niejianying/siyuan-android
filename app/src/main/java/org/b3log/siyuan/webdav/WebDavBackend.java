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

import java.util.List;

/**
 * WebDAV 虚拟后端接口；每个后端挂载于 {@code /dav{mountPath}/} 下。
 * 平移自 niejianying 参考实现 {@code webdav_backend.dart}。
 */
public interface WebDavBackend {

    /**
     * 挂载路径，如 {@code /photos}（不含 {@code /dav} 前缀）。
     */
    String mountPath();

    /**
     * 后端根集合的显示名。
     */
    String displayName();

    /**
     * 解析相对路径（相对 mount 根，如 {@code album/file.jpg}）。
     */
    WebDavResource resolve(String relativePath) throws Exception;

    /**
     * 列出目录子项；{@code relativePath} 为空表示 mount 根。
     */
    List<WebDavResource> listChildren(String relativePath) throws Exception;

    /**
     * 打开文件流；非文件路径返回 {@code null}。
     */
    WebDavReadResult openRead(String relativePath) throws Exception;
}

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

import java.util.ArrayList;
import java.util.List;

/**
 * 将多个 {@link WebDavBackend} 聚合为统一虚拟根 {@code /dav/}。
 * 平移自 niejianying 参考实现 {@code webdav_registry.dart}。
 */
public final class WebDavRegistry {

    private final List<WebDavBackend> backends;

    public WebDavRegistry(final List<WebDavBackend> backends) {
        this.backends = backends;
    }

    public List<WebDavBackend> backends() {
        return java.util.Collections.unmodifiableList(backends);
    }

    private WebDavBackend backendForPath(final String relativePath) {
        final String normalized = WebDavPathUtils.normalizeRelativePath(relativePath);
        for (final WebDavBackend b : backends) {
            final String mount = b.mountPath().replaceFirst("^/", "");
            if (normalized.equals(mount) || normalized.startsWith(mount + "/")) {
                return b;
            }
        }
        return null;
    }

    private String stripMount(final WebDavBackend backend, final String relativePath) {
        final String normalized = WebDavPathUtils.normalizeRelativePath(relativePath);
        final String mount = backend.mountPath().replaceFirst("^/", "");
        if (normalized.equals(mount)) {
            return "";
        }
        if (normalized.startsWith(mount + "/")) {
            return normalized.substring(mount.length() + 1);
        }
        return normalized;
    }

    /**
     * 根集合：列出各后端挂载点。
     */
    public List<WebDavResource> listRootMounts(final String davBaseHref) {
        final String base = davBaseHref.endsWith("/") ? davBaseHref : davBaseHref + "/";
        final List<WebDavResource> out = new ArrayList<>();
        for (final WebDavBackend b : backends) {
            final String segment = b.mountPath().replaceFirst("^/", "");
            out.add(WebDavResource.collection(
                    b.displayName(),
                    WebDavPathUtils.joinHref(base, segment + "/")));
        }
        return out;
    }

    /**
     * 解析相对路径对应的资源。
     */
    public WebDavResource resolve(final String relativePath, final String davBaseHref) throws Exception {
        final String normalized = WebDavPathUtils.normalizeRelativePath(relativePath);
        if (normalized.isEmpty()) {
            final String base = davBaseHref.endsWith("/") ? davBaseHref : davBaseHref + "/";
            return WebDavResource.collection("dav", base);
        }
        final WebDavBackend backend = backendForPath(normalized);
        if (backend == null) {
            return null;
        }
        final String mountSegment = backend.mountPath().replaceFirst("^/", "");
        final String base = davBaseHref.endsWith("/") ? davBaseHref : davBaseHref + "/";
        final String mountHref = WebDavPathUtils.joinHref(base, mountSegment + "/");
        final String inner = stripMount(backend, normalized);
        if (inner.isEmpty()) {
            return WebDavResource.collection(backend.displayName(), mountHref);
        }
        final WebDavResource r = backend.resolve(inner);
        if (r == null) {
            return null;
        }
        return new WebDavResource(r.name,
                WebDavPathUtils.joinHref(mountHref, inner),
                r.isCollection, r.contentLength, r.lastModifiedMillis, r.mimeType, r.etag);
    }

    /**
     * 列出目录子项。
     */
    public List<WebDavResource> listChildren(final String relativePath, final String davBaseHref) throws Exception {
        final String normalized = WebDavPathUtils.normalizeRelativePath(relativePath);
        final String base = davBaseHref.endsWith("/") ? davBaseHref : davBaseHref + "/";
        if (normalized.isEmpty()) {
            return listRootMounts(davBaseHref);
        }
        final WebDavBackend backend = backendForPath(normalized);
        if (backend == null) {
            return java.util.Collections.emptyList();
        }
        final String mountSegment = backend.mountPath().replaceFirst("^/", "");
        final String mountHref = WebDavPathUtils.joinHref(base, mountSegment + "/");
        final String inner = stripMount(backend, normalized);
        if (inner.isEmpty()) {
            final List<WebDavResource> children = backend.listChildren("");
            final List<WebDavResource> out = new ArrayList<>(children.size());
            for (final WebDavResource c : children) {
                out.add(new WebDavResource(c.name,
                        WebDavPathUtils.joinHref(mountHref, WebDavPathUtils.childSegment(c.href)),
                        c.isCollection, c.contentLength, c.lastModifiedMillis, c.mimeType, c.etag));
            }
            return out;
        }
        final List<WebDavResource> children = backend.listChildren(inner);
        final String parentHref = WebDavPathUtils.joinHref(mountHref, inner);
        final String prefix = parentHref.endsWith("/") ? parentHref : parentHref + "/";
        final List<WebDavResource> out = new ArrayList<>(children.size());
        for (final WebDavResource c : children) {
            out.add(new WebDavResource(c.name,
                    WebDavPathUtils.joinHref(prefix, WebDavPathUtils.childSegment(c.href)),
                    c.isCollection, c.contentLength, c.lastModifiedMillis, c.mimeType, c.etag));
        }
        return out;
    }

    public WebDavReadResult openRead(final String relativePath) throws Exception {
        final String normalized = WebDavPathUtils.normalizeRelativePath(relativePath);
        final WebDavBackend backend = backendForPath(normalized);
        if (backend == null) {
            return null;
        }
        final String inner = stripMount(backend, normalized);
        if (inner.isEmpty()) {
            return null;
        }
        return backend.openRead(inner);
    }

    /**
     * PROPFIND depth=infinity 递归列举；{@code maxDepth} 限制层级（默认 3）。
     */
    public List<WebDavResource> collectRecursive(final String relativePath, final String davBaseHref,
                                                  final int maxDepth, final int currentDepth) throws Exception {
        final WebDavResource self = resolve(relativePath, davBaseHref);
        if (self == null) {
            return java.util.Collections.emptyList();
        }
        final List<WebDavResource> results = new ArrayList<>();
        results.add(self);
        if (!self.isCollection || currentDepth >= maxDepth) {
            return results;
        }
        final List<WebDavResource> children = listChildren(relativePath, davBaseHref);
        for (final WebDavResource c : children) {
            final String childRel = relativeFromHref(davBaseHref, c.href);
            if (c.isCollection) {
                results.addAll(collectRecursive(childRel, davBaseHref, maxDepth, currentDepth + 1));
            } else {
                results.add(c);
            }
        }
        return results;
    }

    private String relativeFromHref(final String davBaseHref, final String href) {
        final String base = davBaseHref.endsWith("/") ? davBaseHref : davBaseHref + "/";
        if (href.startsWith(base)) {
            return WebDavPathUtils.normalizeRelativePath(href.substring(base.length()));
        }
        return WebDavPathUtils.normalizeRelativePath(href);
    }
}

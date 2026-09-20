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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 系统相册 WebDAV 后端，挂载于 {@code /dav/photos/}。
 * 平移自 niejianying 参考实现 {@code photos_webdav_backend.dart}。
 */
public final class PhotosWebDavBackend implements WebDavBackend {

    private static final String TAG = "WebDavPhotos";

    private final Context context;
    private final ContentResolver resolver;

    public PhotosWebDavBackend(final Context context) {
        this.context = context.getApplicationContext();
        this.resolver = this.context.getContentResolver();
    }

    @Override
    public String mountPath() {
        return "/photos";
    }

    @Override
    public String displayName() {
        return "photos";
    }

    @Override
    public WebDavResource resolve(final String relativePath) throws Exception {
        final String path = WebDavPathUtils.normalizeRelativePath(relativePath);
        if (path.isEmpty()) {
            return WebDavResource.collection("photos", "photos/");
        }
        final String[] segments = path.split("/");
        if (segments.length == 1) {
            final Album album = findAlbumBySlug(segments[0]);
            if (album == null) {
                return null;
            }
            return WebDavResource.collection(album.displayName, segments[0] + "/");
        }
        if (segments.length == 2) {
            final String albumSlug = segments[0];
            final String fileName = segments[1];
            if (findAlbumBySlug(albumSlug) == null) {
                return null;
            }
            final String assetId = WebDavPathUtils.parseAssetIdFromFileName(fileName);
            if (assetId == null) {
                return null;
            }
            final MediaAsset asset = resolveAsset(assetId);
            if (asset == null) {
                return null;
            }
            return WebDavResource.file(fileName, albumSlug + "/" + fileName,
                    asset.sizeBytes, asset.dateModifiedMillis, asset.mimeType, assetId);
        }
        return null;
    }

    @Override
    public List<WebDavResource> listChildren(final String relativePath) throws Exception {
        final String path = WebDavPathUtils.normalizeRelativePath(relativePath);
        if (path.isEmpty()) {
            final Map<String, Album> albums = loadAlbums();
            final List<WebDavResource> out = new ArrayList<>(albums.size());
            for (final Map.Entry<String, Album> e : albums.entrySet()) {
                out.add(WebDavResource.collection(e.getValue().displayName, e.getKey() + "/"));
            }
            return out;
        }
        final String[] segments = path.split("/");
        if (segments.length != 1) {
            return Collections.emptyList();
        }
        final Album album = findAlbumBySlug(segments[0]);
        if (album == null) {
            return Collections.emptyList();
        }
        return listAlbumFiles(album, segments[0]);
    }

    @Override
    public WebDavReadResult openRead(final String relativePath) throws Exception {
        final String path = WebDavPathUtils.normalizeRelativePath(relativePath);
        final String[] segments = path.split("/");
        if (segments.length != 2) {
            return null;
        }
        final String assetId = WebDavPathUtils.parseAssetIdFromFileName(segments[1]);
        if (assetId == null) {
            return null;
        }
        return openAssetFile(assetId);
    }

    private WebDavReadResult openAssetFile(final String assetId) throws Exception {
        final MediaAsset asset = resolveAsset(assetId);
        if (asset == null) {
            return null;
        }
        final Uri uri = ContentUris.withAppendedId(asset.collectionUri, asset.id);
        final InputStream in = resolver.openInputStream(uri);
        if (in == null) {
            return null;
        }
        return new WebDavReadResult(in, asset.sizeBytes, asset.mimeType, asset.dateModifiedMillis, assetId);
    }

    private static class Album {
        final long bucketId;
        final String displayName;
        Album(final long bucketId, final String displayName) {
            this.bucketId = bucketId;
            this.displayName = displayName;
        }
    }

    private static class MediaAsset {
        final long id;
        final long sizeBytes;
        final long dateModifiedMillis;
        final String mimeType;
        final Uri collectionUri;
        MediaAsset(final long id, final long sizeBytes, final long dateModifiedMillis,
                   final String mimeType, final Uri collectionUri) {
            this.id = id;
            this.sizeBytes = sizeBytes;
            this.dateModifiedMillis = dateModifiedMillis;
            this.mimeType = mimeType;
            this.collectionUri = collectionUri;
        }
    }

    private Map<String, Album> loadAlbums() {
        final Map<Long, String> nameByBucket = new HashMap<>();
        collectAlbums(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, nameByBucket);
        collectAlbums(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, nameByBucket);
        final Set<String> usedSlugs = new HashSet<>();
        final Map<String, Album> out = new HashMap<>();
        for (final Map.Entry<Long, String> e : nameByBucket.entrySet()) {
            final String slug = WebDavPathUtils.uniqueSlug(e.getValue(), usedSlugs);
            out.put(slug, new Album(e.getKey(), e.getValue()));
        }
        return out;
    }

    private void collectAlbums(final Uri collectionUri, final Map<Long, String> out) {
        final String[] projection = {
                "BUCKET_ID",
                "BUCKET_DISPLAY_NAME"
        };
        final String bucketIdCol = MediaStore.Images.Media.BUCKET_ID;
        final String bucketNameCol = MediaStore.Images.Media.BUCKET_DISPLAY_NAME;
        try (final Cursor cursor = resolver.query(collectionUri, projection,
                bucketIdCol + " IS NOT NULL) GROUP BY (" + bucketIdCol, null, null)) {
            if (cursor == null) {
                return;
            }
            final int idIdx = cursor.getColumnIndex(bucketIdCol);
            final int nameIdx = cursor.getColumnIndex(bucketNameCol);
            while (cursor.moveToNext()) {
                if (idIdx < 0 || nameIdx < 0) {
                    continue;
                }
                final long bucketId = cursor.getLong(idIdx);
                final String name = cursor.getString(nameIdx);
                if (name == null || name.isEmpty()) {
                    continue;
                }
                out.putIfAbsent(bucketId, name);
            }
        } catch (final Exception e) {
            Log.w(TAG, "query albums failed [" + collectionUri + "]: " + e.getMessage());
        }
    }

    private Album findAlbumBySlug(final String slug) {
        for (final Map.Entry<String, Album> e : loadAlbums().entrySet()) {
            if (e.getKey().equals(slug)) {
                return e.getValue();
            }
        }
        return null;
    }

    private MediaAsset resolveAsset(final String assetId) {
        final MediaAsset fromImages = queryAssetById(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, assetId);
        if (fromImages != null) {
            return fromImages;
        }
        return queryAssetById(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, assetId);
    }

    private MediaAsset queryAssetById(final Uri collectionUri, final String assetId) {
        final String[] projection = {
                "_ID",
                "_SIZE",
                "DATE_MODIFIED",
                "MIME_TYPE",
                "DISPLAY_NAME"
        };
        try (final Cursor cursor = resolver.query(collectionUri, projection,
                "_ID = ?", new String[]{assetId}, null)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return null;
            }
            final int idIdx = cursor.getColumnIndex("_ID");
            final int sizeIdx = cursor.getColumnIndex("_SIZE");
            final int dateIdx = cursor.getColumnIndex("DATE_MODIFIED");
            final int mimeIdx = cursor.getColumnIndex("MIME_TYPE");
            if (idIdx < 0 || sizeIdx < 0 || dateIdx < 0 || mimeIdx < 0) {
                return null;
            }
            final long id = cursor.getLong(idIdx);
            long size = cursor.getLong(sizeIdx);
            if (size <= 0) {
                final Uri uri = ContentUris.withAppendedId(collectionUri, id);
                try (final InputStream probe = resolver.openInputStream(uri)) {
                    if (probe != null) {
                        size = Math.max(0, probe.available());
                    }
                } catch (final Exception ignored) {
                }
            }
            return new MediaAsset(id, size, cursor.getLong(dateIdx) * 1000L,
                    cursor.getString(mimeIdx), collectionUri);
        } catch (final Exception e) {
            Log.w(TAG, "query asset failed [" + assetId + "]: " + e.getMessage());
            return null;
        }
    }

    private List<WebDavResource> listAlbumFiles(final Album album, final String albumSlug) {
        final List<WebDavResource> out = new ArrayList<>();
        appendAlbumFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, album, albumSlug, out);
        appendAlbumFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, album, albumSlug, out);
        return out;
    }

    private void appendAlbumFiles(final Uri collectionUri, final Album album, final String albumSlug,
                                  final List<WebDavResource> out) {
        final String[] projection = {
                "_ID",
                "_SIZE",
                "DATE_MODIFIED",
                "MIME_TYPE",
                "DISPLAY_NAME",
                "BUCKET_ID"
        };
        try (final Cursor cursor = resolver.query(collectionUri, projection,
                "BUCKET_ID = ?", new String[]{String.valueOf(album.bucketId)},
                "DATE_MODIFIED DESC")) {
            if (cursor == null) {
                return;
            }
            final int idIdx = cursor.getColumnIndex("_ID");
            final int sizeIdx = cursor.getColumnIndex("_SIZE");
            final int dateIdx = cursor.getColumnIndex("DATE_MODIFIED");
            final int mimeIdx = cursor.getColumnIndex("MIME_TYPE");
            final int nameIdx = cursor.getColumnIndex("DISPLAY_NAME");
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(idIdx);
                long size = cursor.getLong(sizeIdx);
                if (size <= 0) {
                    final Uri uri = ContentUris.withAppendedId(collectionUri, id);
                    try (final InputStream probe = resolver.openInputStream(uri)) {
                        if (probe != null) {
                            size = Math.max(0, probe.available());
                        }
                    } catch (final Exception ignored) {
                    }
                }
                final long date = cursor.getLong(dateIdx) * 1000L;
                final String mime = cursor.getString(mimeIdx);
                final String displayName = nameIdx >= 0 ? cursor.getString(nameIdx) : null;
                final String ext = inferExtension(mime);
                final String fileName = WebDavPathUtils.buildFileName(
                        displayName == null ? "file" : displayName, String.valueOf(id), ext);
                final String etag = String.valueOf(id);
                out.add(WebDavResource.file(fileName, albumSlug + "/" + fileName,
                        size, date, mime, etag));
            }
        } catch (final Exception e) {
            Log.w(TAG, "list album files failed [" + collectionUri + "]: " + e.getMessage());
        }
    }

    private static String inferExtension(final String mime) {
        if (mime == null) {
            return "";
        }
        switch (mime) {
            case "image/jpeg":
                return "jpg";
            case "image/png":
                return "png";
            case "image/gif":
                return "gif";
            case "image/webp":
                return "webp";
            case "image/heic":
                return "heic";
            case "image/heif":
                return "heif";
            case "image/bmp":
                return "bmp";
            case "video/mp4":
                return "mp4";
            case "video/3gpp":
                return "3gp";
            case "video/quicktime":
                return "mov";
            default:
                return "";
        }
    }
}

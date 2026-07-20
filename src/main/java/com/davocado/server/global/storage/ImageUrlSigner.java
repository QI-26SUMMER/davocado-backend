package com.davocado.server.global.storage;

import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Turns a stored {@code gs://bucket/object} path into a short-lived HTTPS URL the client can fetch.
 *
 * <p>The bucket is private, so raw {@code gs://} paths are useless to the app — every image handed
 * to a client goes through here first.
 *
 * <p>Returns null when GCS is not configured (no bucket, no credentials) rather than throwing, so
 * environments without GCP still serve scan data with the image fields simply empty.
 */
@Component
public class ImageUrlSigner {

    private static final String GS_SCHEME = "gs://";

    private final ObjectProvider<Storage> storageProvider;
    private final GcsProperties properties;

    public ImageUrlSigner(ObjectProvider<Storage> storageProvider, GcsProperties properties) {
        this.storageProvider = storageProvider;
        this.properties = properties;
    }

    /** Returns a signed read URL, or null if the path is empty or GCS is unavailable. */
    public String sign(String gcsPath) {
        if (gcsPath == null || gcsPath.isBlank()) {
            return null;
        }
        Storage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            return null;
        }
        String withoutScheme = gcsPath.startsWith(GS_SCHEME) ? gcsPath.substring(GS_SCHEME.length()) : gcsPath;
        int slash = withoutScheme.indexOf('/');
        if (slash < 0) {
            return null;
        }
        String bucket = withoutScheme.substring(0, slash);
        String object = withoutScheme.substring(slash + 1);
        if (object.isEmpty()) {
            return null;
        }
        return storage.signUrl(
                        BlobInfo.newBuilder(bucket, object).build(),
                        properties.signedUrlTtlMinutes(),
                        TimeUnit.MINUTES,
                        Storage.SignUrlOption.withV4Signature())
                .toString();
    }
}

package com.davocado.server.global.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Writes image bytes to the private GCS bucket.
 *
 * <p>Returns null instead of throwing when GCS is not configured, mirroring {@link ImageUrlSigner}:
 * an environment without GCP still works, it just stores no images.
 */
@Component
public class ImageStorage {

    private final ObjectProvider<Storage> storageProvider;
    private final GcsProperties properties;

    public ImageStorage(ObjectProvider<Storage> storageProvider, GcsProperties properties) {
        this.storageProvider = storageProvider;
        this.properties = properties;
    }

    /**
     * Uploads to {@code objectName} within the configured bucket.
     *
     * @return the {@code gs://bucket/object} URI to persist, or null if GCS is unavailable
     */
    public String upload(String objectName, byte[] content, String contentType) {
        Storage storage = storageProvider.getIfAvailable();
        if (storage == null || properties.bucket() == null || properties.bucket().isBlank()) {
            return null;
        }
        BlobInfo blobInfo = BlobInfo.newBuilder(BlobId.of(properties.bucket(), objectName))
                .setContentType(contentType)
                .build();
        storage.create(blobInfo, content);
        return "gs://" + properties.bucket() + "/" + objectName;
    }
}

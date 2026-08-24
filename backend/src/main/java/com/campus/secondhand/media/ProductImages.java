package com.campus.secondhand.media;

import org.springframework.core.io.Resource;

public interface ProductImages {

    StoredImage store(Long ownerId, ImageUpload upload);

    StoredContent load(Long ownerId, String fileName);

    record ImageUpload(byte[] content, String originalName, String declaredContentType) {
    }

    record StoredImage(String url, String contentType, long size, int width, int height) {
    }

    record StoredContent(Resource resource, String contentType, long size) {
    }
}

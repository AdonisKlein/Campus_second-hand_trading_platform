package com.campus.secondhand.marketplace.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "campus.minio")
public record MinioProperties(
        String endpoint,
        String accessKey,
        String secretKey,
        String bucket
) {
    public MinioProperties {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("MinIO endpoint is required when using minio storage");
        }
        if (accessKey == null || accessKey.isBlank()) {
            throw new IllegalArgumentException("MinIO accessKey is required when using minio storage");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalArgumentException("MinIO secretKey is required when using minio storage");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("MinIO bucket is required when using minio storage");
        }
    }
}
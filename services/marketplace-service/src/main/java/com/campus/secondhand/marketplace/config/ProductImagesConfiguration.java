package com.campus.secondhand.marketplace.config;

import com.campus.secondhand.marketplace.media.FileSystemProductImages;
import com.campus.secondhand.marketplace.media.MinioProductImages;
import com.campus.secondhand.marketplace.media.ProductImages;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class ProductImagesConfiguration {

    @Bean
    @ConditionalOnProperty(name = "campus.image.storage", havingValue = "minio")
    public ProductImages minioProductImages(MinioClient minioClient, MinioProperties minioProperties) {
        return new MinioProductImages(minioClient, minioProperties);
    }

    @Bean
    @ConditionalOnProperty(name = "campus.image.storage", havingValue = "filesystem", matchIfMissing = true)
    public ProductImages fileSystemProductImages(com.campus.secondhand.marketplace.MarketplaceProperties properties) {
        return new FileSystemProductImages(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "campus.image.storage", havingValue = "minio")
    public MinioClient minioClient(MinioProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }
}

package com.campus.secondhand.marketplace.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.campus.secondhand.marketplace.config.MinioProperties;
import io.minio.MinioClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class MinioProductImagesIT {

    private static final String MINIO_IMAGE =
            "minio/minio@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(MINIO_IMAGE)
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data");

    @Test
    void storedImageIsReadableByAnotherAdapterInstance() throws Exception {
        byte[] png = png(8, 6);
        String bucket = "campus-it-" + randomSuffix();
        ProductImages.StoredImage stored = adapter(bucket).store(7L,
                new ProductImages.ImageUpload(png, "photo.png", "image/png"));

        assertThat(stored.url()).matches("/media/product-images/7/[0-9a-f-]{36}\\.png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.width()).isEqualTo(8);
        assertThat(stored.height()).isEqualTo(6);

        // 模拟扩容后的另一个实例从同一桶读取旧实例上传的对象
        ProductImages.StoredContent loaded = adapter(bucket).load(7L, fileName(stored.url()));
        assertThat(loaded.contentType()).isEqualTo("image/png");
        assertThat(loaded.size()).isEqualTo(png.length);
        assertThat(loaded.resource().getInputStream().readAllBytes()).isEqualTo(png);
    }

    @Test
    void missingObjectIsReportedAsNotFound() throws Exception {
        String bucket = "campus-it-" + randomSuffix();
        MinioProductImages images = adapter(bucket);
        images.store(7L, new ProductImages.ImageUpload(png(4, 4), "photo.png", "image/png"));

        ProductImageException error = catchThrowableOfType(() ->
                images.load(7L, UUID.randomUUID() + ".png"), ProductImageException.class);
        assertThat(error).hasMessageContaining("图片不存在");
        assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private MinioProductImages adapter(String bucket) {
        String endpoint = "http://127.0.0.1:" + MINIO.getMappedPort(9000);
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials("minioadmin", "minioadmin")
                .build();
        return new MinioProductImages(client,
                new MinioProperties(endpoint, "minioadmin", "minioadmin", bucket));
    }

    private byte[] png(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private String fileName(String url) {
        return url.substring(url.lastIndexOf('/') + 1);
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}

package com.campus.secondhand.marketplace.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.campus.secondhand.marketplace.config.MinioProperties;
import io.minio.MinioClient;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class MinioProductImagesTest {

    private final MinioProductImages images = adapter("http://127.0.0.1:1");

    @Test
    void rejectsEmptyUploadBeforeAnyNetworkCall() {
        ProductImageException error = catchThrowableOfType(() -> images.store(7L,
                new ProductImages.ImageUpload(new byte[0], "x.png", "image/png")),
                ProductImageException.class);
        assertThat(error).hasMessageContaining("请选择图片文件");
        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsOversizedUploadBeforeAnyNetworkCall() {
        ProductImageException error = catchThrowableOfType(() -> images.store(7L,
                new ProductImages.ImageUpload(new byte[5 * 1024 * 1024 + 1], "x.png", "image/png")),
                ProductImageException.class);
        assertThat(error).hasMessageContaining("图片不能超过 5MB");
        assertThat(error.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @Test
    void rejectsBytesThatAreNotJpegOrPngBeforeAnyNetworkCall() {
        ProductImageException error = catchThrowableOfType(() -> images.store(7L,
                new ProductImages.ImageUpload(new byte[] {1, 2, 3}, "x.jpg", "image/jpeg")),
                ProductImageException.class);
        assertThat(error).hasMessageContaining("仅支持 JPG 和 PNG 图片");
        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsOversizedImageDimensionsBeforeAnyNetworkCall() throws Exception {
        BufferedImage huge = new BufferedImage(8_001, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(huge, "png", bytes);
        ProductImageException error = catchThrowableOfType(() -> images.store(7L,
                new ProductImages.ImageUpload(bytes.toByteArray(), "huge.png", "image/png")),
                ProductImageException.class);
        assertThat(error).hasMessageContaining("图片尺寸过大");
        assertThat(error.status()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void rejectsUnsafeObjectNameBeforeAnyNetworkCall() {
        ProductImageException error = catchThrowableOfType(() -> images.load(7L, "..%2F..%2Fsecret.png"),
                ProductImageException.class);
        assertThat(error).hasMessageContaining("图片不存在");
        assertThat(error.status()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private MinioProductImages adapter(String endpoint) {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials("minioadmin", "minioadmin")
                .build();
        return new MinioProductImages(client,
                new MinioProperties(endpoint, "minioadmin", "minioadmin", "campus-test-images"));
    }
}

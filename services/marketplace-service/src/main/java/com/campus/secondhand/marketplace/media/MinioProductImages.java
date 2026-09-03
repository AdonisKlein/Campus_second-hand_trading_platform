package com.campus.secondhand.marketplace.media;

import com.campus.secondhand.marketplace.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;

public class MinioProductImages implements ProductImages {

    private static final Logger logger = LoggerFactory.getLogger(MinioProductImages.class);
    private static final long MAX_UPLOAD_BYTES = 5 * 1024 * 1024; // 5MB
    private static final long MAX_PIXELS = 12_000_000L;
    private static final int MAX_EDGE = 8_000;

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private boolean bucketEnsured;

    public MinioProductImages(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public StoredImage store(Long ownerId, ImageUpload upload) {
        if (ownerId == null || ownerId <= 0 || upload == null || upload.content() == null
                || upload.content().length == 0) {
            throw invalid("请选择图片文件");
        }
        if (upload.content().length > MAX_UPLOAD_BYTES) {
            throw new ProductImageException(HttpStatus.PAYLOAD_TOO_LARGE, "图片不能超过 5MB");
        }
        ImageInfo info = inspect(upload.content());
        String extension = "jpeg".equals(info.format()) ? "jpg" : "png";
        String fileName = UUID.randomUUID() + "." + extension;
        String objectName = ownerId + "/" + fileName;
        ensureBucket();
        try (InputStream inputStream = new ByteArrayInputStream(upload.content())) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.bucket())
                            .object(objectName)
                            .stream(inputStream, upload.content().length, -1)
                            .contentType("image/" + info.format())
                            .build()
            );
            String url = "/media/product-images/" + ownerId + "/" + fileName;
            logger.info("Stored image in MinIO bucket={} object={}", properties.bucket(), objectName);
            return new StoredImage(url, "image/" + info.format(), upload.content().length,
                    info.width(), info.height());
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Failed to store image in MinIO: {}", e.getMessage());
            throw new ProductImageException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败");
        }
    }

    @Override
    public StoredContent load(Long ownerId, String fileName) {
        if (ownerId == null || ownerId <= 0 || fileName == null
                || !fileName.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            throw notFound();
        }
        String objectName = ownerId + "/" + fileName;
        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(properties.bucket())
                        .object(objectName)
                        .build()
        )) {
            byte[] content = inputStream.readAllBytes();
            String contentType = detectContentType(content);
            return new StoredContent(new ByteArrayResource(content), contentType, content.length);
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw notFound();
            }
            logger.warn("Failed to load image from MinIO: {}", e.getMessage());
            throw new ProductImageException(HttpStatus.SERVICE_UNAVAILABLE, "图片服务暂时不可用");
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            logger.warn("Failed to load image from MinIO: {}", e.getMessage());
            throw new ProductImageException(HttpStatus.SERVICE_UNAVAILABLE, "图片服务暂时不可用");
        }
    }

    private synchronized void ensureBucket() {
        if (bucketEnsured) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(properties.bucket()).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(properties.bucket()).build()
                );
                logger.info("Created MinIO bucket: {}", properties.bucket());
            }
            bucketEnsured = true;
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("Failed to ensure MinIO bucket exists: {}", e.getMessage());
            throw new ProductImageException(HttpStatus.INTERNAL_SERVER_ERROR, "图片保存失败");
        }
    }

    private String detectContentType(byte[] content) {
        if (content.length > 4) {
            if (content[0] == (byte) 0xFF && content[1] == (byte) 0xD8) {
                return "image/jpeg";
            }
            if (content[0] == (byte) 0x89 && content[1] == (byte) 0x50
                    && content[2] == (byte) 0x4E && content[3] == (byte) 0x47) {
                return "image/png";
            }
        }
        return "application/octet-stream";
    }

    private ImageInfo inspect(byte[] bytes) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (stream == null) {
                throw invalid("无法识别图片内容");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw invalid("仅支持 JPG 和 PNG 图片");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if ("jpg".equals(format)) {
                    format = "jpeg";
                }
                if (!"jpeg".equals(format) && !"png".equals(format)) {
                    throw invalid("仅支持 JPG 和 PNG 图片");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE
                        || (long) width * height > MAX_PIXELS) {
                    throw invalid("图片尺寸过大，最多 1200 万像素且单边不超过 8000px");
                }
                return new ImageInfo(format, width, height);
            } finally {
                reader.dispose();
            }
        } catch (ProductImageException e) {
            throw e;
        } catch (IOException e) {
            throw invalid("图片文件已损坏或无法读取");
        }
    }

    private ProductImageException invalid(String message) {
        return new ProductImageException(HttpStatus.BAD_REQUEST, message);
    }

    private ProductImageException notFound() {
        return new ProductImageException(HttpStatus.NOT_FOUND, "图片不存在");
    }

    private record ImageInfo(String format, int width, int height) {}
}

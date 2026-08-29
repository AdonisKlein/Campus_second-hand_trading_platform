package com.campus.secondhand.unit.media;

import com.campus.secondhand.media.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductImagesTest {
    @Test void storeNormalizesPngAndReturnsOwnedPath() throws Exception {
        BufferedImage image = new BufferedImage(2, 3, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out);
        FileSystemProductImages images = new FileSystemProductImages("target/test-uploads");
        var stored = images.store(42L, new ProductImages.ImageUpload(out.toByteArray(), "x.png", "image/png"));
        assertAll(() -> assertTrue(stored.url().startsWith("/media/product-images/42/")),
            () -> assertEquals(2, stored.width()), () -> assertEquals(3, stored.height()));
        assertDoesNotThrow(() -> images.load(42L, stored.url().substring(stored.url().lastIndexOf('/') + 1)));
    }

    @Test void rejectsInvalidBytesAndTraversal() {
        FileSystemProductImages images = new FileSystemProductImages("target/test-uploads-invalid");
        assertThrows(ProductImageException.class, () -> images.store(1L,
            new ProductImages.ImageUpload(new byte[] {1, 2, 3}, "x.bin", "application/octet-stream")));
        assertThrows(ProductImageException.class, () -> images.load(1L, "../secret.png"));
    }
}

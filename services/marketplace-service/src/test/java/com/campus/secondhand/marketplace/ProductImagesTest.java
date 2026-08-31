package com.campus.secondhand.marketplace;

import static org.assertj.core.api.Assertions.*;

import com.campus.secondhand.marketplace.media.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductImagesTest {
    @TempDir Path temp;

    @Test void actualImageContentControlsFormatAndRandomPlatformPath() throws Exception {
        BufferedImage source=new BufferedImage(8,6,BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        ImageIO.write(source,"png",bytes);
        var properties=new MarketplaceProperties("http://account","http://trading",
                "service-token-012345678901234567890123","jwt-secret-012345678901234567890123456",temp.toString(),300,800);
        var images=new FileSystemProductImages(properties);
        var stored=images.store(7L,new ProductImages.ImageUpload(bytes.toByteArray(),"fake.jpg","image/jpeg"));
        assertThat(stored.contentType()).isEqualTo("image/png");
        assertThat(stored.url()).matches("/media/product-images/7/[0-9a-f-]{36}\\.png");
        assertThat(images.load(7L,stored.url().substring(stored.url().lastIndexOf('/')+1)).size()).isPositive();
    }

    @Test void nonImageBytesAreRejected() {
        var properties=new MarketplaceProperties("http://account","http://trading",
                "service-token-012345678901234567890123","jwt-secret-012345678901234567890123456",temp.toString(),300,800);
        var images=new FileSystemProductImages(properties);
        assertThatThrownBy(()->images.store(7L,new ProductImages.ImageUpload(new byte[]{1,2,3},"x.jpg","image/jpeg")))
                .isInstanceOf(ProductImageException.class).hasMessageContaining("支持 JPG 和 PNG");
    }
}

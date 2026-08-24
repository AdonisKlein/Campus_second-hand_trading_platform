package com.campus.secondhand.media;

import com.campus.secondhand.common.ApiResponse;
import com.campus.secondhand.security.CurrentActor;
import com.campus.secondhand.security.CurrentActorService;
import java.io.IOException;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/media/product-images")
public class ProductImageController {
    private final ProductImages images;
    private final CurrentActorService actors;

    public ProductImageController(ProductImages images, CurrentActorService actors) {
        this.images = images;
        this.actors = actors;
    }

    @PostMapping
    public ApiResponse<ProductImages.StoredImage> upload(@RequestParam("file") MultipartFile file) throws IOException {
        CurrentActor actor = actors.require();
        if (!"STUDENT".equals(actor.role())) {
            throw new AccessDeniedException("只有学生用户可以上传商品图片");
        }
        ProductImages.ImageUpload upload = new ProductImages.ImageUpload(
            file.getBytes(), file.getOriginalFilename(), file.getContentType());
        return ApiResponse.created(images.store(actor.userId(), upload));
    }

    @GetMapping("/{ownerId}/{fileName}")
    public ResponseEntity<org.springframework.core.io.Resource> load(@PathVariable Long ownerId,
                                                                     @PathVariable String fileName) {
        ProductImages.StoredContent content = images.load(ownerId, fileName);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .contentLength(content.size())
            .cacheControl(CacheControl.maxAge(java.time.Duration.ofDays(365)).cachePublic().immutable())
            .body(content.resource());
    }
}

package com.campus.secondhand.marketplace.media;

import com.campus.secondhand.marketplace.*;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/media/product-images")
public class ProductImageController {
    private final ProductImages images; private final CurrentActorService actors;
    public ProductImageController(ProductImages images,CurrentActorService actors){this.images=images;this.actors=actors;}
    @PostMapping public ApiResponse<ProductImages.StoredImage> upload(Authentication authentication,@RequestParam("file") MultipartFile file)throws IOException{CurrentActor actor=actors.require(authentication);if(!"STUDENT".equals(actor.role()))throw new AccessDeniedException("只有学生用户可以上传商品图片");return ApiResponse.created(images.store(actor.userId(),new ProductImages.ImageUpload(file.getBytes(),file.getOriginalFilename(),file.getContentType())));}
    @GetMapping("/{ownerId}/{fileName}") public ResponseEntity<org.springframework.core.io.Resource> load(@PathVariable Long ownerId,@PathVariable String fileName){var content=images.load(ownerId,fileName);return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType())).contentLength(content.size()).cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable()).body(content.resource());}
}

package com.campus.secondhand.marketplace.media;

import com.campus.secondhand.marketplace.MarketplaceProperties;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;
import javax.imageio.*;
import javax.imageio.stream.*;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;

public class FileSystemProductImages implements ProductImages {
    private static final long MAX_UPLOAD_BYTES=5L*1024*1024,MAX_OWNER_BYTES=100L*1024*1024,MAX_PIXELS=12_000_000L;
    private static final int MAX_EDGE=8_000;
    private final Path root;
    private final Semaphore imageProcessors=new Semaphore(2,true);
    private final Object[] ownerLocks=java.util.stream.IntStream.range(0,64).mapToObj(index->new Object()).toArray();
    public FileSystemProductImages(MarketplaceProperties properties){root=Path.of(properties.uploadDir()).toAbsolutePath().normalize().resolve("product-images");}

    @Override public StoredImage store(Long ownerId,ImageUpload upload){
        if(ownerId==null||ownerId<=0||upload==null||upload.content()==null||upload.content().length==0)throw invalid("请选择图片文件");
        if(upload.content().length>MAX_UPLOAD_BYTES)throw new ProductImageException(HttpStatus.PAYLOAD_TOO_LARGE,"图片不能超过 5MB");
        if(!imageProcessors.tryAcquire())throw new ProductImageException(HttpStatus.TOO_MANY_REQUESTS,"图片处理繁忙，请稍后重试");
        ImageInfo info;byte[] normalized;
        try{info=inspect(upload.content());normalized=normalize(upload.content(),info.format());}finally{imageProcessors.release();}
        if(normalized.length>MAX_UPLOAD_BYTES)throw new ProductImageException(HttpStatus.PAYLOAD_TOO_LARGE,"处理后的图片超过 5MB，请降低分辨率");
        synchronized(ownerLocks[Math.floorMod(ownerId.hashCode(),ownerLocks.length)]){return persist(ownerId,normalized,info);}
    }
    private StoredImage persist(Long ownerId,byte[] bytes,ImageInfo info){Path directory=directory(ownerId);try{Files.createDirectories(directory);long used;try(Stream<Path> paths=Files.list(directory)){used=paths.filter(Files::isRegularFile).mapToLong(this::safeSize).sum();}if(used+bytes.length>MAX_OWNER_BYTES)throw new ProductImageException(HttpStatus.PAYLOAD_TOO_LARGE,"个人图片空间已达到 100MB 上限");String extension="jpeg".equals(info.format())?"jpg":"png";String name=UUID.randomUUID()+"."+extension;Path temporary=Files.createTempFile(directory,"upload-",".tmp"),target=directory.resolve(name);try{Files.write(temporary,bytes);Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE);}finally{Files.deleteIfExists(temporary);}return new StoredImage("/media/product-images/"+ownerId+"/"+name,"image/"+info.format(),bytes.length,info.width(),info.height());}catch(ProductImageException error){throw error;}catch(IOException error){throw new ProductImageException(HttpStatus.INTERNAL_SERVER_ERROR,"图片保存失败");}}
    @Override public StoredContent load(Long ownerId,String name){if(ownerId==null||ownerId<=0||name==null||!name.matches("[0-9a-fA-F-]{36}\\.(jpg|png)"))throw notFound();Path target=directory(ownerId).resolve(name).normalize();if(!target.startsWith(directory(ownerId))||!Files.isRegularFile(target))throw notFound();try{return new StoredContent(new FileSystemResource(target),name.endsWith(".png")?"image/png":"image/jpeg",Files.size(target));}catch(IOException error){throw notFound();}}
    private ImageInfo inspect(byte[] bytes){try(ImageInputStream stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))){if(stream==null)throw invalid("无法识别图片内容");Iterator<ImageReader> readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw invalid("仅支持 JPG 和 PNG 图片");ImageReader reader=readers.next();try{reader.setInput(stream,true,true);String format=reader.getFormatName().toLowerCase(Locale.ROOT);if("jpg".equals(format))format="jpeg";if(!"jpeg".equals(format)&&!"png".equals(format))throw invalid("仅支持 JPG 和 PNG 图片");int width=reader.getWidth(0),height=reader.getHeight(0);if(width<=0||height<=0||width>MAX_EDGE||height>MAX_EDGE||(long)width*height>MAX_PIXELS)throw invalid("图片尺寸过大，最多 1200 万像素且单边不超过 8000px");return new ImageInfo(format,width,height);}finally{reader.dispose();}}catch(ProductImageException error){throw error;}catch(IOException error){throw invalid("图片文件已损坏或无法读取");}}
    private byte[] normalize(byte[] bytes,String format){try{BufferedImage source=ImageIO.read(new ByteArrayInputStream(bytes));if(source==null)throw invalid("图片文件已损坏或无法读取");BufferedImage output=source;if("jpeg".equals(format)){output=new BufferedImage(source.getWidth(),source.getHeight(),BufferedImage.TYPE_INT_RGB);Graphics2D graphics=output.createGraphics();graphics.setColor(Color.WHITE);graphics.fillRect(0,0,output.getWidth(),output.getHeight());graphics.drawImage(source,0,0,null);graphics.dispose();}ByteArrayOutputStream buffer=new ByteArrayOutputStream();Iterator<ImageWriter> writers=ImageIO.getImageWritersByFormatName(format);if(!writers.hasNext())throw invalid("服务器不支持处理该图片");ImageWriter writer=writers.next();try(ImageOutputStream imageOutput=ImageIO.createImageOutputStream(buffer)){writer.setOutput(imageOutput);var params=writer.getDefaultWriteParam();if("jpeg".equals(format)&&params.canWriteCompressed()){params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);params.setCompressionQuality(.88f);}writer.write(null,new javax.imageio.IIOImage(output,null,null),params);}finally{writer.dispose();}return buffer.toByteArray();}catch(ProductImageException error){throw error;}catch(IOException error){throw invalid("图片文件已损坏或无法读取");}}
    private Path directory(Long id){return root.resolve(id.toString()).normalize();}private long safeSize(Path path){try{return Files.size(path);}catch(IOException ignored){return 0;}}
    private ProductImageException invalid(String message){return new ProductImageException(HttpStatus.BAD_REQUEST,message);}private ProductImageException notFound(){return new ProductImageException(HttpStatus.NOT_FOUND,"图片不存在");}
    private record ImageInfo(String format,int width,int height){}
}
package com.campus.secondhand.marketplace;
import jakarta.persistence.*;
import java.math.BigDecimal; import java.time.LocalDateTime; import java.util.LinkedHashSet; import java.util.Set;
@Entity @Table(name="items") public class Item {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,length=120) private String title; @Column(nullable=false,length=40) private String category;
 @Column(nullable=false,precision=10,scale=2) private BigDecimal price; @Column(length=1000) private String description; @Column(length=255) private String imageUrl;
 @Column(nullable=false,length=40) private String region; @Column(name="seller_id",nullable=false) private Long sellerId;
 @com.fasterxml.jackson.annotation.JsonIgnore @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="seller_id",insertable=false,updatable=false) private SearchableUserProjection sellerProjection;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ItemStatus status=ItemStatus.ON_SALE;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ItemModerationStatus moderationStatus=ItemModerationStatus.VISIBLE;
 @Version @Column(nullable=false) private Long version=0L; @Column(nullable=false) private LocalDateTime createdAt=LocalDateTime.now();
 @ElementCollection(fetch=FetchType.EAGER) @CollectionTable(name="item_tags",joinColumns=@JoinColumn(name="item_id")) @Column(name="tag",nullable=false,length=20) private Set<String> tags=new LinkedHashSet<>();
 public Long getId(){return id;} public void setId(Long v){id=v;} public String getTitle(){return title;} public void setTitle(String v){title=v;} public String getCategory(){return category;} public void setCategory(String v){category=v;} public BigDecimal getPrice(){return price;} public void setPrice(BigDecimal v){price=v;} public String getDescription(){return description;} public void setDescription(String v){description=v;} public String getImageUrl(){return imageUrl;} public void setImageUrl(String v){imageUrl=v;} public String getRegion(){return region;} public void setRegion(String v){region=v;} public Long getSellerId(){return sellerId;} public void setSellerId(Long v){sellerId=v;} public ItemStatus getStatus(){return status;} public void setStatus(ItemStatus v){status=v;} public ItemModerationStatus getModerationStatus(){return moderationStatus;} public void setModerationStatus(ItemModerationStatus v){moderationStatus=v;} public Long getVersion(){return version;} public LocalDateTime getCreatedAt(){return createdAt;} public Set<String> getTags(){return tags;} public void setTags(Set<String> v){tags.clear();if(v!=null)tags.addAll(v);}
 public SearchableUserProjection getSellerProjection(){return sellerProjection;}
}

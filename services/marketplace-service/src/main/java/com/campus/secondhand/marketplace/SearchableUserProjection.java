package com.campus.secondhand.marketplace;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity @Table(name="searchable_user_projection")
public class SearchableUserProjection {
    @Id private Long id;
    @Column(nullable=false,length=80) private String username;
    @Column(length=80) private String nickname;
    @Column(length=40) private String campusRegion;
    @Column(nullable=false) private Integer creditScore;
    private LocalDateTime lastActiveAt;
    @Column(nullable=false,length=20) private String status;
    @Column(nullable=false,length=20) private String role;
    @Column(nullable=false) private LocalDateTime createdAt;
    @Column(nullable=false) private Long sourceVersion;
    @Version @Column(nullable=false) private Long rowVersion=0L;
    @Column(nullable=false) private LocalDateTime updatedAt;

    public void apply(UserPublicProfileChanged event) {
        if (sourceVersion != null && event.version() <= sourceVersion) return;
        id=event.userId(); username=event.username(); nickname=event.nickname(); campusRegion=event.region();
        creditScore=event.creditScore(); lastActiveAt=event.lastActiveAt(); status=event.status(); role=event.role();
        createdAt=event.createdAt(); sourceVersion=event.version(); updatedAt=event.occurredAt();
    }
    public Long getId(){return id;} public String getUsername(){return username;} public String getNickname(){return nickname;}
    public String getCampusRegion(){return campusRegion;} public Integer getCreditScore(){return creditScore;}
    public LocalDateTime getLastActiveAt(){return lastActiveAt;} public String getStatus(){return status;}
    public String getRole(){return role;} public LocalDateTime getCreatedAt(){return createdAt;}
    public Long getSourceVersion(){return sourceVersion;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}

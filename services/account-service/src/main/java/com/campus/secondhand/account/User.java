package com.campus.secondhand.account;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false,unique=true,length=50) private String username;
 @Column(name="password_hash",nullable=false) private String passwordHash;
 @Column(length=80) private String nickname;
 @Column(length=30) private String phone;
 @Column(nullable=false,unique=true,length=254) private String email;
 @Column(nullable=false,length=20) private String role="STUDENT";
 @Column(nullable=false,length=20) private String status="ACTIVE";
 @Column(name="login_failed_count",nullable=false) private Integer loginFailedCount=0;
 @Column(name="locked_until") private LocalDateTime lockedUntil;
 @Column(name="auth_version",nullable=false) private Integer authVersion=0;
 @Column(name="campus_region",length=40) private String campusRegion="学院路校区";
 @Column(name="credit_score",nullable=false) private Integer creditScore=100;
 @Column(name="last_active_at",nullable=false) private LocalDateTime lastActiveAt=LocalDateTime.now();
 @Column(name="created_at",nullable=false,updatable=false) private LocalDateTime createdAt=LocalDateTime.now();
 public Long getId() { return id; }
 public String getUsername() { return username; }
 public void setUsername(String value) { username = value; }
 public String getPasswordHash() { return passwordHash; }
 public void setPasswordHash(String value) { passwordHash = value; }
 public String getNickname() { return nickname; }
 public void setNickname(String value) { nickname = value; }
 public String getPhone() { return phone; }
 public void setPhone(String value) { phone = value; }
 public String getEmail() { return email; }
 public void setEmail(String value) { email = value; }
 public String getRole() { return role; }
 public void setRole(String value) { role = value; }
 public String getStatus() { return status; }
 public void setStatus(String value) { status = value; }
 public Integer getAuthVersion() { return authVersion; }
 public void setAuthVersion(Integer value) { authVersion = value; }
 public Integer getCreditScore() { return creditScore; }
 public String getCampusRegion() { return campusRegion; }
 public void setCampusRegion(String value) { campusRegion = value; }
 public LocalDateTime getLastActiveAt() { return lastActiveAt; }
 public void setLastActiveAt(LocalDateTime value) { lastActiveAt = value; }
 public Integer getLoginFailedCount() { return loginFailedCount; }
 public void setLoginFailedCount(Integer value) { loginFailedCount = value; }
 public LocalDateTime getLockedUntil() { return lockedUntil; }
}

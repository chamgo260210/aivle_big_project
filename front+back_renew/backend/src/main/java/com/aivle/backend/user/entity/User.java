package com.aivle.backend.user.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.entity.UserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String username;

    @Column(unique = true, length = 254)
    private String email;

    @Column(length = 120)
    private String organizationName;
    @Column(length = 120)
    private String departmentName;
    @Column(length = 120)
    private String jobTitle;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(nullable = false)
    private Integer failedLoginCount;

    private LocalDateTime lockedUntil;
    private LocalDateTime lastLoginAt;
    private LocalDateTime passwordChangedAt;
    private LocalDateTime emailVerifiedAt;

    private User(
        String username,
        String email,
        String passwordHash,
        String name, String organizationName, String departmentName, String jobTitle, UserStatus status
    ) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = UserRole.USER;
        this.status = status;
        this.failedLoginCount = 0;
        this.organizationName = organizationName; this.departmentName = departmentName; this.jobTitle = jobTitle;
    }

    public static User register(String username, String email, String passwordHash, String name, String organizationName, String departmentName, String jobTitle) {
        return new User(username, email, passwordHash, name, organizationName, departmentName, jobTitle, UserStatus.ACTIVE);
    }

    /** Compatibility factory for non-auth fixtures; production registration must supply username explicitly. */
    public static User create(String email, String passwordHash, String name) {
        String username = email.split("@", 2)[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        if (username.length() < 4) username = "user-" + Math.abs(email.hashCode());
        return register(username.substring(0, Math.min(username.length(), 30)), email, passwordHash, name, null, null, null);
    }

    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }

    public void recordSuccessfulLogin(LocalDateTime now) {
        this.lastLoginAt = now;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }

    public void updatePasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public void updateProfile(
        String name,
        String email,
        String organizationName,
        String departmentName,
        String jobTitle
    ) {
        this.name = name;
        this.email = email;
        this.organizationName = organizationName;
        this.departmentName = departmentName;
        this.jobTitle = jobTitle;
    }
}

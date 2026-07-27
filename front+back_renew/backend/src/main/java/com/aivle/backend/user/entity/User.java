package com.aivle.backend.user.entity;

import com.aivle.backend.common.entity.BaseEntity;
import com.aivle.backend.common.entity.UserRole;
import com.aivle.backend.common.entity.UserStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

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

    private User(String email, String passwordHash, String name) {
        this(email, passwordHash, name, UserStatus.PENDING);
    }

    private User(
        String email,
        String passwordHash,
        String name,
        UserStatus status
    ) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = UserRole.USER;
        this.status = status;
        this.failedLoginCount = 0;
    }

    public static User create(String email, String passwordHash, String name) {
        return new User(email, passwordHash, name);
    }

    public static User register(String email, String passwordHash, String name) {
        return new User(email, passwordHash, name, UserStatus.ACTIVE);
    }

    public boolean canLogin() {
        return status == UserStatus.ACTIVE;
    }

    public void recordSuccessfulLogin(LocalDateTime now) {
        this.lastLoginAt = now;
        this.failedLoginCount = 0;
        this.lockedUntil = null;
    }
}

package com.onehostel.model;

import java.time.Instant;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import com.onehostel.constants.CollectionNames;
import com.onehostel.constants.CommonEnumHelper.Role;
import com.onehostel.constants.CommonEnumHelper.UserStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Document(collection = CollectionNames.USERS)
public class User {

    @Id
    private String id;

    private String name;

    /** Super admins only: an alternative login identifier to the email. */
    @Indexed(unique = true, sparse = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    /** 10-digit mobile number, no country code. Owners and tenants can log in with it. */
    @Indexed(unique = true, sparse = true)
    private String phoneNumber;

    private String passwordHash;

    /** Super admins only: BCrypt hash of the 6-digit passcode. */
    private String passcodeHash;

    private Role role;

    @Indexed
    private String organizationId;

    /** The hostel (branch) the account belongs to: a tenant's hostel, empty for owners of a group. */
    private String hostelId;

    /** Tenants: their room and bed. {@code bedId} is the unit reference carried in the token. */
    private String roomNumber;

    private String bedId;

    private UserStatus status = UserStatus.ACTIVE;

    /** Administrative suspension, independent of status. */
    private boolean locked;

    private Instant lastLoginAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public User(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    public boolean canLogIn() {
        return status == UserStatus.ACTIVE && !locked;
    }
}

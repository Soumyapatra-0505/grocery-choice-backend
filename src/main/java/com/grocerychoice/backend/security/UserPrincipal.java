package com.grocerychoice.backend.security;

import com.grocerychoice.backend.entity.Role;
import com.grocerychoice.backend.entity.User;
import com.grocerychoice.backend.entity.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

/**
 * Custom UserDetails implementation holding authenticated user identity, role, and fine-grained permissions.
 */
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String email;
    private final String phone;
    private final String fullName;
    private final Role role;
    private final boolean primaryOwner;
    private final UserStatus status;
    private final String designation;
    private final String storeHub;
    private final Set<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id, String email, String phone, String fullName, Role role) {
        this(id, email, phone, fullName, role, false, UserStatus.ACTIVE, null, "Flagship Hub", Collections.emptySet(),
                List.of(new SimpleGrantedAuthority("ROLE_" + (role != null ? role.name() : "CUSTOMER"))));
    }

    public UserPrincipal(Long id, String email, String phone, String fullName, Role role,
                         boolean primaryOwner, UserStatus status, String designation, String storeHub,
                         Set<String> permissions, Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.email = email;
        this.phone = phone;
        this.fullName = fullName;
        this.role = role != null ? role : Role.CUSTOMER;
        this.primaryOwner = primaryOwner;
        this.status = status != null ? status : UserStatus.ACTIVE;
        this.designation = designation;
        this.storeHub = storeHub != null ? storeHub : "Flagship Hub";
        this.permissions = permissions != null ? permissions : Collections.emptySet();
        this.authorities = authorities != null ? authorities : List.of(new SimpleGrantedAuthority("ROLE_" + this.role.name()));
    }

    public static UserPrincipal create(User user) {
        Role role = user.getRole() != null ? user.getRole() : Role.CUSTOMER;
        Set<String> perms = user.getEffectivePermissions();

        List<GrantedAuthority> auths = new ArrayList<>();
        auths.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        for (String perm : perms) {
            auths.add(new SimpleGrantedAuthority("PERM_" + perm));
        }

        return new UserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPhone(),
                user.getFullName(),
                role,
                user.isPrimaryOwner(),
                user.getStatus() != null ? user.getStatus() : UserStatus.ACTIVE,
                user.getDesignation(),
                user.getStoreHub(),
                perms,
                auths
        );
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getFullName() {
        return fullName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isCustomer() {
        return Role.CUSTOMER.equals(this.role);
    }

    public boolean isStaff() {
        return Role.STAFF.equals(this.role);
    }

    public boolean isOwner() {
        return Role.OWNER.equals(this.role);
    }

    public boolean isAdmin() {
        return Role.ADMIN.equals(this.role);
    }

    public boolean isPrimaryOwner() {
        return primaryOwner;
    }

    public UserStatus getStatus() {
        return status;
    }

    public String getDesignation() {
        return designation;
    }

    public String getStoreHub() {
        return storeHub;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public boolean hasPermission(String permission) {
        if (primaryOwner) return true;
        return permissions.contains(permission);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email != null ? email : (phone != null ? phone : String.valueOf(id));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.DISABLED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status != UserStatus.DISABLED;
    }
}

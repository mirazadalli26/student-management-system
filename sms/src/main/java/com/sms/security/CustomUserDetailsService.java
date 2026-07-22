package com.sms.security;

import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.sms.entity.Admin;
import com.sms.entity.Role;
import com.sms.entity.Student;
import com.sms.repository.AdminRepository;
import com.sms.repository.StudentRepository;

/**
 * Loads authentication principals for the login and JWT flows.
 *
 * <p>A username is resolved by checking the {@link AdminRepository} first and
 * then the {@link StudentRepository}. The resolved account is exposed as a
 * Spring Security {@link UserDetails} carrying the stored BCrypt password and a
 * single granted authority derived from the account {@link Role}
 * ({@code ADMIN -> ROLE_ADMIN}, {@code STUDENT -> ROLE_STUDENT}) so that
 * {@code hasRole(...)} checks in the security configuration resolve correctly
 * (R2.1, R2.2, R3.6).</p>
 *
 * <p>The account-status flags (enabled, non-expired, credentials-non-expired,
 * and non-locked) are all {@code true} by default. The lockout policy (5 failed
 * attempts within a 15-minute window, R2.8) is enforced by the authentication
 * controller; the {@link #buildUserDetails} helper accepts an
 * {@code accountNonLocked} flag so that lock state can be integrated here later
 * without reworking the principal construction.</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AdminRepository adminRepository;
    private final StudentRepository studentRepository;

    public CustomUserDetailsService(AdminRepository adminRepository,
                                    StudentRepository studentRepository) {
        this.adminRepository = adminRepository;
        this.studentRepository = studentRepository;
    }

    /**
     * Resolves a username to a {@link UserDetails}, checking administrators
     * first and then students.
     *
     * @param username the account username to resolve
     * @return the matching principal with its stored password and role authority
     * @throws UsernameNotFoundException if neither an admin nor a student has the
     *                                   given username
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Admin admin = adminRepository.findByUsername(username).orElse(null);
        if (admin != null) {
            return buildUserDetails(admin.getUsername(), admin.getPassword(), admin.getRole(), true);
        }

        Student student = studentRepository.findByUsername(username).orElse(null);
        if (student != null) {
            return buildUserDetails(student.getUsername(), student.getPassword(), student.getRole(), true);
        }

        throw new UsernameNotFoundException("No account found for username: " + username);
    }

    /**
     * Builds a {@link UserDetails} for an account.
     *
     * <p>The {@code accountNonLocked} flag is threaded through so the lockout
     * policy can mark a principal as locked once per-account failure tracking is
     * wired in; today callers pass {@code true}.</p>
     *
     * @param username         the account username
     * @param password         the stored BCrypt password hash
     * @param role             the account role, mapped to a {@code ROLE_*} authority
     * @param accountNonLocked whether the account is currently unlocked
     * @return the constructed principal
     */
    private UserDetails buildUserDetails(String username, String password, Role role, boolean accountNonLocked) {
        List<GrantedAuthority> authorities = List.of(toAuthority(role));
        return User.withUsername(username)
                .password(password)
                .authorities(authorities)
                .accountLocked(!accountNonLocked)
                .accountExpired(false)
                .credentialsExpired(false)
                .disabled(false)
                .build();
    }

    /**
     * Maps an account {@link Role} to its Spring Security authority name so that
     * {@code hasRole("ADMIN")} / {@code hasRole("STUDENT")} checks match.
     *
     * @param role the account role
     * @return the {@code ROLE_}-prefixed granted authority
     */
    private GrantedAuthority toAuthority(Role role) {
        return new SimpleGrantedAuthority("ROLE_" + role.name());
    }
}

package com.ximofam.graduation_project.users.securities;

import com.ximofam.graduation_project.common.helpers.utils.Utils;
import com.ximofam.graduation_project.common.securities.CustomUserDetails;
import com.ximofam.graduation_project.users.entities.User;
import com.ximofam.graduation_project.users.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MyUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(usernameOrEmail)
                .or(() -> userRepository.findByEmail(usernameOrEmail))
                .orElseThrow(() -> new UsernameNotFoundException(usernameOrEmail));

        List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(Utils.getRole(user.getRole())));

        return CustomUserDetails.builder(user.getUsername(), user.getPasswordHash())
                .userId(user.getId())
                .accountNonLocked(!user.isLocked())
                .enabled(user.isEnable())
                .authorities(authorities)
                .build();
    }
}

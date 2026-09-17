package com.sethdevkh.restapi.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.sethdevkh.restapi.domain.User;
import com.sethdevkh.restapi.domain.UserRepository;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads the persisted user for the JWT principal. Team and role come from this
     * row, not from the request body.
     */
    public User requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new IllegalStateException("No authenticated user");
        }
        return userRepository.findById(principal.userId())
                .orElseThrow(() -> new IllegalStateException("Authenticated user is missing"));
    }
}

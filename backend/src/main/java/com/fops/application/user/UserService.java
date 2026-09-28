package com.fops.application.user;

import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User createUser(String name, String email) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Name is required");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException("Email is required");
        }

        String normalizedEmail = email.trim().toLowerCase();
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new DuplicateResourceException("A user with this email already exists: " + normalizedEmail);
        }

        return userRepository.save(new User(name.trim(), normalizedEmail));
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return userRepository.findAll();
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User " + id + " not found"));
    }
}

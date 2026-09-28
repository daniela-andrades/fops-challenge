package com.fops.application.user;

import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;
import com.fops.domain.exception.ResourceInUseException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.OrderRepository;
import com.fops.infrastructure.persistence.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;

    public UserService(UserRepository userRepository, OrderRepository orderRepository) {
        this.userRepository = userRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public User createUser(String name, String email) {
        String normalizedEmail = validate(name, email);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw duplicateEmail(normalizedEmail);
        }

        return userRepository.save(new User(name.trim(), normalizedEmail));
    }

    @Transactional
    public User updateUser(Long id, String name, String email) {
        String normalizedEmail = validate(name, email);
        User user = findById(id);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(normalizedEmail, id)) {
            throw duplicateEmail(normalizedEmail);
        }

        user.setName(name.trim());
        user.setEmail(normalizedEmail);
        return user;
    }

    /**
     * Users who placed orders are kept: their orders reference them and receive their completion emails.
     */
    @Transactional
    public void deleteUser(Long id) {
        User user = findById(id);
        if (orderRepository.existsByUserId(id)) {
            throw new ResourceInUseException("User " + id + " has orders and cannot be deleted");
        }
        userRepository.delete(user);
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

    private static String validate(String name, String email) {
        if (name == null || name.isBlank()) {
            throw new BusinessRuleException("Name is required");
        }
        if (email == null || email.isBlank()) {
            throw new BusinessRuleException("Email is required");
        }
        return email.trim().toLowerCase();
    }

    private static DuplicateResourceException duplicateEmail(String email) {
        return new DuplicateResourceException("A user with this email already exists: " + email);
    }
}

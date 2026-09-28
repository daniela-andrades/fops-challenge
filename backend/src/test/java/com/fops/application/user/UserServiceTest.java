package com.fops.application.user;

import com.fops.domain.exception.BusinessRuleException;
import com.fops.domain.exception.DuplicateResourceException;
import com.fops.domain.exception.ResourceInUseException;
import com.fops.domain.exception.ResourceNotFoundException;
import com.fops.domain.model.User;
import com.fops.infrastructure.persistence.OrderRepository;
import com.fops.infrastructure.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.fops.support.TestData.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderRepository orderRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void createsUserWithTrimmedNameAndNormalizedEmail() {
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User user = userService.createUser("  Ana  ", "  Ana@Example.COM ");

        assertThat(user.getName()).isEqualTo("Ana");
        assertThat(user.getEmail()).isEqualTo("ana@example.com");
    }

    @Test
    void rejectsDuplicateEmailIgnoringCase() {
        when(userRepository.existsByEmailIgnoreCase("ana@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("Ana", "ANA@example.com"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(userRepository, never()).save(any());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "   ")
    void requiresNameAndEmail(String blank) {
        assertThatThrownBy(() -> userService.createUser(blank, "a@b.c")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> userService.createUser("Ana", blank)).isInstanceOf(BusinessRuleException.class);
    }

    @Test
    void updatesNameAndNormalizedEmail() {
        User existing = user(4);
        when(userRepository.findById(4L)).thenReturn(Optional.of(existing));

        User updated = userService.updateUser(4L, " Ana Maria ", " Ana.M@Example.com ");

        assertThat(updated.getName()).isEqualTo("Ana Maria");
        assertThat(updated.getEmail()).isEqualTo("ana.m@example.com");
        verify(userRepository).existsByEmailIgnoreCaseAndIdNot("ana.m@example.com", 4L);
    }

    @Test
    void updateRejectsAnEmailUsedByAnotherUser() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(user(4)));
        when(userRepository.existsByEmailIgnoreCaseAndIdNot("taken@example.com", 4L)).thenReturn(true);

        assertThatThrownBy(() -> userService.updateUser(4L, "Ana", "taken@example.com"))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deletesAUserWithoutOrders() {
        User existing = user(4);
        when(userRepository.findById(4L)).thenReturn(Optional.of(existing));

        userService.deleteUser(4L);

        verify(userRepository).delete(existing);
    }

    @Test
    void refusesToDeleteAUserWithOrders() {
        when(userRepository.findById(4L)).thenReturn(Optional.of(user(4)));
        when(orderRepository.existsByUserId(4L)).thenReturn(true);

        assertThatThrownBy(() -> userService.deleteUser(4L))
                .isInstanceOf(ResourceInUseException.class)
                .hasMessage("User 4 has orders and cannot be deleted");
        verify(userRepository, never()).delete(any());
    }

    @Test
    void findByIdFailsWithNotFound() {
        when(userRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("User 9 not found");
    }
}

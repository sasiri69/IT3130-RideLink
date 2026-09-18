package com.ridelink.account.service;

import com.ridelink.account.exception.UserNotFoundException;
import com.ridelink.account.model.Role;
import com.ridelink.account.model.User;
import com.ridelink.account.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId("6aaba60b028e24e9d71b0a8c");
        sampleUser.setFirstName("Kamal");
        sampleUser.setLastName("Perera");
        sampleUser.setEmail("kamal@gmail.com");
        sampleUser.setRole(Role.PASSENGER);
    }

    @Test
    @DisplayName("Should return user when valid ID is provided")
    void getUserById_Success() {
        when(userRepository.findById("6aaba60b028e24e9d71b0a8c"))
                .thenReturn(Optional.of(sampleUser));

        User foundUser = userService.getUserById("6aaba60b028e24e9d71b0a8c");

        assertNotNull(foundUser);
        assertEquals("kamal@gmail.com", foundUser.getEmail());
        verify(userRepository, times(1)).findById("6aaba60b028e24e9d71b0a8c");
    }

    @Test
    @DisplayName("Should throw UserNotFoundException when user ID does not exist")
    void getUserById_NotFound() {
        when(userRepository.findById("invalid_id"))
                .thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> {
            userService.getUserById("invalid_id");
        });

        verify(userRepository, times(1)).findById("invalid_id");
    }
}
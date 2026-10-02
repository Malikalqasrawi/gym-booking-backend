package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.UserResponse;
import com.mycompany.gymbooking.model.User;
import com.mycompany.gymbooking.phone.PhoneNumbers;
import com.mycompany.gymbooking.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Changes to the current user's own details. */
@Service
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * E.g. right after signing up with Google, which doesn't share a phone number. A new number has
     * to be confirmed by SMS again before the next booking.
     */
    @Transactional
    public UserResponse updatePhone(Long userId, String phone) {
        User user = userRepository.findById(userId).orElseThrow();
        user.changePhone(PhoneNumbers.toInternational(phone));
        return UserResponse.from(userRepository.save(user));
    }
}

package com.mycompany.gymbooking.controller;

import com.mycompany.gymbooking.dto.AuthResponse;
import com.mycompany.gymbooking.dto.ChangePasswordRequest;
import com.mycompany.gymbooking.dto.PhoneRequest;
import com.mycompany.gymbooking.dto.RecoveryCodesResponse;
import com.mycompany.gymbooking.dto.TwoFactorCodeRequest;
import com.mycompany.gymbooking.dto.TwoFactorDisableRequest;
import com.mycompany.gymbooking.dto.TwoFactorSetupRequest;
import com.mycompany.gymbooking.dto.TwoFactorSetupResponse;
import com.mycompany.gymbooking.dto.UserResponse;
import com.mycompany.gymbooking.security.SecurityUser;
import com.mycompany.gymbooking.service.AuthService;
import com.mycompany.gymbooking.service.ProfileService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final AuthService authService;
    private final ProfileService profileService;

    public UserController(AuthService authService, ProfileService profileService) {
        this.authService = authService;
        this.profileService = profileService;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal SecurityUser currentUser) {
        return UserResponse.from(currentUser.getUser());
    }

    @PutMapping("/me/phone")
    public UserResponse updatePhone(@AuthenticationPrincipal SecurityUser currentUser,
                                    @Valid @RequestBody PhoneRequest request) {
        return profileService.updatePhone(currentUser.getUser().getId(), request.phone());
    }

    /** Ends the sessions on other devices; this device gets a new session in the response. */
    @PostMapping("/me/password")
    public AuthResponse changePassword(@AuthenticationPrincipal SecurityUser currentUser,
                                       @Valid @RequestBody ChangePasswordRequest request) {
        return authService.changePassword(currentUser.getUser().getId(), request);
    }

    @PostMapping("/me/logout-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logoutEverywhere(@AuthenticationPrincipal SecurityUser currentUser) {
        authService.logoutEverywhere(currentUser.getUser().getId());
    }

    @PostMapping("/me/2fa/setup")
    public TwoFactorSetupResponse startTwoFactorSetup(@AuthenticationPrincipal SecurityUser currentUser,
                                                      @Valid @RequestBody TwoFactorSetupRequest request) {
        return authService.startTwoFactorSetup(currentUser.getUser().getId(), request);
    }

    @PostMapping("/me/2fa/confirm")
    public RecoveryCodesResponse confirmTwoFactorSetup(@AuthenticationPrincipal SecurityUser currentUser,
                                                       @Valid @RequestBody TwoFactorCodeRequest request) {
        return authService.confirmTwoFactorSetup(currentUser.getUser().getId(), request);
    }

    @PostMapping("/me/2fa/disable")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disableTwoFactor(@AuthenticationPrincipal SecurityUser currentUser,
                                 @Valid @RequestBody TwoFactorDisableRequest request) {
        authService.disableTwoFactor(currentUser.getUser().getId(), request);
    }
}

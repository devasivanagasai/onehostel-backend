package com.onehostel.access.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.onehostel.access.dto.ProfileDtos.ProfileResponse;
import com.onehostel.access.dto.ProfileDtos.ProfileUpdateRequest;
import com.onehostel.access.service.ProfileService;
import com.onehostel.common.annotation.AuthenticatedUser;
import com.onehostel.common.annotation.ResponseMessage;
import com.onehostel.common.vo.CurrentUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/** My Profile: any signed-in user, on their own account only (the id comes from the verified token). */
@RestController
@RequestMapping("/api/v1/access/me")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    public ProfileResponse profile(@AuthenticatedUser CurrentUser caller) {
        return profileService.get(caller.userId());
    }

    @PutMapping
    @ResponseMessage("Profile updated successfully")
    public ProfileResponse update(@AuthenticatedUser CurrentUser caller, @Valid @RequestBody ProfileUpdateRequest request) {
        return profileService.update(caller.userId(), request);
    }
}

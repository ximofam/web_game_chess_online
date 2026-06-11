package com.ximofam.graduation_project.admin.controllers;

import com.ximofam.graduation_project.common.securities.CustomUserDetails;
import com.ximofam.graduation_project.users.dtos.request.UpdateUserRequest;
import com.ximofam.graduation_project.users.dtos.response.UserDetailResponse;
import com.ximofam.graduation_project.users.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminHomeController {
    private final UserService userService;

    @GetMapping("/login")
    public String loginView() {
        return "admin/auth/login";
    }

    @GetMapping("/")
    public String home() {
        return "admin/dashboard";
    }

    @GetMapping("/profile/my")
    public String myProfile(Model model, @AuthenticationPrincipal CustomUserDetails principal) {
        UserDetailResponse res = userService.getUserById(principal.getUserId());
        model.addAttribute("user", res);
        return "admin/profile";
    }

    @PostMapping("/profile/update")
    public String updateMyProfile(@ModelAttribute UpdateUserRequest request,
                                  @AuthenticationPrincipal CustomUserDetails principal,
                                  RedirectAttributes redirectAttributes) {
        try {
            userService.updateUser(principal.getUserId(), request);

            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật thông tin cá nhân thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Có lỗi xảy ra: " + e.getMessage());
        }
        return "redirect:/admin/profile/my";
    }

    @PostMapping("/profile/avatar")
    public String uploadMyAvatar(@AuthenticationPrincipal CustomUserDetails principal,
                                 @RequestParam("file") MultipartFile file,
                                 RedirectAttributes redirectAttributes) {
        try {
            userService.uploadAvatar(principal.getUserId(), file);
            redirectAttributes.addFlashAttribute("successMessage", "Cập nhật avatar thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Có lỗi xảy ra: " + e.getMessage());
        }
        return "redirect:/admin/profile/my";
    }
}

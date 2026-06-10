package com.ximofam.graduation_project.admin.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminHomeController {
    @GetMapping("/login")
    public String loginView() {
        return "admin/auth/login";
    }

    @GetMapping("/")
    public String home() {
        return "admin/dashboard";
    }
}

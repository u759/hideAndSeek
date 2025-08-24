package com.hideandseek.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminViewController {

    @GetMapping("/admin")
    public String adminPanel() {
    return "forward:/admin-login.html";
    }
    
    @GetMapping("/")
    public String home() {
    return "forward:/admin-login.html";
    }
}

package cn.edu.lab.auth.dto;

public record LoginResponse(String accessToken, int expiresIn, UserView user) {}

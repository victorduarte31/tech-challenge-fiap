package br.com.oficina.application.dto;

public record LoginResponseDto(
    String token,
    String username,
    String role,
    long expiresIn
) {}

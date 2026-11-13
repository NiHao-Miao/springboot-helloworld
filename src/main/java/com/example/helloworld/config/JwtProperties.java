package com.example.helloworld.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "app.jwt.secret must not be blank") String secret,
        @NotNull(message = "app.jwt.expiration must be set") Duration expiration
) {
}

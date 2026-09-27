package com.ridelink.ride.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI configuration for Ride Management Service documentation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rideServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink – Ride Management Service API")
                        .description("Backend Microservice for Ride Booking, Driver Assignment, and Lifecycle State Transitions (Member 3 – IT24101290).")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Member 3 – IT24101290")
                                .email("it24101290@my.sliit.lk"))
                        .license(new License().name("Academic Use Only – IT3130")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter Bearer JWT token issued by Account Service")));
    }
}

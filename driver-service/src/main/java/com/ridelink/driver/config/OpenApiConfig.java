package com.ridelink.driver.config;

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
 * OpenAPI configuration for generating Swagger UI documentation.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI driverServiceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RideLink – Driver & Vehicle Service API")
                        .description("Backend Microservice for Driver Operational Profiles, Vehicle Management, Availability, Location Tracking, and Dispatch Querying.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Driver & Vehicle Service Team")
                                .email("driver-service@ridelink.com"))
                        .license(new License().name("Apache 2.0")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .name("bearerAuth")
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter JWT Bearer token issued by Account Service")));
    }
}

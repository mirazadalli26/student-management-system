package com.sms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Student Management System backend.
 *
 * <p>Boots the Spring context which wires the security layer, REST controllers,
 * JPA persistence, and the default-administrator provisioning at startup, and
 * serves the static frontend from {@code src/main/resources/static}.
 */
@SpringBootApplication
public class StudentManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudentManagementApplication.class, args);
    }
}

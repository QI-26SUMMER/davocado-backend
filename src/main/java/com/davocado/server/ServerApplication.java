package com.davocado.server;

import jakarta.annotation.PostConstruct;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * D-avocado backend entry point.
 *
 * <p>Responsibilities of this Spring service: authentication, CRUD, D-day calculation,
 * notification scheduling and DB access. Image inference (ResNet-18) is handled by a
 * separate Python sidecar service, called over HTTP (integration added in a later step).
 */
@SpringBootApplication
public class ServerApplication {

    /** Handle all time in UTC regardless of the host machine's timezone. */
    @PostConstruct
    void useUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    public static void main(String[] args) {
        SpringApplication.run(ServerApplication.class, args);
    }
}

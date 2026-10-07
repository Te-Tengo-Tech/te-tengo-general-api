package tech.tetengo.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Te Tengo backend API (the "Backend API del sistema" container of the C4 model). */
@SpringBootApplication
public class TeTengoGeneralApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeTengoGeneralApiApplication.class, args);
    }
}

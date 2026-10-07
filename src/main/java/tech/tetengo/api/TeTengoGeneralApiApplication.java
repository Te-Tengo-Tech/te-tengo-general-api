package tech.tetengo.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Backend API del sistema Te Tengo (contenedor «Backend API del sistema» del modelo C4). */
@SpringBootApplication
public class TeTengoGeneralApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeTengoGeneralApiApplication.class, args);
    }
}

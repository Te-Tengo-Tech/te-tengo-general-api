package tech.tetengo.api.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Test doubles shared by every integration test (one Spring context for the whole suite). */
@TestConfiguration(proxyBeanMethods = false)
public class SoporteDePruebas {

    @Bean
    @Primary
    RelojDePrueba relojDePrueba() {
        return new RelojDePrueba();
    }

    @Bean
    @Primary
    CorreoDePrueba correoDePrueba() {
        return new CorreoDePrueba();
    }

    @Bean
    @Primary
    PushDePrueba pushDePrueba() {
        return new PushDePrueba();
    }

    @Bean
    @Primary
    AlmacenamientoDePrueba almacenamientoDePrueba() {
        return new AlmacenamientoDePrueba();
    }

    @Bean
    @Primary
    TransmisionDePrueba transmisionDePrueba() {
        return new TransmisionDePrueba();
    }
}

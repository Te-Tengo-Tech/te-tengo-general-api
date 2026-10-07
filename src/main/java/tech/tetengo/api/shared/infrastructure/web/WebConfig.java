package tech.tetengo.api.shared.infrastructure.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                .useRequestHeader(ApiVersioning.CABECERA)
                .setDefaultVersion(ApiVersioning.V1)
                .addSupportedVersions(ApiVersioning.V1);
    }
}

package com.ticketya.platform.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/** Valores por defecto de la plataforma, con la MENOR prioridad: cualquier servicio puede sobrescribirlos. */
public class PlatformDefaultsPostProcessor implements EnvironmentPostProcessor {

    static final String SOURCE_NAME = "ticketyaPlatformDefaults";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("ticketya.platform.internal-token", "${INTERNAL_TOKEN:dev-internal-token}");
        // el correlationId aparece en cada línea de log (después del nivel)
        defaults.put("logging.pattern.level", "%5p [%X{correlationId:-}]");
        environment.getPropertySources().addLast(new MapPropertySource(SOURCE_NAME, defaults));
    }
}

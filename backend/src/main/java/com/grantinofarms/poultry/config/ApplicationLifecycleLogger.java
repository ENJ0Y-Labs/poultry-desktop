package com.grantinofarms.poultry.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class ApplicationLifecycleLogger {
    private static final Logger log = LoggerFactory.getLogger(ApplicationLifecycleLogger.class);

    private final Environment environment;

    public ApplicationLifecycleLogger(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String[] profiles = environment.getActiveProfiles();
        String profile = profiles.length == 0 ? environment.getProperty("spring.profiles.default", "default") : String.join(",", profiles);
        log.info("application_started profile={} service={}",
                profile,
                environment.getProperty("spring.application.name", "poultry-backend"));
    }

    @EventListener(ContextClosedEvent.class)
    public void onClosed() {
        log.info("application_shutdown service={}",
                environment.getProperty("spring.application.name", "poultry-backend"));
    }
}

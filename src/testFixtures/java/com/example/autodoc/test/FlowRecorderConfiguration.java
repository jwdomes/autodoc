package com.example.autodoc.test;

import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/** Import into a @SpringBootTest that runs scenarios with autodoc.observations.enabled=true. */
@TestConfiguration(proxyBeanMethods = false)
public class FlowRecorderConfiguration {

    @Bean
    FlowRecorder flowRecorder(ObservationRegistry registry, Environment environment) {
        FlowRecorder recorder = new FlowRecorder(environment);
        registry.observationConfig().observationHandler(recorder.handler);
        return recorder;
    }
}

package com.example.flowcap.boot;

import com.example.flowcap.FlowStep;
import io.micrometer.observation.ObservationRegistry;
import java.util.function.Supplier;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Role;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;

/**
 * Registers nothing unless flowcap.observations.enabled=true, so @FlowStep stays inert metadata in
 * production: no beans, no proxies, no expression parsing. When enabled, an ObservationRegistry bean is
 * required (Actuator provides one).
 */
@AutoConfiguration
@ConditionalOnProperty(name = "flowcap.observations.enabled", havingValue = "true")
public class FlowcapAutoConfiguration {

    /** Static with the infrastructure role so the auto-proxy creator picks it up early, like @Transactional. */
    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    static Advisor flowStepAdvisor(ObjectProvider<ObservationRegistry> registry, Environment environment) {
        return advisor(registry::getObject, environment);
    }

    static Advisor advisor(Supplier<ObservationRegistry> registry, PropertyResolver properties) {
        return new DefaultPointcutAdvisor(AnnotationMatchingPointcut.forMethodAnnotation(FlowStep.class),
                new FlowStepInterceptor(registry, properties));
    }
}

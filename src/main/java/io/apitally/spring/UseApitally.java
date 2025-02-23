package io.apitally.spring;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Import;

@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Import({ ApitallyAutoConfiguration.class })
public @interface UseApitally {
    Class<? extends Annotation> annotation() default Annotation.class;
}

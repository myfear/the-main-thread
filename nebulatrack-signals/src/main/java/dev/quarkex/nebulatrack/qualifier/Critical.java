package dev.quarkex.nebulatrack.qualifier;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.enterprise.util.AnnotationLiteral;
import jakarta.inject.Qualifier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Qualifier
@Retention(RUNTIME)
@Target({ FIELD, METHOD, PARAMETER, TYPE })
public @interface Critical {

    final class Literal extends AnnotationLiteral<Critical> implements Critical {
        public static final Literal INSTANCE = new Literal();
    }
}

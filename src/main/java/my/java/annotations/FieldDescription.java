package my.java.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Аннотация для описания полей сущностей.
 */
@Retention(RetentionPolicy.RUNTIME) // Аннотация доступна во время выполнения.
@Target(ElementType.FIELD)
public @interface FieldDescription {
    String value();
    boolean skipMapping() default false;
}

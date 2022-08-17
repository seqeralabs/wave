package io.seqera.wave.config
import javax.validation.Constraint;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */

@Retention(RUNTIME)
@Constraint(validatedBy = [])
@interface MountPathValidator {
    String message() default "invalid build configuration"
}

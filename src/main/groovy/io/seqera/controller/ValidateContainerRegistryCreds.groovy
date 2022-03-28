package io.seqera.controller

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank

import io.micronaut.core.annotation.Introspected

@Introspected
class ValidateContainerRegistryCreds {
    @NotBlank
    String userName
    @NotBlank
    String password

}

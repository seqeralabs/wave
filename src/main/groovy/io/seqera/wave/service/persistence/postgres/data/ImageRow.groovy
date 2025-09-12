package io.seqera.wave.service.persistence.postgres.data

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.MappedEntity
import jakarta.persistence.Table

/**
 * Model a row entry in the {@code wave_image} table
 */
@MappedEntity
@Introspected
@CompileStatic
@Table(name = "wave_image")
class SourceImageInfoRow {
    String buildId
    String image
    String digest
    String scanId
}

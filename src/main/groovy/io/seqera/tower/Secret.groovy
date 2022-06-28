package io.seqera.tower

import java.time.Instant
import java.time.OffsetDateTime
import javax.validation.constraints.NotNull

import groovy.transform.CompileStatic
import io.micronaut.data.annotation.DateCreated
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Lob
import jakarta.persistence.Table
import jakarta.persistence.Version
/**
 * Moder a Tower 'secret' entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Table(name="TW_SECRET")
@Entity
class Secret {

    @Id
    String id

    @NotNull
    @Lob
    // The 'blob' type is not available in all databases. MySQL and H2 support it.
    @Column(columnDefinition = 'blob')
    byte[] secure

    Instant expire

    OffsetDateTime lastAccess

    @DateCreated
    @Column(nullable = false, updatable = false)
    OffsetDateTime dateCreated

    @Version
    @Column(name = 'version', nullable = false)
    private long dbVersion

}

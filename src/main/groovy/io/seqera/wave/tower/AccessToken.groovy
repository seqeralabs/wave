package io.seqera.wave.tower

import java.time.Instant
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.DateCreated
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
/**
 * Model a tower access token
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false, includes = 'id,name')
@Table(name="tw_access_token")
@Entity
@CompileStatic
class AccessToken {

    @Id
    Long id

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    User user

    @NotNull
    @NotBlank
    @Size(max = 50)
    String name

    /**
     * Random salt to generate a JWT-like token to be used as auth bearer token.
     * Once `token` is completely removed, `salt` should be made not-nullable.
     */
    @Size(max = 16)
    @JsonIgnore
    byte[] secret

    @Nullable
    Instant lastUsed

    @DateCreated
    @Column(nullable = false, updatable = false)
    Instant dateCreated

    @Version
    @Column(name = 'version', nullable = false)
    private long dbVersion

}

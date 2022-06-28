package io.seqera.tower


import java.time.Instant
import java.time.OffsetDateTime
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.CompileStatic
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
/**
 * Model a tower user
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Table(name="TW_USER")
@Entity
class User {

    @Id
    Long id

    @OneToMany(mappedBy = 'user', cascade = CascadeType.PERSIST, orphanRemoval = true)
    @JsonIgnore
    Set<AccessToken> accessTokens = new HashSet<>()

    @Column(unique = true)
    @NotNull
    @Size(max = 40)
    String userName

    @Column(unique = true)
    @NotNull
    @Size(max = 255)
    String email

    @JsonIgnore
    @Column(unique = true)
    @Size(max = 40)
    String authToken

    @JsonIgnore
    Instant authTime

    @Size(max = 100)
    String firstName

    @Size(max = 100)
    String lastName

    @Size(max = 100)
    String organization

    @Size(max = 1000)
    String description

    String avatar

    @NotNull
    Boolean trusted

    @JsonIgnore
    Boolean disabled

    Boolean notification

    /**
     * Indicates if the user has accepted the terms of use.
     * A value of null denotes the user has not defined her options yet according to GDPR legislation.
     */
    Boolean termsOfUseConsent

    /**
     * Indicates if the user has accepted the marketing consent agreement.
     * A value of null denotes the user has not defined her options yet according to GDPR legislation.
     */
    Boolean marketingConsent

    String options

    OffsetDateTime lastAccess

    @DateCreated
    @Column(nullable = false, updatable = false)
    OffsetDateTime dateCreated

    @DateUpdated
    @Column(nullable = false)
    OffsetDateTime lastUpdated

    boolean deleted

    @Version
    @Column(name = 'version', nullable = false)
    private long dbVersion

}

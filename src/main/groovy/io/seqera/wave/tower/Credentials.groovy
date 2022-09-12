package io.seqera.wave.tower


import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.micronaut.core.annotation.Nullable
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
/**
 * Model a Tower 'credentials' entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false, includes = 'id,name,organizationId,workspaceId')
@Table(name="tw_credentials")
@Entity
@CompileStatic
class Credentials {

    @Id
    String id

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    User user

    @Nullable
    Long organizationId

    @Nullable
    Long workspaceId

    /**
     * Credentials display name
     */
    @NotNull
    String name


    /**
     * The credentials provider i.e. `aws`, `google`, etc
     */
    @NotNull
    String provider


    /**
     * Soft deletion
     */
    @Nullable
    Boolean deleted


    @Version
    @Column(name = 'version', nullable = false)
    private long dbVersion

    /**
     * Random security salt associated to these credentials
     */
    @NotNull
    @Size(max = 16)
    byte[] salt

    @Column(name='keys_meta')
    String keys

}

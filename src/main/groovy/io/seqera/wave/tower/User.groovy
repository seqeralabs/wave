package io.seqera.wave.tower

import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

import groovy.transform.CompileStatic
import groovy.transform.ToString
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
/**
 * Model a tower user
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false, includes = 'id,userName,email')
@CompileStatic
@Table(name="tw_user")
@Entity
class User {

    @Id
    Long id

    @Column(unique = true)
    @NotNull
    @Size(max = 40)
    String userName

    @Column(unique = true)
    @NotNull
    @Size(max = 255)
    String email

    boolean deleted

    @Version
    @Column(name = 'version', nullable = false)
    private long dbVersion

}

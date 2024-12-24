package io.seqera.wave.service.aws.cache

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.wave.encoder.MoshiExchange
/**
 * Implement a tiered cache for AWS ECR client
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@EqualsAndHashCode
@ToString(includePackage = false, includeNames = true)
class Token  implements MoshiExchange {
    String value

    Token(String value) {
        this.value = value
    }
}

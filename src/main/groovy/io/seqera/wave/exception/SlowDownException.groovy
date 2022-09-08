package io.seqera.wave.exception

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
@InheritConstructors
class SlowDownException extends WaveException implements HttpError{

}

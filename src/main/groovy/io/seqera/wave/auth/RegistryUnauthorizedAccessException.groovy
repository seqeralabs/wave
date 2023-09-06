/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.auth

import groovy.transform.CompileStatic
import io.seqera.wave.exception.WaveException

/**
 * Exception throw when the registry authorization failed
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RegistryUnauthorizedAccessException extends WaveException {

    private String response
    private Integer status

    RegistryUnauthorizedAccessException(String message, Integer status=null, String response=null) {
        super(message)
        this.status = status
        this.response = response
    }

    String getResponse() {
        return response
    }

    @Override
    String getMessage() {
        def result = super.getMessage()
        if( status!=null )
            result += " - HTTP status=$status"
        if( response )
            result += " - response=$response"
        return result
    }
}

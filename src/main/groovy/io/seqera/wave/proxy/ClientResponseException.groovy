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

package io.seqera.wave.proxy

import java.net.http.HttpRequest

import groovy.transform.CompileStatic

/**
 * Model an invalid response got by the registry client client
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ClientResponseException extends Exception {

    private HttpRequest request

    HttpRequest getRequest() { request }

    ClientResponseException(String message, HttpRequest request) {
        super(message)
        this.request = request
    }

}

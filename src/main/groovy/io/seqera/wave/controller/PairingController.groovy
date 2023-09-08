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

package io.seqera.wave.controller

import javax.validation.ConstraintViolationException
import javax.validation.Valid

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.convert.exceptions.ConversionErrorException
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Post
import io.micronaut.validation.Validated
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.exchange.PairingRequest
import io.seqera.wave.exchange.PairingResponse
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.validation.ValidationService
import io.seqera.wave.tower.client.TowerClient
import jakarta.inject.Inject
import static io.seqera.wave.WaveDefault.TOWER
/**
 * Allow a remote Tower instance to register itself
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/")
@Validated
class PairingController {

    @Inject
    private PairingService securityService

    @Inject
    private TowerClient tower

    @Inject
    private ValidationService validationService

    @Post('/pairing')
    HttpResponse<PairingResponse> pairService(@Valid @Body PairingRequest req) {
        validateRequest(req)
        final key = securityService.acquirePairingKey(req.service, req.endpoint)
        return HttpResponse.ok(key)
    }

    protected void validateRequest(PairingRequest req) {
        if( req.service != TOWER )
            throw new BadRequestException("Unknown pairing service: $req.service")
        // validate endpoint
        final err = validationService.checkEndpoint(req.endpoint)
        if( err )
            throw new BadRequestException(err)
    }

    @Error(exception = ConstraintViolationException)
    HttpResponse onValidationFailure(HttpRequest _req, ConstraintViolationException e) {
        return HttpResponse.badRequest(e.message)
    }

    // Used if the body provides an invalid JSON
    @Error(exception = ConversionErrorException)
    HttpResponse onConversionFailure(HttpRequest _req,ConversionErrorException e) {
        return HttpResponse.badRequest(e.message)
    }

}

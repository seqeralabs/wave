/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.controller

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
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Valid
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
@Deprecated
class PairingController {

    @Inject
    private PairingService securityService

    @Inject
    private TowerClient tower

    @Inject
    private ValidationService validationService

    @Post('/pairing')
    HttpResponse<PairingResponse> pairService(@Valid @Body PairingRequest req) {
        log.warn "Wave HTTP-based paring has been deprecated - origin: ${req.endpoint}"
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

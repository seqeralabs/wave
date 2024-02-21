/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.QueryValue
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.service.packages.PackagesService
import io.seqera.wave.service.packages.model.PackagesResponse
import jakarta.inject.Inject
/**
 * Implements controller interface for packages API
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/v1alpha1/packages")
@ExecuteOn(TaskExecutors.IO)
@Requires(property = 'wave.packages.enabled', value = 'true')
class PackagesController {

    @Inject
    PackagesService packagesService

    @Get('/conda')
    HttpResponse<?> list(@Nullable @QueryValue String search,
                         @Nullable @QueryValue List<String> channels) {
        return HttpResponse.ok(new PackagesResponse(packagesService.findCondaPackage(search, channels)))
    }

    @Get('/conda/refresh')
    String update() {
        packagesService.fetchCondaPackages()
        return 'OK'
    }

}

/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.controller.v1

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.annotation.Controller
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.api.v1.model.ContainerInspectConfig
import io.seqera.wave.api.v1.model.ContainerInspectConfigConfig
import io.seqera.wave.api.v1.model.ContainerInspectRequest
import io.seqera.wave.api.v1.model.ContainerInspectResponse
import io.seqera.wave.api.v1.model.ContainerInspectResponseContainer
import io.seqera.wave.api.v1.model.Manifest
import io.seqera.wave.api.v1.model.ManifestConfig
import io.seqera.wave.api.v1.model.ManifestLayer
import io.seqera.wave.api.v1.model.RootFS
import io.seqera.wave.api.v1.spec.InspectionsApiSpec
import io.seqera.wave.core.spec.ConfigSpec
import io.seqera.wave.core.spec.ContainerSpec
import io.seqera.wave.core.spec.ManifestSpec
import io.seqera.wave.core.spec.ObjectRef
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.UserService
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.service.pairing.PairingService
import jakarta.inject.Inject

import static io.micronaut.http.HttpStatus.NOT_FOUND
import static io.seqera.wave.util.ContainerHelper.patchPlatformEndpoint

/**
 * Implements the v1 container inspect endpoint POST /w1/inspections.
 *
 * Supports both anonymous (no Tower token) and authenticated access. When
 * {@code towerAccessToken} is present the same pairing/user-lookup flow as
 * the alpha controller is used to derive a {@link PlatformId}.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
class InspectionsV1Controller implements InspectionsApiSpec {

    @Inject
    private ContainerInspectService inspectService

    @Inject
    private PairingService pairingService

    @Inject
    private UserService userService

    @Inject
    @Value('${wave.server.url}')
    private String serverUrl

    @Inject
    @Value('${tower.endpoint.url:`https://api.cloud.seqera.io`}')
    private String towerEndpointUrl

    @Override
    ContainerInspectResponse inspectContainer(ContainerInspectRequest req) {
        final identity = resolveIdentity(req)
        final spec = inspectService.containerOrIndexSpec(req.getContainerImage(), null, identity)
        if( spec == null || spec.getContainer() == null )
            throw new HttpStatusException(NOT_FOUND, "Container not found")
        return toV1Response(spec.getContainer())
    }

    protected PlatformId resolveIdentity(ContainerInspectRequest req) {
        if( !req.getTowerAccessToken() )
            return PlatformId.NULL
        // resolve the effective endpoint
        final endpoint = req.getTowerEndpoint()
                ? patchPlatformEndpoint(req.getTowerEndpoint())
                : towerEndpointUrl
        // check that the Tower instance is paired with this Wave server
        final registration = pairingService.getPairingRecord(PairingService.TOWER_SERVICE, endpoint)
        if( !registration )
            throw new BadRequestException("Tower instance '${endpoint}' has not enabled to connect Wave service '${serverUrl}'")
        // look up the user associated with the supplied access token
        final auth = JwtAuth.of(endpoint, req.getTowerAccessToken())
        final User user = userService.getUserByAccessToken(registration.endpoint, auth)
        return new PlatformId(user, req.getTowerWorkspaceId(), req.getTowerAccessToken(), endpoint)
    }

    // --- mapping helpers ---

    private static ContainerInspectResponse toV1Response(ContainerSpec spec) {
        final resp = new ContainerInspectResponse()
        resp.setContainer(toV1Container(spec))
        return resp
    }

    private static ContainerInspectResponseContainer toV1Container(ContainerSpec spec) {
        return new ContainerInspectResponseContainer()
                .registry(spec.getRegistry())
                .hostName(spec.getHostName())
                .imageName(spec.getImageName())
                .reference(spec.getReference())
                .digest(spec.getDigest())
                .v1(spec.isV1())
                .v2(spec.isV2())
                .oci(spec.isOci())
                .config(toV1Config(spec.getConfig()))
                .manifest(toV1Manifest(spec.getManifest()))
    }

    private static ContainerInspectConfig toV1Config(ConfigSpec cfg) {
        if( cfg == null )
            return null
        final v1cfg = new ContainerInspectConfig()
                .architecture(cfg.architecture)
                .container(cfg.container)
                .created(cfg.created?.toString())
                .rootfs(toV1RootFS(cfg.rootfs))
                .config(toV1ConfigConfig(cfg.config))
        return v1cfg
    }

    private static ContainerInspectConfigConfig toV1ConfigConfig(ConfigSpec.Config cfg) {
        if( cfg == null )
            return null
        return new ContainerInspectConfigConfig()
                .attachStdin(cfg.attachStdin)
                .attachStdout(cfg.attachStdout)
                .attachStderr(cfg.attachStderr)
                .tty(cfg.tty)
                .env(cfg.env ?: [])
                .cmd(cfg.cmd ?: [])
                .image(cfg.image)
    }

    private static RootFS toV1RootFS(ConfigSpec.Rootfs rootfs) {
        if( rootfs == null )
            return null
        return new RootFS()
                .type(rootfs.type)
                .diffIds(rootfs.diff_ids ?: [])
    }

    private static Manifest toV1Manifest(ManifestSpec mspec) {
        if( mspec == null )
            return null
        return new Manifest()
                .schemaVersion(mspec.getSchemaVersion())
                .mediaType(mspec.getMediaType())
                .config(toV1ManifestConfig(mspec.getConfig()))
                .layers(toV1Layers(mspec.getLayers()))
    }

    private static ManifestConfig toV1ManifestConfig(ObjectRef ref) {
        if( ref == null )
            return null
        return new ManifestConfig()
                .digest(ref.digest)
                .mediaType(ref.mediaType)
                .size(ref.size)
    }

    private static List<ManifestLayer> toV1Layers(List<ObjectRef> layers) {
        if( !layers )
            return []
        return layers.collect { ObjectRef l ->
            new ManifestLayer()
                    .digest(l.digest)
                    .mediaType(l.mediaType)
                    .size(l.size)
        }
    }
}

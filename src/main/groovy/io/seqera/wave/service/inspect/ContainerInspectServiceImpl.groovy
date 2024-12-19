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

package io.seqera.wave.service.inspect

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import groovy.util.logging.Slf4j
import io.seqera.wave.WaveDefault
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentials
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.ContainerAugmenter
import io.seqera.wave.core.ContainerPath
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.spec.ConfigSpec
import io.seqera.wave.core.spec.ContainerSpec
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.RegHelper
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements containers inspect service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ContainerInspectServiceImpl implements ContainerInspectService {

    @PackageScope interface InspectItem { }

    @Canonical
    @PackageScope
    static class InspectEntrypoint implements InspectItem {
        List<String> entrypoint
    }

    @Canonical
    @PackageScope
    static class InspectRepository implements InspectItem {
        String image
    }

    @Inject
    private RegistryLookupService lookupService

    @Inject
    private RegistryCredentialsProvider credentialsProvider

    @Inject
    private RegistryProxyService proxyService

    @Inject
    private RegistryAuthService loginService

    @Inject
    private HttpClientConfig httpConfig

    /**
     * {@inheritDoc}
     */
    @Override
    String credentialsConfigJson(String containerFile, String buildRepo, String cacheRepo, PlatformId identity) {
        final repos = new HashSet(10)
        if( containerFile )
            repos.addAll(findRepositories(containerFile))
        if( buildRepo )
            repos.add(buildRepo)
        if( cacheRepo )
            repos.add(cacheRepo)
        final result = credsJson(repos, identity)
        if( buildRepo && result && !result.contains(host0(buildRepo)) )
            throw new BadRequestException("Missing credentials for container repository: $buildRepo")
        if( cacheRepo && result && !result.contains(host0(cacheRepo)) )
            throw new BadRequestException("Missing credentials for container repository: $cacheRepo")
        return result
    }

    static protected String host0(String repo) {
        ContainerCoordinates
                .parse(repo)
                .registry
    }

    protected String credsJson(Set<String> repositories, PlatformId identity) {
        final hosts = new HashSet()
        final result = new StringBuilder()
        for( String repo : repositories ) {
            final path = ContainerCoordinates.parse(repo)
            final info = lookupService.lookup(path.registry)
            final hostName = info.getIndexHost()
            if( !hosts.add(hostName) ) {
                // skip this index host because it has already be added to the list
                continue
            }
            final creds = credentialsProvider.getCredentials(path, identity)
            log.debug "Build credentials for repository: $repo => $creds"
            if( !creds ) {
                // skip this host because there are no credentials
                continue
            }
            final encode = "${creds.username}:${creds.password}".getBytes().encodeBase64()
            if( result.size() )
                result.append(',')
            result.append("\"${hostName}\":{\"auth\":\"$encode\"}")
        }
        return result.size() ? """{"auths":{$result}}""" : null
    }

    static protected List<String> findRepositories(String dockerfile) {
        final result = new ArrayList(10)
        if( !dockerfile )
            return result
        for( String line : dockerfile.readLines()) {
            final repo = RegHelper.parseFromStatement(line.trim())
            if( repo )
                result.add(repo)
        }
        return result
    }

    static protected List<InspectItem> inspectItems(String containerFile) {
        final result = new ArrayList<InspectItem>(10)
        if( !containerFile )
            return result
        for( String line : containerFile.readLines().reverse() ) {
            String repo
            List<String> entry
            if( repo=RegHelper.parseFromStatement(line.trim()) ) {
                result.add(new InspectRepository(repo))
                // stop when the first FROM statement is found
                // note: considering the container file is scanned from the bottom
                // to the top this the last entry in a multi-stage build
                // therefore the previous entries should not affect the entrypoint definition
                break
            }
            else if( entry=RegHelper.parseEntrypoint(line.trim()) ) {
                result.add(new InspectEntrypoint(entry))
            }
        }
        return result
    }

    /**
     * {@inheritDoc}
     */
    @Override
    List<String> containerEntrypoint(String containerFile, ContainerPlatform containerPlatform, PlatformId identity) {
        final repos = inspectItems(containerFile)
        if( !repos )
            return null

        final itr = repos.iterator()
        while( itr.hasNext() ) {
            final item = itr.next()

            // found an entrypoint, return it
            if( item instanceof InspectEntrypoint ) {
                return item.getEntrypoint()
            }

            // found a container image, fetch the entrypoint from the remote repository
            else if( item instanceof InspectRepository ) {
                final path = ContainerCoordinates.parse(item.getImage())

                final creds = credentialsProvider.getCredentials(path, identity)
                log.debug "Config credentials for repository: ${item.getImage()} => $creds"

                final entry = fetchConfig0(path, creds, containerPlatform).config?.entrypoint
                if( entry )
                    return entry
            }

            else if( item != null ) {
                throw new IllegalStateException("Unknown container file inspect item: $item")
            }
        }

        return null
    }

    private ProxyClient client0(ContainerPath route, RegistryCredentials creds) {
        final registry = lookupService.lookup(route.registry)
        final httpClient = HttpClientFactory.neverRedirectsHttpClient()
        new ProxyClient(httpClient, httpConfig)
                .withRoute(route)
                .withImage(route.image)
                .withRegistry(registry)
                .withCredentials(creds)
                .withLoginService(loginService)
    }

    private ConfigSpec fetchConfig0(ContainerPath path, RegistryCredentials creds, ContainerPlatform platform) {
        final client = client0(path, creds)

        return new ContainerAugmenter()
                .withClient(client)
                .withPlatform(platform)
                .getContainerSpec(path.image, path.getReference(), WaveDefault.ACCEPT_HEADERS)
                .getConfig()
    }

    @Override
    ContainerSpec containerSpec(String containerImage, String arch, PlatformId identity) {
        final path = ContainerCoordinates.parse(containerImage)

        final creds = credentialsProvider.getCredentials(path, identity)
        log.debug "Inspect credentials for repository: ${containerImage} => $creds"

        final client = client0(path, creds)

        return new ContainerAugmenter()
                .withClient(client)
                .withPlatform(arch)
                .getContainerSpec(path.image, path.getReference(), WaveDefault.ACCEPT_HEADERS)
    }
}

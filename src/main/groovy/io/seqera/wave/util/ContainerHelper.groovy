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

package io.seqera.wave.util

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerStatus
import io.seqera.wave.api.ImageNameStrategy
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.config.CondaOpts
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.request.TokenData
import org.yaml.snakeyaml.Yaml
import static io.seqera.wave.service.builder.BuildFormat.SINGULARITY
import static io.seqera.wave.util.DockerHelper.condaEnvironmentToCondaYaml
import static io.seqera.wave.util.DockerHelper.condaFileToDockerFile
import static io.seqera.wave.util.DockerHelper.condaFileToSingularityFile
import static io.seqera.wave.util.DockerHelper.condaPackagesToCondaYaml
import static io.seqera.wave.util.DockerHelper.condaPackagesToDockerFile
import static io.seqera.wave.util.DockerHelper.condaPackagesToSingularityFile
/**
 * Container helper methods
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
class ContainerHelper {

    /**
     * Create a Containerfile from the specified packages specification
     *
     * @param spec
     *      A {@link PackagesSpec} object modelling the packages to be included in the resulting container
     * @param formatSingularity
     *      When {@code false} creates a Dockerfile, when {@code true} creates a Singularity file
     * @return
     *      The corresponding Containerfile
     */
    static String containerFileFromPackages(PackagesSpec spec, boolean formatSingularity) {
        if( spec.type == PackagesSpec.Type.CONDA ) {
            final lockFile = condaLockFile(spec.entries)
            if( !spec.condaOpts )
                spec.condaOpts = new CondaOpts()
            def result
            if ( lockFile ) {
                result = formatSingularity
                        ? condaPackagesToSingularityFile(lockFile, spec.channels, spec.condaOpts)
                        : condaPackagesToDockerFile(lockFile, spec.channels, spec.condaOpts)
            } else {
                result = formatSingularity
                        ? condaFileToSingularityFile(spec.condaOpts)
                        : condaFileToDockerFile(spec.condaOpts)
            }
            return result
        }

        throw new BadRequestException("Unexpected packages spec type: $spec.type")
    }

    static String condaFileFromRequest(SubmitContainerTokenRequest req) {
        if( !req.packages )
            return decodeBase64OrFail(req.condaFile, 'condaFile')

        if( req.packages.type != PackagesSpec.Type.CONDA )
            return null

        if( req.packages.environment ) {
            // parse the attribute as a conda file path *and* append the base packages if any
            // note 'channel' is null, because they are expected to be provided in the conda file
            final decoded = decodeBase64OrFail(req.packages.environment, 'packages.envFile')
            return condaEnvironmentToCondaYaml(decoded, req.packages.channels)
        }

        if ( req.packages.entries && !condaLockFile(req.packages.entries)) {
            // create a minimal conda file with package spec from user input
            final String packages = req.packages.entries.join(' ')
            return condaPackagesToCondaYaml(packages, req.packages.channels)
        }

        return null;
    }

    static protected String condaLockFile(List<String> condaPackages) {
        if( !condaPackages )
            return null;
        final result = condaPackages .findAll(it->it.startsWith("http://") || it.startsWith("https://"))
        if( !result )
            return null;
        if( condaPackages.size()>1 ) {
            throw new IllegalArgumentException("No more than one Conda lock remote file can be specified at the same time");
        }
        return result[0]
    }

    static String decodeBase64OrFail(String value, String field) {
        if( !value )
            return null
        try {
            final bytes = Base64.getDecoder().decode(value)
            final check = Base64.getEncoder().encodeToString(bytes)
            if( value!=check )
                throw new IllegalArgumentException("Not a valid base64 encoded string")
            return new String(bytes)
        }
        catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid '$field' attribute - make sure it encoded as a base64 string", e)
        }
    }

    static SubmitContainerTokenResponse makeResponseV1(ContainerRequest data, TokenData token, String waveImage) {
        final target = waveImage
        final build = data.buildNew ? data.buildId : null
        return new SubmitContainerTokenResponse(data.requestId, token.value, target, token.expiration, data.containerImage, build, null, null, null, null, null)
    }

    static SubmitContainerTokenResponse makeResponseV2(ContainerRequest data, TokenData token, String waveImage) {
        final target = data.durable() ? data.containerImage : waveImage
        final build = data.buildId
        final Boolean cached = !data.buildNew
        final expiration = !data.durable() ? token.expiration : null
        final tokenId = !data.durable() ? token.value : null
        final status = data.buildNew || data.scanId
                ? ContainerStatus.PENDING
                : ContainerStatus.READY
        return new SubmitContainerTokenResponse(data.requestId, tokenId, target, expiration, data.containerImage, build, cached, data.freeze, data.mirror, data.scanId, status)
    }

    static String patchPlatformEndpoint(String endpoint) {
        if( !endpoint )
            return null

        // api.stage-tower.net --> api.cloud.stage-seqera.io
        // api.tower.nf --> api.cloud.seqera.io
        final result = endpoint
                .replace('/api.stage-tower.net','/api.cloud.stage-seqera.io')
                .replace('/api.tower.nf','/api.cloud.seqera.io')
        if( result != endpoint )
            log.debug "Patched Platform endpoint: '$endpoint' with '$result'"
        return result
    }

    static List<String> normaliseDeps0(List deps) {
        final result = new ArrayList(20)
        for( def it : deps ) {
            if( it instanceof CharSequence )
                result.add(it.toString())
            else if( it instanceof Map ) {
                for( Map.Entry entry : (it as Map) ){
                    if( entry.key=='pip' && entry.value instanceof List ) {
                        for( String elem : entry.value as List ) {
                            result.add('pip:' + elem)
                        }
                    }
                    else
                        throw new IllegalStateException("Unexpected Conda dependencies format - offending value: $deps")
                }
            }
            else
                throw new IllegalStateException("Unexpected Conda dependencies format - offending value: $deps")
        }
        return result
    }


    static NameVersionPair guessCondaRecipeName(String condaFileContent, boolean split=false) {
        if( !condaFileContent )
            return null
        try {
            final yaml = (Map)new Yaml().load(condaFileContent)
            if( yaml.name ) {
                final name = yaml.name as String
                return split
                        ? new NameVersionPair([name], [null])
                        : new NameVersionPair([name])
            }

            if( yaml.dependencies instanceof List ) {
                final LinkedHashSet<String> versions = new LinkedHashSet<>()
                final LinkedHashSet<String> result = new LinkedHashSet<>()
                for( String it : normaliseDeps0(yaml.dependencies as List) ) {
                    //strip `pip:` prefix
                    if( it.startsWith('pip:') && it.length()>4 && it[4]!=':')
                        it = it.substring(4)
                    // strip channel prefix
                    final int p=it.indexOf('::')
                    if( p!=-1 )
                        it = it.substring(p+2)
                    final pair = splitVersion(it, '=><')
                    if( split ) {
                        it = pair.v1
                        versions.add(pair.v2)
                    }
                    else if( pair.v2 )
                        it = "${pair.v1}-${pair.v2}".toString()
                    if( it )
                        result.add(it)
                }
                return split
                        ? new NameVersionPair(result, versions)
                        : new NameVersionPair(result)
            }
            return null
        }
        catch (Exception e) {
            log.warn "Unable to infer conda recipe name - cause: ${e.message}", e
            return null
        }
    }

    static Tuple2<String,String> splitVersion(String tool, String sep) {
        if( !tool )
            return null
        final parts = tool.tokenize(sep)
        return new Tuple2<String, String>(parts[0], parts[1])
    }

    static String makeTargetImage(BuildFormat format, String repo, String id, @Nullable String condaFile, @Nullable ImageNameStrategy nameStrategy) {
        assert id, "Argument 'id' cannot be null or empty"
        assert repo, "Argument 'repo' cannot be null or empty"
        assert format, "Argument 'format' cannot be null"

        NameVersionPair tools
        def tag = id
        if( nameStrategy==null || nameStrategy==ImageNameStrategy.tagPrefix ) {
            if( condaFile && (tools=guessCondaRecipeName(condaFile,false)) ) {
                tag = "${normaliseTag(tools.qualifiedNames())}--${id}"
            }
        }
        else if( nameStrategy==ImageNameStrategy.imageSuffix )  {
            if( condaFile && (tools=guessCondaRecipeName(condaFile,true)) ) {
                repo = StringUtils.pathConcat(repo, normaliseName(tools.friendlyNames()))
                if( tools.versions?.size()==1 && tools.versions[0] )
                    tag = "${normaliseTag(tools.versions[0])}--${id}"
            }
        }
        else if( nameStrategy!=ImageNameStrategy.none ) {
            throw new BadRequestException("Unsupported image naming strategy: '${nameStrategy}'")
        }

        format==SINGULARITY ? "oras://${repo}:${tag}" : "${repo}:${tag}"
    }

    static protected String normalise0(String tag, int maxLength, String pattern) {
        assert maxLength>0, "Argument maxLength cannot be less or equals to zero"
        if( !tag )
            return null
        tag = tag.replaceAll(/$pattern/,'')
        // only allow max 100 chars
        if( tag.length()>maxLength ) {
            // try to tokenize splitting by `_`
            def result = ''
            def parts = tag.tokenize('_')
            for( String it : parts ) {
                if( result )
                    result += '_'
                result += it
                if( result.size()>maxLength )
                    break
            }
            tag = result
        }
        // still too long, trunc it
        if( tag.length()>maxLength ) {
            tag = tag.substring(0,maxLength)
        }
        // remove trailing or leading special chars
        tag = tag.replaceAll(/^(\W|_)+|(\W|_)+$/,'')
        return tag ?: null
    }

    static protected String normaliseTag(String value, int maxLength=80) {
        normalise0(value, maxLength, /[^a-zA-Z0-9_.-]/)
    }

    static protected String normaliseName(String value, int maxLength=255) {
        value ? normalise0(value.toLowerCase(), maxLength, /[^a-z0-9_.\-\/]/) : null
    }

    static String makeContainerId(String containerFile, String condaFile, ContainerPlatform platform, String repository, BuildContext buildContext) {
        final attrs = new LinkedHashMap<String,String>(10)
        attrs.containerFile = containerFile
        attrs.condaFile = condaFile
        attrs.platform = platform?.toString()
        attrs.repository = repository
        if( buildContext ) attrs.buildContext = buildContext.tarDigest
        return RegHelper.sipHash(attrs)
    }

    static void checkContainerSpec(String file) {
        if( !file )
            return
        if( file.contains('/.docker/config.json') )
            throw new BadRequestException("Provided container file is not allowed (error code: 100)")
        if( file.contains('/kaniko') )
            throw new BadRequestException("Provided container file is not allowed (error code: 101)")
    }
}

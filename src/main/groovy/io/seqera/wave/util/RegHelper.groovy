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

import java.net.http.HttpHeaders
import java.net.http.HttpResponse
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom

import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.model.ContainerCoordinates
import org.yaml.snakeyaml.Yaml
/**
 * Helper methods
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Slf4j
class RegHelper {

    final private static char PADDING = '_' as char
    final private static BaseEncoding BASE32 = BaseEncoding.base32() .withPadChar(PADDING)

    // this clas is not to be extended
    private RegHelper(){
        throw new AssertionError()
    }

    static private String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte byt : bytes) result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    static String digest(String str) {
        return digest(str.getBytes())
    }

    static String digest(Path path) {
        return digest(Files.readAllBytes(path))
    }

    static String digest(byte[] bytes) {
        final digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return "sha256:${bytesToHex(digest)}"
    }

    static String encodeBase32(String str, boolean padding=true) {
        final result = BASE32.encode(str.bytes).toLowerCase()
        if( padding )
            return result
        final p = result.indexOf(PADDING as byte)
        return p == -1 ? result : result.substring(0,p)
    }

    static String decodeBase32(String encoded) {
        final result = BASE32.decode(encoded.toUpperCase())
        return new String(result)
    }

    static randomString(int len) {
        byte[] array = new byte[len]
        new Random().nextBytes(array)
        return new String(array, Charset.forName("UTF-8"));
    }

    static randomString(int min, int max) {
        Random random = new Random();
        final len = random.nextInt(max - min) + min
        randomString(len)
    }

    static String dumpJson(payload) {
        if( payload==null )
            return '(null)'
        try {
            return '\n' + JsonOutput.prettyPrint(payload.toString().trim())
        }
        catch( Throwable e ) {
            return '(no json output)'
        }
    }

    static String dumpHeaders(HttpHeaders headers) {
        return dumpHeaders(headers.map())
    }

    static String dumpHeaders(Map<String, List<String>> headers) {
        def result = new StringBuilder()
        for( Map.Entry<String,List<String>> entry : headers )  {
            for( String val : entry.value )
                result.append("\n  $entry.key=$val")
        }
        return result.toString()
    }

    static String random256Hex() {
        final secureRandom = new SecureRandom();
        byte[] token = new byte[32]
        secureRandom.nextBytes(token)
        def result = new BigInteger(1, token).toString(16)
        // pad with extra zeros if necessary
        while( result.size()<64 )
            result = '0'+result
        return result
    }

    static String stringToId(String str) {
        def result = bytesToHex(MessageDigest.getInstance("SHA-256").digest(str.bytes))
        // pad with extra zeros if necessary
        while( result.size()<64 )
            result = '0'+result
        return result
    }

    static String parseFromStatement(String line){
        if( !line )
            return null
        if( !line.startsWith('FROM ') && !line.toLowerCase().startsWith('from:' ))
            return null
        final tokens = line.tokenize(' ')
        if( tokens.size() < 2 )
            return null
        if ( tokens[1].startsWith('--platform=') )
            return tokens[2]
        else
            return tokens[1]
    }

    static List<String> parseEntrypoint(String line) {
        if( !line )
            return null
        if( !line.startsWith('ENTRYPOINT '))
            return null
        final value = line.substring(10).trim()
        if( value.startsWith('[') && value.endsWith(']') ) {
            // parse syntax as ["executable", "param1", "param2"]
           return value.substring(1, value.length()-1)
                   .tokenize(',')
                   .collect(it-> it.trim().replaceAll(/^"(.*)"$/,'$1'))
        }
        else {
            // parse syntax ENTRYPOINT command param1 param2
            return value.tokenize(' ')
        }
    }

    static String singularityRemoteFile(String repo)  {
        assert repo.startsWith('oras://')
        final coords = ContainerCoordinates.parse(repo)
        """\
        Active: SylabsCloud
        Remotes:
          SylabsCloud:
            URI: cloud.sylabs.io
            System: true
            Exclusive: false
        Credentials:
        - URI: ${coords.scheme}://${coords.registry}
          Insecure: false
        """.stripIndent()
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
                for( String it : yaml.dependencies ) {
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

    static NameVersionPair guessSpackRecipeName(String spackFileContent, boolean split=false) {
        if( !spackFileContent )
            return null
        try {
            final yaml = new Yaml().load(spackFileContent) as Map
            final spack = yaml.spack as Map

            if( !spack ){
                throw new BadRequestException('Malformed Spack environment file - missing "spack:" section')
            }
            if( !spack.specs ){
                throw new BadRequestException('Malformed Spack environment file - missing "spack.specs:" section')
            }

            if( spack.specs instanceof List ) {
                final LinkedHashSet<String> result = new LinkedHashSet()
                final LinkedHashSet<String> versions = new LinkedHashSet()
                for( String it : spack.specs ) {
                    final p = it.indexOf(' ')
                    // remove everything after the first blank because they are supposed package directives
                    if( p!=-1 )
                        it = it.substring(0,p)
                    if( split ) {
                        final pair = splitVersion(it, '@')
                        it = pair.v1
                        versions.add(pair.v2)
                    }
                    else {
                        // replaces '@' version separator with `-`
                        it = it.replace('@','-')
                    }
                    if( it )
                        result.add(it)
                }
                return split
                        ? new NameVersionPair(result, versions)
                        : new NameVersionPair(result)
            }
            return null
        }
        catch (BadRequestException e) {
            throw  e
        }
        catch (Throwable e) {
            log.warn "Unable to infer spack recipe name - cause: ${e.message}", e
            return null
        }
    }

    static String sipHash(LinkedHashMap<String,String> values) {
        if( values == null )
            throw new IllegalArgumentException("Missing argument for sipHash method")

        final hasher = Hashing.sipHash24().newHasher()
        for( Map.Entry<String,String> entry : values.entrySet() ) {
            hasher.putUnencodedChars(entry.key)
            hasher.putUnencodedChars(Character.toString(0x1C))
            if( entry.value!=null )
                hasher.putUnencodedChars(entry.value)
            hasher.putUnencodedChars(Character.toString(0x1E))
        }

        return hasher.hash().toString()
    }

    static String layerName(ContainerLayer layer) {
        return "layer-${layer.gzipDigest.replace(/sha256:/,'')}.tar.gz"
    }

    static String layerDir(ContainerLayer layer) {
        return layerName(layer).replace(/.tar.gz/,'')
    }

    static void closeResponse(HttpResponse<?> response) {
        log.trace "Closing HttpClient response: $response"
        try {
            // close the httpclient response to prevent leaks
            // https://bugs.openjdk.org/browse/JDK-8308364
            final b0 = response.body()
            if( b0 instanceof Closeable )
                b0.close()
        }
        catch (Throwable e) {
            log.debug "Unexpected error while closing http response - cause: ${e.message}", e
        }
    }
}

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

package io.seqera.wave.util

import java.net.http.HttpHeaders
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


    static String guessCondaRecipeName(String condaFileContent) {
        if( !condaFileContent )
            return null
        try {
            final yaml = (Map)new Yaml().load(condaFileContent)
            if( yaml.name )
                return yaml.name
            if( yaml.dependencies instanceof List ) {
                final LinkedHashSet<String> result = new LinkedHashSet()
                for( String it : yaml.dependencies ) {
                    final int p=it.indexOf('::')
                    if( p!=-1 )
                        it = it.substring(p+2)
                    it = it.replace('=','-')
                    if( it )
                        result.add(it)
                }
                return result.join('_')
            }
            return null
        }
        catch (Exception e) {
            log.warn "Unable to infer conda recipe name - cause: ${e.message}", e
            return null
        }
    }


    static String guessSpackRecipeName(String spackFileContent) {
        if( !spackFileContent )
            return null
        try {
            final yaml = new Yaml().load(spackFileContent) as Map
            final spack = yaml.spack as Map
            if( spack.specs instanceof List ) {
                final LinkedHashSet<String> result = new LinkedHashSet()
                for( String it : spack.specs ) {
                    final p = it.indexOf(' ')
                    // remove everything after the first blank because they are supposed package directives
                    if( p!=-1 )
                        it = it.substring(0,p)
                    // replaces '@' version separator with `'`
                    it = it.replace('@','-')
                    if( it )
                        result.add(it)
                }
                return result.join('_')
            }
            return null
        }
        catch (Exception e) {
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
}

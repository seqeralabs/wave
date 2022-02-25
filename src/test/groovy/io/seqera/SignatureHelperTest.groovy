package io.seqera

import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
class SignatureHelperTest extends Specification {

    def 'should sign a manifest schema v1'() {
        given:
        def keyPair = SignatureHelper.newKeyPair()
        def MANIFEST = new JsonSlurper().parse(this.class.getResourceAsStream("/foo/manifest_schema1.json")) as Map

        when:
        def MANIFEST_SIGNED = SignatureHelper.signManifest(MANIFEST, keyPair)

        then: 'the manifest has signatures'
        'signatures' in MANIFEST_SIGNED

    }

    def 'should jose base64 encode'() {
        given:
        def PAYLOAD = "{'kk': 'vaka'}"

        expect:
        SignatureHelper.joseBase64UrlEncode(PAYLOAD.bytes) == "eydrayc6ICd2YWthJ30"

    }
}

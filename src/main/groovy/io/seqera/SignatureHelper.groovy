package io.seqera

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.JWSObject
import com.nimbusds.jose.JWSSigner
import com.nimbusds.jose.Payload
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import groovy.json.JsonOutput
import io.seqera.util.Base32

import java.text.DateFormat
import java.text.SimpleDateFormat

/**
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
class SignatureHelper {

    static Map signManifest(Map manifest, ECKey key) {
        final manifestNotSigned = [:] << manifest
        manifestNotSigned.remove("signatures")

        //TODO Pretty print JSON using only 3 blank indentation? Is it mandatory?
        final content = JsonOutput.prettyPrint(JsonOutput.toJson(manifestNotSigned)).bytes

        // Protected header
        final closeIndex = content.findLastIndexOf { !Character.isWhitespace(it as char) }
        final lastRuneIndex = content[0..(closeIndex - 1)].findLastIndexOf { !Character.isWhitespace(it as char) }
        final formatLength = lastRuneIndex + 1
        final formatTail = content[formatLength..-1] as byte[]
        final protectedJson = JsonOutput.toJson([
                'formatLength': formatLength,
                'formatTail'  : joseBase64UrlEncode(formatTail),
                'time'        : currentDateTimeRFC3339()
        ])
        final protectedBase64 = joseBase64UrlEncode(protectedJson.bytes)

        // Sign
        final alg = JWSAlgorithm.ES256
        final payload = joseBase64UrlEncode(content)
        final data = (protectedBase64 + '.' + payload).bytes
        final signData = signData(data, key, alg)

        // Prepare JWK public key with fingerprint KID
        final publicKey = key.toPublicJWK()
        final jwk = key.toPublicJWK().toJSONObject()
        jwk['kid'] = keyIDFromCryptoKey(publicKey)

        // New signature
        final newSignature = [
                'header'   : [
                        'jwk': jwk,
                        'alg': alg.getName()
                ],
                'signature': signData,
                'protected': protectedBase64
        ]

        // Manifest signed
        manifestNotSigned['signatures'] = [newSignature]
        return manifestNotSigned
    }

    /**
     *  joseBase64UrlEncode encodes the given data using the standard base64 url
     * encoding format but with all trailing '=' characters ommitted in accordance
     * with the jose specification.
     * http://tools.ietf.org/html/draft-ietf-jose-json-web-signature-31#section-2
     *
     * @param content
     * @return
     */
    static String joseBase64UrlEncode(byte[] content) {
        content.encodeBase64().toString().replaceAll(/=*$/, '')
    }

    private static final DateFormat RFC3339_FORMATTER = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ")

    static String currentDateTimeRFC3339() {
        return RFC3339_FORMATTER.format(new Date())
    }

    private static String signData(byte[] data, ECKey keyPair, JWSAlgorithm alg) {
        // Create the EC signer
        JWSSigner signer = new ECDSASigner(keyPair);

        // Creates the JWS object with payload
        JWSObject jwsObject = new JWSObject(
                new JWSHeader.Builder(alg).keyID(keyPair.getKeyID()).build(),
                new Payload(data))

        // Compute the EC signature
        jwsObject.sign(signer);

        // Return signature
        jwsObject.getSignature().toString()
    }

    static ECKey newKeyPair() {
        new ECKeyGenerator(Curve.P_256).generate()
    }

    /**
     *
     * Generate and return a 'libtrust' fingerprint of the public key.
     * For an RSA key this should be:
     *   SHA256(DER encoded ASN1)
     * Then truncated to 240 bits and encoded into 12 base32 groups like so:
     +   ABCD:EFGH:IJKL:MNOP:QRST:UVWX:YZ23:4567:ABCD:EFGH:IJKL:MNOP
     *
     * @param publicKey
     * @return
     */
    static String keyIDFromCryptoKey(ECKey key) {

        // TODO Use DER encoded version
        // derBytes, err := x509.MarshalPKIXPublicKey(pubKey.CryptoPublicKey())
        final derString = "${key.x}.${key.y}"

        final defSHA256 = derString.sha256().bytes[0..30] as byte[]
        final defBase32 = Base32.encode(defSHA256)
        final defID = (0..(defBase32.length()-3)).step(4).collect { defBase32[it..(it+3)] }.join(':')

        return defID
    }
}

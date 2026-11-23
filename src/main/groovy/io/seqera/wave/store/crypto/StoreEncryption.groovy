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

package io.seqera.wave.store.crypto

import javax.annotation.PostConstruct
import javax.crypto.spec.SecretKeySpec
import java.nio.charset.StandardCharsets
import java.util.Base64

import com.squareup.moshi.JsonAdapter
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.tower.crypto.CryptoHelper
import io.seqera.tower.crypto.Sealed
import io.seqera.wave.util.DigestFunctions
import jakarta.inject.Singleton

/**
 * Encrypts the fields marked {@link SecretField} in the records a state store writes to Redis.
 *
 * <p>Values are written as {@code enc:<keyId>:<base64>}; a value without that prefix is returned
 * as-is, which is what lets records written before this was switched on still decode.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class StoreEncryption {

    private static final String PREFIX = 'enc:'

    /**
     * Salt for the key derivation. Fixed on purpose: the derivation is expensive (PBKDF2, 65k
     * rounds) and must happen once at startup, not per record.
     */
    private static final byte[] KDF_SALT = 'wave-store-v1'.getBytes(StandardCharsets.UTF_8)

    /**
     * Encryption keys, most recent first. Records are written with the first one and can be read
     * with any of them, so a key can be rotated in without orphaning the records already stored.
     */
    @Nullable
    @Value('${wave.store.encryption.keys}')
    List<String> keys

    private Map<String,SecretKeySpec> keyById = Map.of()

    private String activeKeyId

    private static StoreEncryption disabled

    /**
     * A no-op instance, for tests and for the stores of records holding no credential.
     * Created on demand because the static {@code log} field is not yet set during class init.
     */
    static synchronized StoreEncryption disabled() {
        if( disabled == null )
            disabled = new StoreEncryption().tap { it.init() }
        return disabled
    }

    @PostConstruct
    void init() {
        // trim before use: a comma-separated env var yields ' key2', and a later cleanup of that
        // whitespace would change both the derived key and its id, orphaning every record it sealed
        final all = keys?.collect(it -> it?.trim())?.findAll(it -> it) ?: List.<String>of()
        if( !all ) {
            log.warn "Wave store encryption is DISABLED - Platform credentials are stored in cleartext; set 'wave.store.encryption.keys' to enable it"
            return
        }
        final map = new LinkedHashMap<String,SecretKeySpec>(all.size())
        for( String it : all ) {
            final derived = CryptoHelper.deriveKey(it, KDF_SALT)
            map.put(keyId(derived), derived)
        }
        this.keyById = map
        this.activeKeyId = keyId(CryptoHelper.deriveKey(all.first(), KDF_SALT))
        log.info "Wave store encryption ENABLED - activeKey=${activeKeyId}; keys=${keyById.size()}"
    }

    boolean isEnabled() {
        return activeKeyId != null
    }

    /**
     * Short identifier so a record says which key opens it. Derived from the *derived* key, never
     * from the configured passphrase: the id sits in cleartext beside the ciphertext, so hashing
     * the passphrase would hand a Redis reader an md5-speed oracle to test guesses against,
     * sidestepping the 65k-round derivation this is meant to cost them.
     */
    protected static String keyId(SecretKeySpec key) {
        return DigestFunctions.md5(Base64.encoder.encodeToString(key.encoded)).substring(0, 8)
    }

    String encrypt(String value) {
        if( value == null || !enabled )
            return value
        final sealed = CryptoHelper.encrypt(keyById.get(activeKeyId), value.getBytes(StandardCharsets.UTF_8))
        return PREFIX + activeKeyId + ':' + Base64.encoder.encodeToString(sealed.serialize())
    }

    String decrypt(String value) {
        // no prefix means the record predates encryption being switched on - return it untouched
        if( value == null || !value.startsWith(PREFIX) )
            return value
        final int sep = value.indexOf(':', PREFIX.length())
        if( sep < 0 )
            throw new IllegalStateException("Malformed encrypted store value - missing key id")
        final id = value.substring(PREFIX.length(), sep)
        final key = keyById.get(id)
        if( !key )
            throw new IllegalStateException("Unable to decrypt store value - no encryption key with id '${id}' is configured")
        final sealed = Sealed.deserialize(Base64.decoder.decode(value.substring(sep + 1)))
        return new String(CryptoHelper.decrypt(key, sealed), StandardCharsets.UTF_8)
    }

    /** Moshi factory applying this encryption to every {@link SecretField} string */
    JsonAdapter.Factory factory() {
        return new SecretFieldAdapterFactory(this)
    }

}

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

import java.lang.annotation.Annotation
import java.lang.reflect.Type

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import groovy.transform.CompileStatic

/**
 * Binds {@link SecretField} strings to the encrypting adapter.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
class SecretFieldAdapterFactory implements JsonAdapter.Factory {

    private final StoreEncryption encryption

    SecretFieldAdapterFactory(StoreEncryption encryption) {
        this.encryption = encryption
    }

    @Override
    JsonAdapter<?> create(Type type, Set<? extends Annotation> annotations, Moshi moshi) {
        if( type != String.class )
            return null
        final rest = Types.nextAnnotations(annotations, SecretField.class)
        if( rest == null )
            return null
        final JsonAdapter<String> delegate = moshi.adapter(String.class, rest)
        return new JsonAdapter<String>() {

            @Override
            String fromJson(JsonReader reader) throws IOException {
                return encryption.decrypt(delegate.fromJson(reader))
            }

            @Override
            void toJson(JsonWriter writer, String value) throws IOException {
                delegate.toJson(writer, encryption.encrypt(value))
            }
        }
    }

}

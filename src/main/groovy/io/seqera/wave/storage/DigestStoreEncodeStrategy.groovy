/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2025, Seqera Labs
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

package io.seqera.wave.storage

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import io.seqera.wave.encoder.MoshiEncodeStrategy
/**
 * Factory class for creating a Moshi-based encoding strategy for polymorphic digest store types.
 * <p>
 * This class provides a {@link MoshiEncodeStrategy} configured with a polymorphic JSON adapter
 * that enables serialization and deserialization of different {@link DigestStore} subtypes
 * using a type discriminator field ({@code @type}) in the JSON representation.
 * <p>
 * The following digest store types are supported:
 * <ul>
 *   <li>{@link ZippedDigestStore} - Digest store for zipped/compressed container layers</li>
 *   <li>{@link HttpDigestStore} - Digest store for HTTP-accessible container layers</li>
 *   <li>{@link DockerDigestStore} - Digest store for Docker registry container layers</li>
 * </ul>
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DigestStoreEncodeStrategy {

    static MoshiEncodeStrategy<DigestStore> create() {
        new MoshiEncodeStrategy<DigestStore>() {}
    }

    static JsonAdapter.Factory factory() {
        PolymorphicJsonAdapterFactory.of(DigestStore.class, "@type")
                .withSubtype(ZippedDigestStore, ZippedDigestStore.simpleName)
                .withSubtype(HttpDigestStore, HttpDigestStore.simpleName)
                .withSubtype(DockerDigestStore, DockerDigestStore.simpleName)
    }
}

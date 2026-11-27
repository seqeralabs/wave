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

package io.seqera.wave.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.squareup.moshi.Json;

/**
 * The visibility a Wave record can be created with.
 *
 * The wire value is lowercase, {@code public} or {@code private}, like the other enums of the
 * submit request ({@link ScanMode}, {@link ImageNameStrategy}). Those words are Java reserved
 * words, so the constants are uppercase and each one names its wire value for Moshi
 * ({@link Json}) and Jackson ({@link JsonProperty}). Both libraries apply these names to enum
 * constants natively, so no adapter is needed. Jackson is a compile-only dependency: a client
 * without it on the classpath ignores the annotation.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
public enum Visibility {

    @Json(name = "public") @JsonProperty("public")
    PUBLIC("public"),

    @Json(name = "private") @JsonProperty("private")
    PRIVATE("private");

    private final String value;

    Visibility(String value) {
        this.value = value;
    }

    /**
     * @return the wire value, {@code public} or {@code private}
     */
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    /**
     * @return {@code true} only for {@link #PRIVATE}: an absent visibility means public
     */
    public static boolean isPrivate(Visibility value) {
        return value == PRIVATE;
    }

    /**
     * Converts a {@link Visibility} or its wire value, in any case, to the constant.
     *
     * @param value a {@link Visibility}, a string or {@code null}
     * @return the matching constant, or {@code null} when the value is {@code null}
     * @throws IllegalArgumentException when the value is not a known visibility
     */
    public static Visibility of(Object value) {
        if( value == null )
            return null;
        if( value instanceof Visibility )
            return (Visibility) value;
        final String str = value.toString();
        for( Visibility it : values() ) {
            if( it.value.equalsIgnoreCase(str) )
                return it;
        }
        throw new IllegalArgumentException("Invalid visibility: '" + str + "' - allowed values are 'public' and 'private'");
    }
}

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

/**
 * The visibility of a Wave record. It is never requested: Wave derives it from the identity of
 * the request that created the record.
 *
 * Modelled as a string rather than an enum because {@code public} and {@code private} are Java
 * reserved words, so they cannot be enum constants, and every other wire value in this API is
 * spelled exactly as its constant.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
public final class Visibility {

    public static final String PUBLIC = "public";

    public static final String PRIVATE = "private";

    private Visibility() {}

    /** @return {@code true} for a private record; a record with no visibility is public */
    public static boolean isPrivate(String value) {
        return PRIVATE.equals(value);
    }

}

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

package io.seqera.wave.api;

/**
 * The visibility a Wave record can be created with.
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

    /**
     * @return {@code true} when the value is one Wave understands, {@code null} included: an
     *      absent visibility means public.
     */
    public static boolean isValid(String value) {
        return value == null || PUBLIC.equals(value) || PRIVATE.equals(value);
    }

    public static boolean isPrivate(String value) {
        return PRIVATE.equals(value);
    }

}

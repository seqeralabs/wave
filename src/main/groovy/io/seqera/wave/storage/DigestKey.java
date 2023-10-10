/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.storage;

import java.util.Objects;

/**
 * Cache wrapper holding a reference to {@link DigestStore}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class DigestKey {

    final private DigestStore target;

    private DigestKey(DigestStore store) {
        this.target = store;
    }

    static public DigestKey of(DigestStore store) {
        return new DigestKey(store);
    }

    public byte[] readAllBytes() throws InterruptedException {
        return target.getBytes();
    }

    public DigestStore getTarget() {
        return target;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(target.getDigest());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DigestKey digestKey = (DigestKey) o;
        return Objects.equals(target.getDigest(), digestKey.target.getDigest());
    }

    @Override
    public String toString() {
        return String.format("DigestKey[%s]", target.toLogString());
    }
}

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

    public byte[] getAllBytes() {
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

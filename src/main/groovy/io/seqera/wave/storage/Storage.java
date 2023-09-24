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


import java.util.Optional;

import io.seqera.wave.storage.reader.ContentReader;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
public interface Storage {

    Optional<DigestStore> getManifest(String path);

    DigestStore saveManifest(String path, String manifest, String type, String digest);

    DigestStore saveManifest(String path, DigestStore store);

    Optional<DigestStore> getBlob(String path);

    DigestStore saveBlob(String path, byte[] content, String type, String digest);

    DigestStore saveBlob(String path, ContentReader content, String type, String digest, int size);
}

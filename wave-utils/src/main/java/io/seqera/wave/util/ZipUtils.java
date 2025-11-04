/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.wave.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.DeflaterInputStream;
import java.util.zip.InflaterInputStream;

/**
 * Helper class to basic compress/decompress funcionality
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ZipUtils {

    public static byte[] compress(InputStream stream) {
        try (InputStream in0=new DeflaterInputStream(stream)) {
            return in0.readAllBytes();
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to compress provider stream", e);
        }
    }

    public static byte[] compress(byte[] bytes) {
        return compress(new ByteArrayInputStream(bytes));
    }

    public static byte[] compress(String text) throws IOException {
        return compress(new ByteArrayInputStream(text.getBytes()));
    }

    public static InputStream decompress(byte[] buffer) {
        return new InflaterInputStream(new ByteArrayInputStream(buffer));
    }

    public static String decompressAsString(byte[] buffer) throws IOException {
        try( InputStream in0=decompress(buffer) ) {
            return new String(in0.readAllBytes());
        }
    }

    public static byte[] decompressAsBytes(byte[] buffer) {
        try ( InputStream in0=decompress(buffer) ) {
            return in0.readAllBytes();
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to decompress provider buffer", e);
        }
    }
}

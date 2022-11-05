package io.seqera.wave.storage.reader;

import java.io.IOException;
import java.util.Base64;

import io.seqera.wave.util.ZipUtils;

/**
 * Implements a {@link ContentReader} that hold data
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class GzipContentReader implements ContentReader {

    final private byte[] data;

    private GzipContentReader(byte[] data) {
        this.data = data;
    }

    @Override
    public byte[] readAllBytes() {
        return ZipUtils.decompressAsBytes(data);
    }

    public static GzipContentReader fromPlainString(String value) throws IOException {
        final byte[] compressed = ZipUtils.compress(value);
        return new GzipContentReader(compressed);
    }

    public static GzipContentReader fromBase64EncodedString(String value) {
        final byte[] decoded = Base64.getDecoder().decode(value);
        return new GzipContentReader(decoded);
    }
}

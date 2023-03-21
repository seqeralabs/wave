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

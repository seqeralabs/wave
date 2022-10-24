package io.seqera.wave.util;

import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

import com.google.common.io.BaseEncoding;

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 **/
public class DigestFunctions {

    final private static char PADDING = '_';
    final private static BaseEncoding BASE32 = BaseEncoding.base32() .withPadChar(PADDING);

    private static MessageDigest getSha256() throws NoSuchAlgorithmException {
        // warning: MessageDigest instance is not thread safe!
        return MessageDigest.getInstance("SHA-256");
    }

    private static MessageDigest getMd5() throws NoSuchAlgorithmException {
        return MessageDigest.getInstance("MD5");
    }

    public static String md5(String str) throws NoSuchAlgorithmException {
        if( str==null )
            str = Character.toString(0);
        final byte[] digest = getMd5().digest(str.getBytes());
        return bytesToHex(digest);
    }

    public static String md5(Map<String,Object> map) throws NoSuchAlgorithmException {
        if( map==null || map.size()==0 )
            throw new IllegalArgumentException("Cannot compute MD5 checksum for a null or empty map");
        final String result = concat0(map, new StringBuilder());
        return md5(result);
    }

    private static String concat0(Map<String,Object> map, StringBuilder result) throws NoSuchAlgorithmException {
        for( Map.Entry<String,Object> entry : map.entrySet() ) {
            // compute key checksum
            result.append( entry.getKey() );
            result.append( Character.toString(0x1C) );
            // compute key checksum
            final Object value = entry.getValue();
            if( value instanceof Map ) {
                concat0( (Map<String, Object>) value, result );
            }
            else if( value instanceof Collection ) {
                final Iterator<?> itr = ((Collection<?>) value).iterator();
                int i=0;
                while(itr.hasNext()) {
                    if( i++>0 ) result.append( Character.toString(0x1D) );
                    result.append( str0(itr.next()) );
                }
            }
            else {
                result.append( str0(value) );
            }
            result.append( Character.toString(0x1E) );
        }
        return result.toString();
    }

    private static String str0(Object object) {
        return object != null ? object.toString() : Character.toString(0x0);
    }

    public static String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte byt : bytes) result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    public static String digest(String str) throws NoSuchAlgorithmException {
        return digest(str.getBytes());
    }

    public static String digest(byte[] bytes) throws NoSuchAlgorithmException {
        final byte[] digest = getSha256().digest(bytes);
        return "sha256:"+bytesToHex(digest);
    }

    public static String digest(File file) throws IOException, NoSuchAlgorithmException {
        final byte[] digest = getSha256().digest(Files.readAllBytes(Path.of(file.toURI())));
        return "sha256:"+bytesToHex(digest);
    }

    public static String digest(Path path) throws IOException, NoSuchAlgorithmException {
        final byte[] digest = getSha256().digest(Files.readAllBytes(path));
        return "sha256:"+bytesToHex(digest);
    }

    public static String encodeBase32(String str, boolean padding) {
        final String result = BASE32.encode(str.getBytes()).toLowerCase();
        if( padding )
            return result;
        final int p = result.indexOf(PADDING);
        return p == -1 ? result : result.substring(0,p);
    }

    public static String decodeBase32(String encoded) {
        final byte[] result = BASE32.decode(encoded.toUpperCase());
        return new String(result);
    }

    public static String randomString(int len) {
        byte[] array = new byte[len];
        new Random().nextBytes(array);
        return new String(array, Charset.forName("UTF-8"));
    }

    public static String randomString(int min, int max) {
        Random random = new Random();
        final int len = random.nextInt(max - min) + min;
        return randomString(len);
    }

    public static String random256Hex() {
        final SecureRandom secureRandom = new SecureRandom();
        byte[] token = new byte[32];
        secureRandom.nextBytes(token);
        String result = new BigInteger(1, token).toString(16);
        // pad with extra zeros if necessary
        while( result.length()<64 )
            result = '0'+result;
        return result;
    }
}

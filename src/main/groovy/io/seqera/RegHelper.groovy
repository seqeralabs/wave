package io.seqera

import java.security.MessageDigest

import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RegHelper {

    final static MessageDigest SHA256 = java.security.MessageDigest.getInstance("SHA-256")

    static private String bytesToHex(byte[] bytes) {
        StringBuffer result = new StringBuffer();
        for (byte byt : bytes) result.append(Integer.toString((byt & 0xff) + 0x100, 16).substring(1));
        return result.toString();
    }

    static String digest(String str) {
        final digest = SHA256.digest(str.getBytes())
        return "sha256:${bytesToHex(digest)}"
    }

}

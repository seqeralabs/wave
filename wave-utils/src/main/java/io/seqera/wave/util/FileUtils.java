package io.seqera.wave.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class FileUtils {

    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    /**
     * Sets the last-modified time of the file or directory named by this
     * {@code Path}.
     *
     * @param self The {@code Path} to which set the last modified time
     * @param time The new last-modified time, measured in milliseconds since
     *             the epoch (00:00:00 GMT, January 1, 1970)
     * @return <code>true</code> if and only if the operation succeeded;
     *          <code>false</code> otherwise
     */
    public static boolean setLastModified(Path self, long time) {
        try {
            Files.setLastModifiedTime(self, FileTime.fromMillis(time));
            return true;
        }
        catch( IOException e ) {
            log.debug("Unable to set last-modified-time: $time to path: " + self);
            return false;
        }
    }

    /**
     * Get the file Unix permission as a string e.g. {@code rw-r--r--}
     *
     * @param self The {@code Path} for which the permissions string
     * @return Unix permission as a string e.g. {@code rw-r--r--}
     */
    public static String getPermissions(Path self) throws IOException {
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(self);
        return PosixFilePermissions.toString(perms);
    }

    public static String getPermissions(File self) throws IOException {
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(self.toPath());
        return PosixFilePermissions.toString(perms);
    }

    /**
     * Set the file Unix permission using the digit representing the respectively
     * the permissions for the owner, the group and others.
     *
     * @link http://en.wikipedia.org/wiki/File_system_permissions#Numeric_notation
     *
     * @param self The {@code Path} file for which set the permissions
     * @param owner The owner permissions using a octal numeric representation.
     * @param group The group permissions using a octal numeric representation.
     * @param other The others permissions using a octal numeric representation.
     */
    public static boolean setPermissions( Path self, int owner, int group, int other ) {
        StringBuilder str = new StringBuilder();
        digitToPerm(owner, str);
        digitToPerm(group, str);
        digitToPerm(other, str);
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString(str.toString());
        try {
            Files.setPosixFilePermissions(self, perms);
            return true;
        }
        catch( IOException e ) {
            log.debug("Unable to set permissions: $perms to path: "+self);
            return false;
        }
    }

    /**
     * Set the file Unix permission using the digit representing the respectively
     * the permissions for the owner, the group and others.
     *
     * @link http://en.wikipedia.org/wiki/File_system_permissions#Numeric_notation
     *
     * @param self The {@code File} object for which set the permissions
     * @param owner The owner permissions using a octal numeric representation.
     * @param group The group permissions using a octal numeric representation.
     * @param other The others permissions using a octal numeric representation.
     */
    public static boolean setPermissions(File self, int owner, int group, int other ) {
        return setPermissions(self.toPath(), owner, group, other);
    }

    /**
     * Set the file Unix permissions using a string like {@code rw-r--r--}
     *
     * @param self The {@code Path} file for which set the permissions.
     * @param permissions The permissions string e.g. {@code rw-r--r--}. It must contain 9 letters.
     */
    public static boolean setPermissions( Path self, String permissions ) {
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString(permissions);

        try {
            Files.setPosixFilePermissions(self, perms);
            return true;
        }
        catch( IOException e ) {
            log.debug("Unable to set permissions: $permissions to path: " + self);
            return false;
        }
    }

    /**
     * Set the file Unix permissions using a string like {@code rw-r--r--}
     *
     * @param self The {@code File} object for which set the permissions.
     * @param permissions The permissions string e.g. {@code rw-r--r--}. It must contain 9 letters.
     */

    public static boolean setPermissions( File self, String permissions ) {
        return setPermissions(self.toPath(),permissions);
    }

    public static int getPermissionsMode( File self ) throws IOException {
        final Set<PosixFilePermission> perms = Files.getPosixFilePermissions(self.toPath());
        return toOctalFileMode(perms);
    }

    public static int getPermissionsMode( Path self ) throws IOException {
        final Set<PosixFilePermission> perms = Files.getPosixFilePermissions(self);
        return toOctalFileMode(perms);
    }

    public static void setPermissionsMode( File self, int mode ) throws IOException {
        final Set<PosixFilePermission> perms = toPosixFilePermission(mode);
        Files.setPosixFilePermissions(self.toPath(), perms);
    }

    public static void setPermissionsMode( Path self, int mode ) throws IOException {
        final Set<PosixFilePermission> perms = toPosixFilePermission(mode);
        Files.setPosixFilePermissions(self, perms);
    }

    private static final int OWNER_READ_FILEMODE = 0400;
    private static final int OWNER_WRITE_FILEMODE = 0200;
    private static final int OWNER_EXEC_FILEMODE = 0100;
    private static final int GROUP_READ_FILEMODE = 0040;
    private static final int GROUP_WRITE_FILEMODE = 0020;
    private static final int GROUP_EXEC_FILEMODE = 0010;
    private static final int OTHERS_READ_FILEMODE = 0004;
    private static final int OTHERS_WRITE_FILEMODE = 0002;
    private static final int OTHERS_EXEC_FILEMODE = 0001;

    static protected int toOctalFileMode(Set<PosixFilePermission> permissions) {
        int result = 0;
        for (PosixFilePermission permissionBit : permissions) {
            switch (permissionBit) {
                case OWNER_READ:
                    result |= OWNER_READ_FILEMODE;
                    break;
                case OWNER_WRITE:
                    result |= OWNER_WRITE_FILEMODE;
                    break;
                case OWNER_EXECUTE:
                    result |= OWNER_EXEC_FILEMODE;
                    break;
                case GROUP_READ:
                    result |= GROUP_READ_FILEMODE;
                    break;
                case GROUP_WRITE:
                    result |= GROUP_WRITE_FILEMODE;
                    break;
                case GROUP_EXECUTE:
                    result |= GROUP_EXEC_FILEMODE;
                    break;
                case OTHERS_READ:
                    result |= OTHERS_READ_FILEMODE;
                    break;
                case OTHERS_WRITE:
                    result |= OTHERS_WRITE_FILEMODE;
                    break;
                case OTHERS_EXECUTE:
                    result |= OTHERS_EXEC_FILEMODE;
                    break;
            }
        }
        return result;
    }

    static protected Set<PosixFilePermission> toPosixFilePermission(int mode) {
        final Set<PosixFilePermission> result = new HashSet<>();
        // -- owner
        if( (mode & OWNER_READ_FILEMODE) != 0 )
            result.add(OWNER_READ);
        if( (mode & OWNER_WRITE_FILEMODE) != 0 )
            result.add(OWNER_WRITE);
        if( (mode & OWNER_EXEC_FILEMODE) != 0 )
            result.add(OWNER_EXECUTE);
        // -- group
        if( (mode & GROUP_READ_FILEMODE) != 0 )
            result.add(GROUP_READ);
        if( (mode & GROUP_WRITE_FILEMODE) != 0 )
            result.add(GROUP_WRITE);
        if( (mode & GROUP_EXEC_FILEMODE) != 0 )
            result.add(GROUP_EXECUTE);
        // -- other
        if( (mode & OTHERS_READ_FILEMODE) != 0 )
            result.add(OTHERS_READ);
        if( (mode & OTHERS_WRITE_FILEMODE) != 0 )
            result.add(OTHERS_WRITE);
        if( (mode & OTHERS_EXEC_FILEMODE) != 0)
            result.add(OTHERS_EXECUTE);

        return result;
    }

    static StringBuilder digitToPerm( int value ) {
        return digitToPerm(value, new StringBuilder());
    }

    static StringBuilder digitToPerm( int value, StringBuilder sb ) {
        assert value >= 0 && value < 8;

        final boolean x = (value & 1) == 1;
        final boolean w = (value & 2) == 2;
        final boolean r = (value & 4) == 4;

        if (r) {
            sb.append('r');
        } else {
            sb.append('-');
        }
        if (w) {
            sb.append('w');
        } else {
            sb.append('-');
        }
        if (x) {
            sb.append('x');
        } else {
            sb.append('-');
        }

        return sb;
    }

}

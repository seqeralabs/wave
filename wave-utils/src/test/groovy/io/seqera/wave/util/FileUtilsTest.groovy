package io.seqera.wave.util

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Paths

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class FileUtilsTest extends Specification {

    def testSetPermission() {
        setup:
        def file1 = File.createTempFile('testfile',null)
        file1.deleteOnExit()

        when:
        file1.setReadable(true,false)
        def ok = FileUtils.setPermissions(file1.toPath(), 6,4,4)
        then:
        ok
        FileUtils.getPermissions(file1.toPath()) == 'rw-r--r--'

        expect:
        !FileUtils.setPermissions(Paths.get('none'), 6,6,6)

    }

    def testSetPermissionString() {
        setup:
        def file1 = File.createTempFile('testfile',null)
        file1.deleteOnExit()

        when:
        file1.setReadable(true,false)
        def ok = FileUtils.setPermissions(file1.toPath(), 'rw-rw-rw-')
        then:
        ok
        FileUtils.getPermissions(file1.toPath()) == 'rw-rw-rw-'

        expect:
        !FileUtils.setPermissions(Paths.get('none'), 'rw-rw-rw-')

    }

    def testDigitToPerm() {

        expect:
        FileUtils.digitToPerm(1).toString() == '--x'
        FileUtils.digitToPerm(2).toString() == '-w-'
        FileUtils.digitToPerm(3).toString() == '-wx'
        FileUtils.digitToPerm(4).toString() == 'r--'
        FileUtils.digitToPerm(5).toString() == 'r-x'
        FileUtils.digitToPerm(6).toString() == 'rw-'
        FileUtils.digitToPerm(7).toString() == 'rwx'

    }

    def 'should convert octal to permissions' () {
        expect:
        FileUtils.toPosixFilePermission(0) == [] as Set
        and:
        FileUtils.toPosixFilePermission(01) == [OTHERS_EXECUTE] as Set
        FileUtils.toPosixFilePermission(02) == [OTHERS_WRITE] as Set
        FileUtils.toPosixFilePermission(04) == [OTHERS_READ] as Set
        FileUtils.toPosixFilePermission(07) == [OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE] as Set
        and:
        FileUtils.toPosixFilePermission(010) == [GROUP_EXECUTE] as Set
        FileUtils.toPosixFilePermission(020) == [GROUP_WRITE] as Set
        FileUtils.toPosixFilePermission(040) == [GROUP_READ] as Set
        FileUtils.toPosixFilePermission(070) == [GROUP_READ, GROUP_WRITE, GROUP_EXECUTE] as Set
        and:
        FileUtils.toPosixFilePermission(0100) == [OWNER_EXECUTE] as Set
        FileUtils.toPosixFilePermission(0200) == [OWNER_WRITE] as Set
        FileUtils.toPosixFilePermission(0400) == [OWNER_READ] as Set
        FileUtils.toPosixFilePermission(0700) == [OWNER_READ, OWNER_WRITE, OWNER_EXECUTE] as Set
        and:
        FileUtils.toPosixFilePermission(0644) == [OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ] as Set
    }

    def 'should convert permissions to actal' () {
        expect:
        FileUtils.toOctalFileMode([] as Set) == 0
        and:
        FileUtils.toOctalFileMode([OTHERS_EXECUTE] as Set) == 01
        FileUtils.toOctalFileMode([OTHERS_WRITE] as Set) == 02
        FileUtils.toOctalFileMode([OTHERS_READ] as Set) == 04
        FileUtils.toOctalFileMode([OTHERS_READ, OTHERS_WRITE, OTHERS_EXECUTE] as Set) == 07
        and:
        FileUtils.toOctalFileMode([GROUP_EXECUTE] as Set) == 010
        FileUtils.toOctalFileMode([GROUP_WRITE] as Set) == 020
        FileUtils.toOctalFileMode([GROUP_READ] as Set) == 040
        FileUtils.toOctalFileMode([GROUP_READ, GROUP_WRITE, GROUP_EXECUTE] as Set) == 070
        and:
        FileUtils.toOctalFileMode([OWNER_EXECUTE] as Set) == 0100
        FileUtils.toOctalFileMode([OWNER_WRITE] as Set) == 0200
        FileUtils.toOctalFileMode([OWNER_READ] as Set) == 0400
        FileUtils.toOctalFileMode([OWNER_READ, OWNER_WRITE, OWNER_EXECUTE] as Set) == 0700
        and:
        FileUtils.toOctalFileMode([OWNER_READ, OWNER_WRITE, GROUP_READ, OTHERS_READ] as Set) == 0644
    }

    def 'should read and write path file mode' () {
        given:
        def folder = Files.createTempDirectory('test')
        def file = Files.createFile(folder.resolve('file.txt'))
        def dirx = Files.createDirectory(folder.resolve('dirx'))

        when:
        FileUtils.setPermissionsMode(file, 0644)
        then:
        FileUtils.getPermissionsMode(file) == 0644
        FileUtils.getPermissions(file) == 'rw-r--r--'

        when:
        FileUtils.setPermissionsMode(dirx, 0755)
        then:
        FileUtils.getPermissionsMode(dirx) == 0755
        FileUtils.getPermissions(dirx) == 'rwxr-xr-x'

        cleanup:
        folder?.deleteDir()
    }

    def 'should read and write  file mode' () {
        given:
        def folder = Files.createTempDirectory('test')
        def file = Files.createFile(folder.resolve('file.txt')).toFile()
        def dirx = Files.createDirectory(folder.resolve('dirx')).toFile()

        when:
        FileUtils.setPermissionsMode(file, 0644)
        then:
        FileUtils.getPermissionsMode(file) == 0644
        FileUtils.getPermissions(file) == 'rw-r--r--'

        when:
        FileUtils.setPermissionsMode(dirx, 0755)
        then:
        FileUtils.getPermissionsMode(dirx) == 0755
        FileUtils.getPermissions(dirx) == 'rwxr-xr-x'

        cleanup:
        folder?.deleteDir()
    }
    
}

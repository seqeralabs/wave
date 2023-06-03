package io.seqera.wave.api;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.seqera.wave.api.ObjectUtils.isEmpty;

/**
 * Model Fusion version info
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class FusionVersion {

    static final private Pattern VERSION_REGEX = Pattern.compile(".*/v(\\d+(?:\\.\\w+)*)-(\\w*)\\.json$");

    final public String number;

    final public String arch;

    FusionVersion(String number, String arch) {
        this.number = number;
        this.arch = arch;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FusionVersion that = (FusionVersion) o;
        return Objects.equals(number, that.number) && Objects.equals(arch, that.arch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(number, arch);
    }

    static FusionVersion from(String uri) {
        if( isEmpty(uri) )
            return null;
        final Matcher matcher = VERSION_REGEX.matcher(uri);
        if( matcher.matches() ) {
            return new FusionVersion(matcher.group(1), matcher.group(2));
        }
        return null;
    }
}

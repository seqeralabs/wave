package io.seqera.wave.api;

import java.util.Objects;

public class ServiceInfo {

    /** Application version string */
    final public String version;

    /** Build commit ID */
    final public String commitId;

    public ServiceInfo(String version, String commitId) {
        this.version = version;
        this.commitId = commitId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInfo that = (ServiceInfo) o;
        return Objects.equals(version, that.version) && Objects.equals(commitId, that.commitId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, commitId);
    }
}



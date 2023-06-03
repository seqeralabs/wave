package io.seqera.wave.api;

import java.util.Objects;

/**
 * Implements Service info controller response object
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ServiceInfoResponse {
    public ServiceInfo serviceInfo;

    public ServiceInfoResponse() {}

    public ServiceInfoResponse(ServiceInfo info) {
        this.serviceInfo = info;
    }

    @Override
    public String toString() {
        return "ServiceInfoResponse{" +
                "serviceInfo=" + serviceInfo +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInfoResponse that = (ServiceInfoResponse) o;
        return Objects.equals(serviceInfo, that.serviceInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceInfo);
    }
}

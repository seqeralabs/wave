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

package io.seqera.wave.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Model the response of container provisioning status request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerStatusResponse {

    /**
     * Container request Id
     */
    final public String id;

    /**
     * Container request status
     */
    final public ContainerStatus status;

    /**
     * The build id associated with this request
     */
    final public String buildId;

    /**
     * The mirror id associated with this request
     */
    final public String mirrorId;

    /**
     * The security scan id associated with this request
     */
    final public String scanId;

    /**
     * Security vulnerability summary
     */
    final public Map<String,Integer> vulnerabilities;

    /**
     * Whenever the request is succeeded or not
     */
    final public Boolean succeeded;

    /**
     * Descriptive reason for returned status, used for failures
     */
    final public String reason;

    /**
     * Link to detail page
     */
    final public String detailsUri;

    /**
     * Container request time
     */
    final public Instant creationTime;

    /**
     * Duration to complete build
     */
    final public Duration duration;

    /*
     * required for serialization/deserialization
     */
    protected ContainerStatusResponse() {
        this.id = null;
        this.status = null;
        this.buildId = null;
        this.mirrorId =  null;
        this.creationTime = null;
        this.duration = null;
        this.succeeded = null;
        this.scanId = null;
        this.vulnerabilities = null;
        this.reason = null;
        this.detailsUri = null;
    }

    public ContainerStatusResponse(
                String id,
                ContainerStatus status,
                String buildId,
                String mirrorId,
                String scanId,
                Map<String,Integer> vulnerabilities,
                Boolean succeeded,
                String reason,
                String detailsUri,
                Instant creationTime,
                Duration duration
    )
    {
        this.id = id;
        this.status = status;
        this.buildId = buildId;
        this.mirrorId = mirrorId;
        this.scanId = scanId;
        this.vulnerabilities = vulnerabilities;
        this.succeeded = succeeded;
        this.reason = reason;
        this.detailsUri = detailsUri;
        this.creationTime = creationTime;
        this.duration = duration;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerStatusResponse that = (ContainerStatusResponse) o;
        return Objects.equals(id, that.id)
                && status == that.status
                && Objects.equals(buildId, that.buildId)
                && Objects.equals(mirrorId, that.mirrorId)
                && Objects.equals(scanId, that.scanId)
                && Objects.equals(creationTime, that.creationTime)
                && Objects.equals(duration, that.duration)
                && Objects.equals(succeeded, that.succeeded)
                && Objects.equals(vulnerabilities, that.vulnerabilities)
                && Objects.equals(reason, that.reason)
                && Objects.equals(detailsUri, that.detailsUri)
                ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status, buildId, mirrorId, scanId, creationTime, duration, succeeded, vulnerabilities, reason, detailsUri);
    }

    @Override
    public String toString() {
        return "ContainerStatusResponse{" +
                "id='" + id + '\'' +
                ", status=" + status +
                ", buildId='" + buildId + '\'' +
                ", mirrorId='" + mirrorId + '\'' +
                ", scanId='" + scanId + '\'' +
                ", creationTime=" + creationTime +
                ", duration=" + duration +
                ", succeeded=" + succeeded +
                ", vulnerabilities=" + vulnerabilities +
                ", reason='" + reason + '\'' +
                ", detailsUri='" + detailsUri + '\'' +
                '}';
    }
}

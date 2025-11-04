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
import java.util.Objects;


/**
 * Build status response
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
public class BuildStatusResponse {
    public enum Status { PENDING, COMPLETED }

    /** Build Id */
    final public String id;

    /** ContainerStatus of image build */
    final public Status status;

    /** Build start time */
    final public Instant startTime;

    /** Duration to complete build */
    final public Duration duration;

    /** Build success status */
    final public Boolean succeeded;

    /**
     * This is required to allow jackson serialization - do not remove
     */
    private BuildStatusResponse() {
        id = null;
        status = null;
        startTime = null;
        duration = null;
        succeeded = null;
    }

    public BuildStatusResponse(String id, Status status, Instant startTime, Duration duration, Boolean succeeded) {
        this.id = id;
        this.status = status;
        this.startTime = startTime;
        this.duration = duration;
        this.succeeded = succeeded;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        BuildStatusResponse that = (BuildStatusResponse) object;
        return Objects.equals(id, that.id)
                && status == that.status
                && Objects.equals(startTime, that.startTime)
                && Objects.equals(duration, that.duration)
                && Objects.equals(succeeded, that.succeeded)
                ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, status, startTime, duration, succeeded);
    }

    @Override
    public String toString() {
        return "BuildStatusResponse{" +
                "id='" + id + '\'' +
                ", status=" + status +
                ", startTime=" + startTime +
                ", duration=" + duration +
                ", succeeded=" + succeeded +
                '}';
    }
}

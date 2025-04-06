/*
 * Copyright 2023-2025, Seqera Labs
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

package io.seqera.wave.cli.model;

import java.time.Duration;
import java.util.Map;

import io.seqera.wave.api.ContainerStatus;
import io.seqera.wave.api.ContainerStatusResponse;
import io.seqera.wave.api.SubmitContainerTokenResponse;

/**
 * Extend the {@link SubmitContainerTokenResponse} object with extra fields
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class SubmitContainerTokenResponseEx extends SubmitContainerTokenResponse {

    /**
     * The status of this request
     */
    public ContainerStatus status;

    /**
     * The request duration
     */
    public Duration duration;

    /**
     * The found vulnerabilities
     */
    public Map<String,Integer> vulnerabilities;

    /**
     * Descriptive reason for returned status, used for failures
     */
    public String reason;

    /**
     * Link to detail page
     */
    public String detailsUri;

    public SubmitContainerTokenResponseEx(SubmitContainerTokenResponse resp1, ContainerStatusResponse resp2) {
        super(resp1);
        this.status = resp2.status;
        this.duration = resp2.duration;
        this.vulnerabilities = resp2.vulnerabilities;
        this.succeeded = resp2.succeeded;
        this.reason = resp2.reason;
        this.detailsUri = resp2.detailsUri;
    }

}

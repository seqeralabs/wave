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

import java.util.Objects;

/**
 * Model a container inspect request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerInspectRequest {

    /**
     * Container image to be pulled
     */
    public String containerImage;

    /**
     * Tower access token required to enable the service
     */
    public String towerAccessToken;

    /**
     * Tower endpoint: the public address
     * of the tower instance to integrate with wave
     */
    public String towerEndpoint;

    /**
     * Tower workspace id
     */
    public Long towerWorkspaceId;

    public ContainerInspectRequest withContainerImage(String image) {
        this.containerImage = image;
        return this;
    }

    public ContainerInspectRequest withTowerAccessToken(String token) {
        this.towerAccessToken = token;
        return this;
    }

    public ContainerInspectRequest withTowerEndpoint(String endpoint) {
        this.towerEndpoint = endpoint;
        return this;
    }

    public ContainerInspectRequest withTowerWorkspaceId(Long workspaceId) {
        this.towerWorkspaceId = workspaceId;
        return this;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ContainerInspectRequest that = (ContainerInspectRequest) object;
        return Objects.equals(containerImage, that.containerImage) && Objects.equals(towerAccessToken, that.towerAccessToken) && Objects.equals(towerEndpoint, that.towerEndpoint) && Objects.equals(towerWorkspaceId, that.towerWorkspaceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(containerImage, towerAccessToken, towerEndpoint, towerWorkspaceId);
    }

    @Override
    public String toString() {
        return "ContainerInspectRequest{" +
                "containerImage='" + containerImage + '\'' +
                ", towerAccessToken='" + towerAccessToken + '\'' +
                ", towerEndpoint='" + towerEndpoint + '\'' +
                ", towerWorkspaceId=" + towerWorkspaceId +
                '}';
    }
}

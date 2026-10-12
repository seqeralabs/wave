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

package io.seqera.wave.core.spec;

import java.util.Objects;

import io.seqera.wave.model.ContentType;

/**
 * Model container manifest and config metadata
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerSpec {
    String registry;
    String hostName;
    String imageName;
    String reference;
    String digest;
    ConfigSpec config;
    ManifestSpec manifest;

    /* REQUIRED BY SERIALIZATION */
    private ContainerSpec() {}

    public ContainerSpec(String registry, String hostName, String imageName, String reference, String digest, ConfigSpec config, ManifestSpec manifest) {
        this.registry = registry;
        this.hostName = hostName;
        this.imageName = imageName;
        this.reference = reference;
        this.digest = digest;
        this.config = config;
        this.manifest = manifest;
    }

    public ContainerSpec(ContainerSpec that) {
        this.registry = that.registry;
        this.hostName = that.hostName;
        this.imageName = that.imageName;
        this.reference = that.reference;
        this.digest = that.digest;
        this.config = that.config;
        this.manifest = that.manifest;
    }

    public String getRegistry() {
        return registry;
    }

    public String getHostName() {
        return hostName;
    }

    public String getImageName() {
        return imageName;
    }

    public String getReference() {
        return reference;
    }

    public String getDigest() {
        return digest;
    }

    public ConfigSpec getConfig() {
        return config;
    }

    public ManifestSpec getManifest() {
        return manifest;
    }

    public boolean isV1() { return manifest.schemaVersion==1; }

    public boolean isV2() { return manifest.schemaVersion==2; }

    public boolean isOci() { return ContentType.OCI_IMAGE_MANIFEST_V1.equals(manifest.mediaType); }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ContainerSpec that = (ContainerSpec) object;
        return Objects.equals(registry, that.registry)
                && Objects.equals(hostName, that.hostName)
                && Objects.equals(imageName, that.imageName)
                && Objects.equals(reference, that.reference)
                && Objects.equals(digest, that.digest)
                && Objects.equals(config, that.config)
                && Objects.equals(manifest, that.manifest);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registry, hostName, imageName, reference, digest, config, manifest);
    }

    @Override
    public String toString() {
        return "ContainerSpec{" +
                "registry='" + registry + '\'' +
                ", hostName='" + hostName + '\'' +
                ", imageName='" + imageName + '\'' +
                ", reference='" + reference + '\'' +
                ", digest='" + digest + '\'' +
                ", config=" + config +
                ", manifest=" + manifest +
                '}';
    }

}

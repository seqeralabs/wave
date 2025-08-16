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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.seqera.wave.core.spec.Helper.*;

/**
 * Model a container manifest specification
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ConfigSpec {

    static public class Config {
        public String hostName;
        public String domainName;
        public String user;
        public Boolean attachStdin;
        public Boolean attachStdout;
        public Boolean attachStderr;
        public Boolean tty;
        public List<String> env;
        public List<String> cmd;
        public String image;
        public String workingDir;
        public List<String> entrypoint;

        Config() {
            this(Map.of());
        }

        Config(Map opts) {
            this.hostName = (String) opts.get("Hostname");
            this.domainName = (String) opts.get("Domainname");
            this.user = (String) opts.get("User");
            this.attachStdin = asBoolean(opts.get("AttachStdin"));
            this.attachStdout = asBoolean(opts.get("AttachStdout"));
            this.attachStderr = asBoolean(opts.get("AttachStderr"));
            this.tty = asBoolean(opts.get("Tty"));
            this.env = opts.containsKey("Env") ? (List<String>) opts.get("Env") : List.<String>of();
            this.cmd = opts.containsKey("Cmd") ? (List<String>) opts.get("Cmd") : List.<String>of();
            this.image = (String) opts.get("Image");
            this.workingDir = (String) opts.get("WorkingDir");
            this.entrypoint = opts.containsKey("Entrypoint") ? (List<String>) opts.get("Entrypoint") : List.<String>of();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            Config config = (Config) object;
            return Objects.equals(hostName, config.hostName) && Objects.equals(domainName, config.domainName) && Objects.equals(user, config.user) && Objects.equals(attachStdin, config.attachStdin) && Objects.equals(attachStdout, config.attachStdout) && Objects.equals(attachStderr, config.attachStderr) && Objects.equals(tty, config.tty) && Objects.equals(env, config.env) && Objects.equals(cmd, config.cmd) && Objects.equals(image, config.image) && Objects.equals(workingDir, config.workingDir) && Objects.equals(entrypoint, config.entrypoint);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hostName, domainName, user, attachStdin, attachStdout, attachStderr, tty, env, cmd, image, workingDir, entrypoint);
        }

        @Override
        public String toString() {
            return "Config{" +
                    "hostName='" + hostName + '\'' +
                    ", domainName='" + domainName + '\'' +
                    ", user='" + user + '\'' +
                    ", attachStdin=" + attachStdin +
                    ", attachStdout=" + attachStdout +
                    ", attachStderr=" + attachStderr +
                    ", tty=" + tty +
                    ", env=" + env +
                    ", cmd=" + cmd +
                    ", image='" + image + '\'' +
                    ", workingDir='" + workingDir + '\'' +
                    ", entrypoint=" + entrypoint +
                    '}';
        }
    }

    static public class Rootfs {
        public String type;
        public List<String> diff_ids;

        Rootfs() {
            this(Map.of());
        }

        Rootfs(Map opts) {
            this.type = (String) opts.get("type");
            this.diff_ids = opts.containsKey("diff_ids") ? (List<String>) opts.get("diff_ids") : List.of();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            Rootfs rootfs = (Rootfs) object;
            return Objects.equals(type, rootfs.type) && Objects.equals(diff_ids, rootfs.diff_ids);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, diff_ids);
        }

        @Override
        public String toString() {
            return "Rootfs{" +
                    "type='" + type + '\'' +
                    ", diff_ids=" + diff_ids +
                    '}';
        }
    }

    public String architecture;
    public Config config;
    public String container;
    public Instant created;
    public Rootfs rootfs;

    ConfigSpec() {
        this(Map.of());
    }

    ConfigSpec(Map<String,?> opts) {
        this.architecture = (String) opts.get("architecture");
        this.container = (String) opts.get("container");
        this.config = new Config( opts.containsKey("config") ? (Map<String,?>) opts.get("config") : Map.of() );
        this.created = asInstant( opts.get("created") );
        this.rootfs = new Rootfs( opts.containsKey("rootfs") ? (Map<String,?>) opts.get("rootfs") : Map.of() );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ConfigSpec that = (ConfigSpec) object;
        return Objects.equals(architecture, that.architecture) && Objects.equals(config, that.config) && Objects.equals(container, that.container) && Objects.equals(created, that.created);
    }

    @Override
    public int hashCode() {
        return Objects.hash(architecture, config, container, created);
    }

    @Override
    public String toString() {
        return "ConfigSpec{" +
                "architecture='" + architecture + '\'' +
                ", config=" + config +
                ", container='" + container + '\'' +
                ", created=" + created +
                '}';
    }

    static public ConfigSpec parse(String manifest) {
        Map<String,Object> payload = fromJson(manifest,Map.class);
        return new ConfigSpec(payload);
    }

    static public ConfigSpec parseV1(Map<String,?> opts) {
        // fetch the history from the v1 manifest
        List<Map<String,String>> history = (List<Map<String,String>>) opts.get("history");
        if( history!=null && !history.isEmpty() ) {
            String configJson = history.get(0).get("v1Compatibility");
            Map<String,?> configObj = fromJson(configJson,Map.class);
            return new ConfigSpec( configObj );
        }
        throw new IllegalArgumentException("Invalid Docker v1 manifest");
    }

    static public ConfigSpec parseV1(String manifest) {
        // parse the content
        final Map<String,Object> opts = fromJson(manifest,Map.class);
        return parseV1(opts);
    }

}

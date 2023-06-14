package io.seqera.wave.api;


import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.seqera.wave.api.ObjectUtils.isEmpty;

/**
 * Model a container configuration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerConfig {

    public  List<String> entrypoint;

    public List<String> cmd;

    public List<String> env;

    public String workingDir;

    public List<ContainerLayer> layers;

    ContainerConfig() {
        entrypoint = null;
        cmd = null;
        env = null;
        workingDir = null;
        layers = new ArrayList<>();
    }

    public ContainerConfig(List<String> entrypoint, List<String> cmd, List<String> env, String workDir, List<ContainerLayer> layers) {
        this.entrypoint = entrypoint;
        this.cmd = cmd;
        this.env = env;
        this.workingDir = workDir;
        this.layers = layers;
    }

    /**
     * Implements Groovy truth
     * 
     * @return {@code true} when at lest one field is populated or {@code false} otherwise
     */
    public boolean asBoolean() {
        return !empty();
    }

    public boolean empty() {
        return isEmpty(entrypoint) &&
                isEmpty(cmd) &&
                isEmpty(env) &&
                isEmpty(workingDir) &&
                isEmpty(layers);
    }

    public void validate(){
        for( ContainerLayer it : layers ) {
            it.validate();
        }
    }

    public boolean hasFusionLayer() {
        if( isEmpty(layers) )
            return false;
        for( ContainerLayer it : layers ) {
            if( !isEmpty(it.location) && it.location.contains("https://fusionfs.seqera.io") )
                return true;
        }
        return false;
    }

    public FusionVersion fusionVersion() {
        if( isEmpty(layers) )
            return null;
        for( ContainerLayer it : layers ) {
            if( !isEmpty(it.location) && it.location.startsWith("https://fusionfs.seqera.io/") )
                return FusionVersion.from(it.location);
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("ContainerConfig[entrypoint=%s; cmd=%s; env=%s; workingDir=%s; layers=%s]", entrypoint, cmd, env, workingDir, layers);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContainerConfig that = (ContainerConfig) o;
        return Objects.equals(entrypoint, that.entrypoint) && Objects.equals(cmd, that.cmd) && Objects.equals(env, that.env) && Objects.equals(workingDir, that.workingDir) && Objects.equals(layers, that.layers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entrypoint, cmd, env, workingDir, layers);
    }

    static ContainerConfig copy(ContainerConfig that) {
        return copy(that, false);
    }

    /**
     * Copy method
     *
     * @param that The {@link ContainerConfig} to be copied from
     */
    static public ContainerConfig copy(ContainerConfig that, boolean stripData) {
        if( that==null )
            return null;
        return new ContainerConfig(
                that.entrypoint!=null ? new ArrayList<>(that.entrypoint) : null,
                that.cmd!=null ? new ArrayList<>(that.cmd) : null,
                that.env!=null ? new ArrayList<>(that.env) : null,
                that.workingDir,
                that.layers != null ? that.layers.stream().map(it -> ContainerLayer.copy(it,stripData)).collect(Collectors.toList()) : null
        );
    }

}

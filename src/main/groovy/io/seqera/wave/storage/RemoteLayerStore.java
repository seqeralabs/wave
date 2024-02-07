package io.seqera.wave.storage;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public interface RemoteLayerStore {

    String getLocation();

    default boolean isDockerLayer() {
        final String l = getLocation();
        return l!=null && l.startsWith("docker://");
    }
}

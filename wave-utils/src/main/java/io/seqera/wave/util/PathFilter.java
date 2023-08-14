package io.seqera.wave.util;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class to filter paths
 *
 * @author Munish Chouhan munish.chouhan@seqera.io
 */
public class PathFilter {

    private Set<PathMatcher> matchers;

    public PathFilter(Set<String> patterns) {
        this.matchers = patterns.stream()
                .map(pattern -> FileSystems.getDefault().getPathMatcher("glob:" + pattern))
                .collect(Collectors.toSet());
    }

    public boolean accept(Path path){
        return !matchers.stream().anyMatch(matcher -> matcher.matches(path));
    }

}

package io.seqera.wave.util;

import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Utility class to filter paths
 *
 * @author Munish Chouhan munish.chouhan@seqera.io
 */
public class PathFilter {

    private Set<String> patterns;

    private final String EXCEPTION_PATTERN_MARKER = "!";

    Map<String, PathMatcher> mactherMap = new HashMap<>();

    public PathFilter(LinkedHashSet<String> patterns) {
        this.patterns = patterns;
        FileSystem fileSystem = FileSystems.getDefault();
        for (String pattern : patterns){
            if (pattern.startsWith(EXCEPTION_PATTERN_MARKER)){
                mactherMap.put(pattern,fileSystem.getPathMatcher("glob:" + pattern.substring(1)));
            }else{
                mactherMap.put(pattern,fileSystem.getPathMatcher("glob:" + pattern));
            }
        }
    }

    public boolean accept(Path path) {
        boolean accepted = true;

        for (String pattern : patterns) {
            if (mactherMap.get(pattern).matches(path)) {
                if (pattern.startsWith(EXCEPTION_PATTERN_MARKER)) {
                    accepted = true;
                } else {
                    accepted = false;
                }
            }
        }

        return accepted;
    }
}

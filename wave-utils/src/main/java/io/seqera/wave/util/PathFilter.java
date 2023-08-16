package io.seqera.wave.util;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Utility class to filter paths
 *
 * @author Munish Chouhan munish.chouhan@seqera.io
 */
public class PathFilter {

    private Set<String> patterns;

    private final String EXCEPTION_PATTERN_MARKER = "!";

    public PathFilter(LinkedHashSet<String> patterns) {
        this.patterns = patterns;
    }

    public boolean accept(Path path) {
        boolean accepted = true;
        for (String pattern : patterns) {
            System.out.println(path);
            if (pattern.startsWith(EXCEPTION_PATTERN_MARKER)) {
                if (FileSystems.getDefault().getPathMatcher("glob:" + pattern.substring(1)).matches(path)) {
                    accepted = true;
                }
            } else if (FileSystems.getDefault().getPathMatcher("glob:" + pattern).matches(path)) {
                accepted = false;
            }
        }
        return accepted;
    }
}

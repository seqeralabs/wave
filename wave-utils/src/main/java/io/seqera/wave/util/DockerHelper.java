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

package io.seqera.wave.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.representer.Representer;

/**
 * Helper class to create Dockerfile for Conda package manager.
 * This class provides file-based utility methods for Nextflow CLI integration.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class DockerHelper {

    /**
     * Create a Conda environment file starting from one or more Conda package names
     *
     * @param packages
     *      A string listing one or more Conda package names separated with a blank character
     *      e.g. {@code samtools=1.0 bedtools=2.0}
     * @param condaChannels
     *      A list of Conda channels
     * @return
     *      A path to the Conda environment YAML file. The file is automatically deleted when the JVM exits.
     */
    static public Path condaFileFromPackages(String packages, List<String> condaChannels) {
        final String yaml = condaPackagesToCondaYaml(packages, condaChannels);
        if (yaml == null || yaml.isEmpty())
            return null;
        return toYamlTempFile(yaml);
    }

    static public List<String> condaPackagesToList(String packages) {
        if (packages == null || packages.isEmpty())
            return null;
        return Arrays
                .stream(packages.split(" "))
                .filter(it -> !StringUtils.isEmpty(it))
                .map(it -> trim0(it)).collect(Collectors.toList());
    }

    static List<String> pipPackagesToList(String packages) {
        return condaPackagesToList(packages);
    }

    protected static String trim0(String value) {
        if( value==null )
            return null;
        value = value.trim();
        while( value.startsWith("'") && value.endsWith("'") )
            value = value.substring(1,value.length()-1);
        while( value.startsWith("\"") && value.endsWith("\"") )
            value = value.substring(1,value.length()-1);
        return value;
    }

    public static String condaPackagesToCondaYaml(String packages, List<String> channels) {
        if( packages==null || packages.isBlank() )
            return null;

        final List<Object> deps = new ArrayList<>(20);
        final List<Object> condaPackages = new ArrayList<>(10);
        final List<Object> pipPackages = new ArrayList<>(10);
        // split conda package by pip prefixed packages
        for( String it : condaPackagesToList(packages) ) {
            if( it.startsWith("pip::") )
                throw new IllegalArgumentException(String.format("Invalid pip prefix - Likely you want to use '%s' instead of '%s'", it.replaceAll(":+",":"), it));
            else if( it.startsWith("pip:") )
                pipPackages.add(it.substring(4));
            else
                condaPackages.add(it);
        }

        // add all conda packages
        if( !condaPackages.isEmpty() )
            deps.addAll(condaPackages);

        // add all pip packages
        if( !pipPackages.isEmpty() ) {
            deps.add("pip");
            deps.add(Map.of("pip", pipPackages));
        }

        // add the channels
        final Map<String, Object> conda = new LinkedHashMap<>();
        if (channels != null && !channels.isEmpty() ) {
            conda.put("channels", channels);
        }

        // assemble the final yaml
        conda.put("dependencies", deps);
        return dumpCondaYaml(conda);
    }

    static private String dumpCondaYaml(Map<String, Object> conda) {
        DumperOptions dumperOpts = new DumperOptions();
        dumperOpts.setPrettyFlow(false); // Disable pretty formatting
        dumperOpts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK); // Use block style
        return new Yaml(new Representer(dumperOpts), dumperOpts).dump(conda);
    }

    /**
     * Get a Conda environment file from a string path.
     *
     * @param condaFile
     *      A file system path where the Conda environment file is located.
     * @param channels
     *      A list of Conda channels. If provided the channels are added to the ones
     *      specified in the Conda environment files.
     * @return
     *      A {@link Path} to the Conda environment file. It can be the same file as specified
     *      via the condaFile argument or a temporary file if the environment was modified due to
     *      the channels or options specified.
     */
    public static Path condaFileFromPath(String condaFile, List<String> channels) {
        if( StringUtils.isEmpty(condaFile) )
            throw new IllegalArgumentException("Argument 'condaFile' cannot be empty");

        final Path condaEnvPath = Path.of(condaFile);

        // make sure the file exists
        if( !Files.exists(condaEnvPath) ) {
            throw new IllegalArgumentException("The specified Conda environment file cannot be found: " + condaFile);
        }

        // if there's nothing to be merged just return the conda file path
        if( channels==null ) {
            return condaEnvPath;
        }

        // => parse the conda file yaml, add the base packages to it
        try {
            final String result = condaEnvironmentToCondaYaml(Files.readString(condaEnvPath), channels);
            return toYamlTempFile(result);
        }
        catch (FileNotFoundException e) {
            throw new IllegalArgumentException("The specified Conda environment file cannot be found: " + condaFile, e);
        }
        catch (IOException e) {
            throw new IllegalArgumentException("Unable to parse conda file: " + condaFile, e);
        }
    }

    public static String condaEnvironmentToCondaYaml(String env, List<String> channels) {
        final Yaml yaml = new Yaml();
        // 1. parse the file
        Map<String,Object> root = yaml.load(env);
        // 2. append channels
        if( channels!=null ) {
            List<String> channels0 = (List<String>)root.get("channels");
            if( channels0==null ) {
                channels0 = new ArrayList<>();
                root.put("channels", channels0);
            }
            for( String it : channels ) {
                if( !channels0.contains(it) )
                    channels0.add(it);
            }
        }
        // 3. return it
        return dumpCondaYaml(root);
    }

    static private Path toYamlTempFile(String yaml) {
        try {
            final File tempFile = File.createTempFile("nf-temp", ".yaml");
            tempFile.deleteOnExit();
            final Path result = tempFile.toPath();
            Files.write(result, yaml.getBytes());
            return result;
        }
        catch (IOException e) {
            throw new IllegalStateException("Unable to write temporary file - Reason: " + e.getMessage(), e);
        }
    }

    /**
     * Valid Dockerfile instruction keywords that can start a command.
     * See: https://docs.docker.com/engine/reference/builder/
     */
    private static final Set<String> VALID_DOCKER_KEYWORDS = Set.of(
            "FROM",
            "RUN",
            "CMD",
            "LABEL",
            "EXPOSE",
            "ENV",
            "ADD",
            "COPY",
            "ENTRYPOINT",
            "VOLUME",
            "USER",
            "WORKDIR",
            "ARG",
            "ONBUILD",
            "STOPSIGNAL",
            "HEALTHCHECK",
            "SHELL"
    );

    /**
     * Pattern to extract the first word from a command (the instruction keyword).
     * Matches the first non-whitespace sequence of characters.
     */
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("^\\s*([A-Z]+)\\b");

    /**
     * Validates that a command starts with a valid Dockerfile instruction keyword.
     *
     * @param command The command to validate
     * @return true if the command starts with a valid Docker keyword, false otherwise
     */
    public static boolean isValidDockerCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }

        Matcher matcher = KEYWORD_PATTERN.matcher(command);
        if (!matcher.find()) {
            return false;
        }

        String keyword = matcher.group(1);
        return VALID_DOCKER_KEYWORDS.contains(keyword);
    }

    /**
     * Validates a list of commands to ensure they all start with valid Dockerfile instruction keywords.
     *
     * @param commands The list of commands to validate
     * @throws IllegalArgumentException if any command is invalid
     */
    public static void validateCommands(List<String> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }

        for (int i = 0; i < commands.size(); i++) {
            String command = commands.get(i);
            if (!isValidDockerCommand(command)) {
                throw new IllegalArgumentException(
                        String.format(
                                "Invalid Docker command at index %d: '%s'. Commands must start with a valid Dockerfile instruction keyword (e.g., RUN, CMD, ENV, COPY, etc.)",
                                i,
                                command
                        )
                );
            }
        }
    }

    /**
     * Gets the set of valid Docker instruction keywords.
     *
     * @return An unmodifiable set of valid Docker keywords
     */
    public static Set<String> getValidKeywords() {
        return VALID_DOCKER_KEYWORDS;
    }

}

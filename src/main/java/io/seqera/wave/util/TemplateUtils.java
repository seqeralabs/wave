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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.seqera.wave.config.CondaOpts;
import io.seqera.wave.config.PixiOpts;
import org.apache.commons.lang3.StringUtils;

/**
 * Helper class to create Dockerfile for Conda package manager
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class TemplateUtils {

    static public String condaPackagesToDockerFile(String packages, List<String> condaChannels, CondaOpts opts) {
        return condaPackagesTemplate0(
                "/templates/conda-micromamba-v1/dockerfile-conda-packages.txt",
                packages,
                condaChannels,
                opts);
    }

    static public String condaPackagesToSingularityFile(String packages, List<String> condaChannels, CondaOpts opts) {
        return condaPackagesTemplate0(
                "/templates/conda-micromamba-v1/singularityfile-conda-packages.txt",
                packages,
                condaChannels,
                opts);
    }

    @Deprecated
    static protected String condaPackagesTemplate0(String template, String packages, List<String> condaChannels, CondaOpts opts) {
        final List<String> channels0 = condaChannels!=null ? condaChannels : List.of();
        final String channelsOpts = channels0.stream().map(it -> "-c "+it).collect(Collectors.joining(" "));
        final String image = opts.mambaImage;
        final boolean singularity = template.contains("/singularityfile");
        final String target = packages.startsWith("http://") || packages.startsWith("https://")
                ? "-f " + packages
                : packages;
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", image);
        binding.put("channel_opts", channelsOpts);
        binding.put("target", target);
        binding.put("base_packages", mambaInstallBasePackage0(opts.basePackages,singularity));

        final String result = renderTemplate0(template, binding) ;
        return addCommands(result, opts.commands, singularity);
    }

    static protected String condaPackagesTemplate1(String template, String packages, List<String> condaChannels, CondaOpts opts) {
        final List<String> channels0 = condaChannels!=null ? condaChannels : List.of();
        final String channelsOpts = channels0.stream().map(it -> "-c "+it).collect(Collectors.joining(" "));
        final boolean singularity = template.contains("/singularityfile");
        final String target = packages.startsWith("http://") || packages.startsWith("https://")
                ? "-f " + packages
                : packages;
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", opts.baseImage);
        binding.put("mamba_image", opts.mambaImage);
        binding.put("channel_opts", channelsOpts);
        binding.put("target", target);
        binding.put("base_packages", mambaInstallBasePackage0(opts.basePackages,singularity));

        final String result = renderTemplate0(template, binding) ;
        return addCommands(result, opts.commands, singularity);
    }

    static public String condaFileToDockerFile(CondaOpts opts) {
        return condaFileTemplate0("/templates/conda-micromamba-v1/dockerfile-conda-file.txt", opts);
    }

    static public String condaFileToSingularityFile(CondaOpts opts) {
        return condaFileTemplate0("/templates/conda-micromamba-v1/singularityfile-conda-file.txt", opts);
    }

    static public String condaFileToDockerFileUsingV2(CondaOpts opts) {
        return condaFileTemplateV2("/templates/conda-micromamba-v2/dockerfile-conda-file.txt", opts);
    }

    static public String condaFileToSingularityFileV2(CondaOpts opts) {
        return condaFileTemplateV2("/templates/conda-micromamba-v2/singularityfile-conda-file.txt", opts);
    }

    static public String condaFileToDockerFileUsingPixi(PixiOpts opts) {
        return condaFileTemplate1("/templates/conda-pixi-v1/dockerfile-conda-file.txt", opts);
    }

    static public String condaFileToSingularityFileUsingPixi(PixiOpts opts) {
        return condaFileTemplate1("/templates/conda-pixi-v1/singularityfile-conda-file.txt", opts);
    }

    static public String condaPackagesToDockerFileUsingV2(String packages, List<String> condaChannels, CondaOpts opts) {
        return condaPackagesTemplate1(
                "/templates/conda-micromamba-v2/dockerfile-conda-packages.txt",
                packages,
                condaChannels,
                opts);
    }

    static public String condaPackagesToSingularityFileV2(String packages, List<String> condaChannels, CondaOpts opts) {
        return condaPackagesTemplate1(
                "/templates/conda-micromamba-v2/singularityfile-conda-packages.txt",
                packages,
                condaChannels,
                opts);
    }

    static protected String condaFileTemplate0(String template, CondaOpts opts) {
        final boolean singularity = template.contains("/singularityfile");
        // create the binding map
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", opts.mambaImage);
        binding.put("base_packages", mambaInstallBasePackage0(opts.basePackages,singularity));

        final String result = renderTemplate0(template, binding, List.of("wave_context_dir"));
        return addCommands(result, opts.commands, singularity);
    }

    static protected String condaFileTemplateV2(String template, CondaOpts opts) {
        final boolean singularity = template.contains("/singularityfile");
        // create the binding map
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", opts.baseImage);
        binding.put("mamba_image", opts.mambaImage);
        binding.put("base_packages", mambaInstallBasePackage0(opts.basePackages,singularity));

        final String result = renderTemplate0(template, binding, List.of("wave_context_dir"));
        return addCommands(result, opts.commands, singularity);
    }

    static protected String condaFileTemplate1(String template, PixiOpts opts) {
        final boolean singularity = template.contains("/singularityfile");
        // create the binding map
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", opts.baseImage);
        binding.put("pixi_image", opts.pixiImage);
        binding.put("base_packages", pixiAddBasePackage0(opts.basePackages,singularity));

        final String result = renderTemplate0(template, binding, List.of("wave_context_dir"));
        return addCommands(result, opts.commands, singularity);
    }

    static private String renderTemplate0(String templatePath, Map<String,String> binding) {
        return renderTemplate0(templatePath, binding, List.of());
    }

    static private String renderTemplate0(String templatePath, Map<String,String> binding, List<String> ignore) {
        final URL template = TemplateUtils.class.getResource(templatePath);
        if( template==null )
            throw new IllegalStateException(String.format("Unable to load template '%s' from classpath", templatePath));
        try {
            final InputStream reader = template.openStream();
            return new TemplateRenderer()
                    .withIgnore(ignore)
                    .render(reader, binding);
        }
        catch (IOException e) {
            throw new IllegalStateException(String.format("Unable to read classpath template '%s'", templatePath), e);
        }
    }

    private static String mambaInstallBasePackage0(String basePackages, boolean singularity) {
        String result = !StringUtils.isEmpty(basePackages)
                ? String.format("micromamba install -y -n base %s", basePackages)
                : null;
        return result==null || singularity
                ? result
                : "&& " + result + " \\";
    }

    private static String pixiAddBasePackage0(String basePackages, boolean singularity) {
        String result = !StringUtils.isEmpty(basePackages)
                ? String.format("pixi add %s", basePackages)
                : null;
        return result==null || singularity
                ? result
                : "&& " + result + " \\";
    }

    static private String addCommands(String result, List<String> commands, boolean singularity) {
        if( commands==null || commands.isEmpty() )
            return result;
        if( singularity )
            result += "%post\n";
        for( String cmd : commands ) {
            if( singularity ) result += "    ";
            result += cmd + "\n";
        }
        return result;
    }

}

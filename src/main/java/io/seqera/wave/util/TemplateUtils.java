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
import java.util.Base64;
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

    static public String pixiLockUrlToDockerFile(String lockUrl, PixiOpts opts) {
        return pixiLockUrlTemplate("/templates/conda-pixi-lock-v1/dockerfile-pixi-lock-url.txt", lockUrl, opts);
    }

    static public String pixiLockUrlToSingularityFile(String lockUrl, PixiOpts opts) {
        return pixiLockUrlTemplate("/templates/conda-pixi-lock-v1/singularityfile-pixi-lock-url.txt", lockUrl, opts);
    }

    static public String pixiLockFileToDockerFile(PixiOpts opts) {
        return pixiLockFileTemplate("/templates/conda-pixi-lock-v1/dockerfile-pixi-lock-file.txt", opts);
    }

    static public String pixiLockFileToSingularityFile(PixiOpts opts) {
        return pixiLockFileTemplate("/templates/conda-pixi-lock-v1/singularityfile-pixi-lock-file.txt", opts);
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

    static public String pixiTomlFileToDockerFile(PixiOpts opts) {
        return pixiTomlFileTemplate("/templates/conda-pixi-toml-v1/dockerfile-pixi-toml-file.txt", opts);
    }

    static public String pixiTomlUrlToDockerFile(String tomlUrl, PixiOpts opts) {
        return pixiTomlUrlTemplate("/templates/conda-pixi-toml-v1/dockerfile-pixi-toml-url.txt", tomlUrl, opts);
    }

    static public String pixiTomlFileToSingularityFile(PixiOpts opts) {
        return pixiTomlFileTemplate("/templates/conda-pixi-toml-v1/singularityfile-pixi-toml-file.txt", opts);
    }

    static public String pixiTomlUrlToSingularityFile(String tomlUrl, PixiOpts opts) {
        return pixiTomlUrlTemplate("/templates/conda-pixi-toml-v1/singularityfile-pixi-toml-url.txt", tomlUrl, opts);
    }

    static protected String pixiTomlFileTemplate(String template, PixiOpts opts) {
        final boolean singularity = template.contains("/singularityfile");
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", opts.baseImage);
        binding.put("pixi_image", opts.pixiImage);

        final String result = renderTemplate0(template, binding, List.of("wave_context_dir"));
        return addCommands(result, opts.commands, singularity);
    }

    static protected String pixiTomlUrlTemplate(String template, String tomlUrl, PixiOpts opts) {
        final boolean singularity = template.contains("/singularityfile");
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", opts.baseImage);
        binding.put("pixi_image", opts.pixiImage);
        binding.put("toml_url", tomlUrl);

        final String result = renderTemplate0(template, binding);
        return addCommands(result, opts.commands, singularity);
    }

    static protected String pixiLockUrlTemplate(String template, String lockUrl, PixiOpts opts) {
        final boolean singularity = template.contains("/singularityfile");
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", opts.baseImage);
        binding.put("pixi_image", opts.pixiImage);
        binding.put("lock_url", lockUrl);
        binding.put("manifest_generate", pixiManifestGenerate(opts.manifest, singularity));
        binding.put("base_packages", pixiGlobalInstallBasePackage0(opts.basePackages, singularity));

        final String result = renderTemplate0(template, binding);
        return addCommands(result, opts.commands, singularity);
    }

    static protected String pixiLockFileTemplate(String template, PixiOpts opts) {
        final boolean singularity = template.contains("/singularityfile");
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", opts.baseImage);
        binding.put("pixi_image", opts.pixiImage);
        binding.put("manifest_generate", pixiManifestGenerate(opts.manifest, singularity));
        binding.put("base_packages", pixiGlobalInstallBasePackage0(opts.basePackages, singularity));

        final String result = renderTemplate0(template, binding, List.of("wave_context_dir"));
        return addCommands(result, opts.commands, singularity);
    }

    /**
     * Generate the shell commands to create pixi.toml from either a provided manifest
     * or by extracting metadata from the lock file.
     */
    private static String pixiManifestGenerate(String manifest, boolean singularity) {
        if (manifest != null && !manifest.isEmpty()) {
            // Manifest provided: decode from base64 at build time
            final String encoded = Base64.getEncoder().encodeToString(manifest.getBytes());
            if (singularity) {
                return "printf '%s' '" + encoded + "' | base64 -d > pixi.toml";
            } else {
                return "printf '%s' '" + encoded + "' | base64 -d > pixi.toml \\";
            }
        }
        // No manifest: generate from lock file by extracting channels, platforms, and deps
        final String awkDeps = "awk '/- conda:/{n=split($NF,u,\"/\");f=u[n];sub(/\\.(conda|tar\\.bz2)$/,\"\",f);m=split(f,s,\"-\");v=0;for(i=2;i<=m;i++)if(s[i]~/^[0-9]/&&index(s[i],\".\")>0){v=i;break};if(!v)for(i=2;i<=m;i++)if(s[i]~/^[0-9]/){v=i;break};nm=\"\";for(i=1;i<(v?v:m+1);i++){if(nm)nm=nm\"-\";nm=nm s[i]};if(nm)print nm\" = \\\\\"*\\\\\"\"}' pixi.lock | sort -u > /tmp/deps.toml";
        final String awkPlatforms = "PLATFORMS=$(awk '/- conda:/{n=split($NF,u,\"/\");p=u[n-1];if(p!=\"noarch\")print p}' pixi.lock | sort -u | awk '{printf \"\\\\\"%s\\\\\", \", $0}' | sed 's/, $//')";
        final String awkChannels = "CHANNELS=$(awk '/^    channels:/{ch=1;next} ch&&/- url:/{gsub(/^ *- url: */,\"\");gsub(/https:\\/\\/conda\\.anaconda\\.org\\//,\"\");gsub(/\\/$/,\"\");printf \"\\\\\"%s\\\\\", \",$0;next} ch&&!/^    -/{exit}' pixi.lock | sed 's/, $//')";
        final String printfToml = "printf \"[workspace]\\nname = \\\"wave-env\\\"\\nchannels = [%s]\\nplatforms = [%s]\\n\\n[dependencies]\\n\" \"$CHANNELS\" \"$PLATFORMS\" > pixi.toml";
        final String catDeps = "cat /tmp/deps.toml >> pixi.toml";

        if (singularity) {
            return awkPlatforms + "\n    " + awkChannels + "\n    " + awkDeps + "\n    " + printfToml + "\n    " + catDeps;
        } else {
            return awkPlatforms + " \\\n    && " + awkChannels + " \\\n    && " + awkDeps + " \\\n    && " + printfToml + " \\\n    && " + catDeps + " \\";
        }
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

    /**
     * Install base packages in an isolated pixi global prefix so that the project
     * environment installed via {@code pixi install --frozen} (and its lock file) is
     * left untouched. This is used by the pixi lock build templates to add runtime
     * tools such as {@code procps-ng} (providing the {@code ps} command) without
     * re-solving the locked environment.
     */
    private static String pixiGlobalInstallBasePackage0(String basePackages, boolean singularity) {
        String result = !StringUtils.isEmpty(basePackages)
                ? String.format("pixi global install %s", basePackages)
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

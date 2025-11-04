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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.seqera.wave.config.CranOpts;
import org.apache.commons.lang3.StringUtils;

/**
 * Helper class to create Dockerfile for CRAN/R package manager
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class CranHelper {

    static public List<String> cranPackagesToList(String packages) {
        if (packages == null || packages.isEmpty())
            return null;
        return Arrays
                .stream(packages.split(" "))
                .filter(it -> !StringUtils.isEmpty(it))
                .map(it -> trim0(it)).collect(Collectors.toList());
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

    static public String cranPackagesToDockerFile(String packages, List<String> repositories, CranOpts opts) {
        return cranPackagesTemplate0(
                "/templates/cran/dockerfile-cran-packages.txt",
                packages,
                repositories,
                opts);
    }

    static public String cranPackagesToSingularityFile(String packages, List<String> repositories, CranOpts opts) {
        return cranPackagesTemplate0(
                "/templates/cran/singularityfile-cran-packages.txt",
                packages,
                repositories,
                opts);
    }

    static protected String cranPackagesTemplate0(String template, String packages, List<String> repositories, CranOpts opts) {
        final List<String> repos0 = repositories!=null ? repositories : List.of();
        final boolean singularity = template.contains("/singularityfile");
        final String image = opts.rImage;
        final String target = formatPackageTarget(packages);
        final String basePackages = rInstallBasePackage0(opts.basePackages, singularity);
        final String repoOpts = buildRepositoryOptions(repos0, singularity);
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", image);
        binding.put("repo_opts", repoOpts);
        binding.put("target", target);
        String basePackagesStr = singularity ? "" : "\\";
        if (basePackages != null) {
            if (singularity) {
                basePackagesStr += "\n    " + basePackages;
            } else {
                basePackagesStr += "\n    && " + basePackages + " \\";
            }
        }
        binding.put("base_packages", basePackagesStr);

        final String result = renderTemplate0(template, binding) ;
        return addCommands(result, opts.commands, singularity);
    }

    static public String cranFileToDockerFile(CranOpts opts) {
        return cranFileTemplate0("/templates/cran/dockerfile-cran-file.txt", opts);
    }

    static public String cranFileToSingularityFile(CranOpts opts) {
        return cranFileTemplate0("/templates/cran/singularityfile-cran-file.txt", opts);
    }

    static protected String cranFileTemplate0(String template, CranOpts opts) {
        final boolean singularity = template.contains("/singularityfile");
        final String basePackages = rInstallBasePackage0(opts.basePackages, singularity);
        final Map<String,String> binding = new HashMap<>();
        binding.put("base_image", opts.rImage);
        binding.put("base_packages", basePackages != null ? basePackages : "");

        final String result = renderTemplate0(template, binding, List.of("wave_context_dir"));
        return addCommands(result, opts.commands, singularity);
    }

    private static String buildRepositoryOptions(List<String> repositories, boolean singularity) {
        String repoSetup;
        if (repositories.isEmpty()) {
            // Default to CRAN if no repositories specified
            repoSetup = "R -e \"options(repos = c(CRAN = 'https://cloud.r-project.org/'))\"";
        } else {
            final String repoCommands = repositories.stream()
                    .map(repo -> {
                        if ("bioconductor".equalsIgnoreCase(repo)) {
                            return "options(BioC_mirror = 'https://bioconductor.org')";
                        } else if ("cran".equalsIgnoreCase(repo)) {
                            return "options(repos = c(CRAN = 'https://cloud.r-project.org/'))";
                        } else {
                            return String.format("options(repos = c(CRAN = '%s'))", repo);
                        }
                    })
                    .collect(Collectors.joining("; "));
            repoSetup = String.format("R -e \"%s\"", repoCommands);
        }

        if (singularity) {
            return repoSetup;
        } else {
            // For dockerfile: add proper line continuation
            return repoSetup + " ";
        }
    }

    private static String formatPackageTarget(String packages) {
        if (packages.startsWith("http://") || packages.startsWith("https://")) {
            return packages;
        }

        final List<String> packageList = cranPackagesToList(packages);
        if (packageList == null || packageList.isEmpty()) {
            return "";
        }

        return packageList.stream()
                .map(pkg -> {
                    if (pkg.startsWith("bioc::")) {
                        return String.format("BiocManager::install('%s')", pkg.substring(6));
                    } else {
                        return String.format("'%s'", pkg);
                    }
                })
                .collect(Collectors.joining(" "));
    }

    private static String renderTemplate0(String templatePath, Map<String,String> binding) {
        return renderTemplate0(templatePath, binding, List.of());
    }

    private static String renderTemplate0(String templatePath, Map<String,String> binding, List<String> ignore) {
        final URL template = CranHelper.class.getResource(templatePath);
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

    private static String rInstallBasePackage0(String basePackages, boolean singularity) {
        if (StringUtils.isEmpty(basePackages)) {
            return null;
        }

        return String.format("apt-get update && apt-get install -y %s", basePackages);
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
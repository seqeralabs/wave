/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.util

import groovy.transform.CompileStatic
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.config.CranOpts
import io.seqera.wave.exception.BadRequestException
import org.apache.commons.lang3.StringUtils

/**
 * Helper class to create Dockerfile for CRAN/R package manager
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class CranHelper {

    /**
     * Generate a container file (Dockerfile or Singularity) for R/CRAN packages.
     * This is the main entry point for CRAN_V1 template builds.
     *
     * @param spec The packages specification (must be CRAN type)
     * @param singularity When true, generates Singularity format; otherwise Dockerfile
     * @return The generated container file content
     * @throws BadRequestException if package type is not CRAN
     */
    static String containerFile(PackagesSpec spec, boolean singularity) {
        if( spec.type != PackagesSpec.Type.CRAN ) {
            throw new BadRequestException("Package type '${spec.type}' not supported by 'cran/installr/v1' build template")
        }

        if( !spec.cranOpts )
            spec.cranOpts = new CranOpts()

        if( spec.entries ) {
            final String packages = spec.entries.join(' ')
            return singularity
                    ? cranPackagesToSingularityFile(packages, spec.channels, spec.cranOpts)
                    : cranPackagesToDockerFile(packages, spec.channels, spec.cranOpts)
        }
        else {
            return singularity
                    ? cranFileToSingularityFile(spec.cranOpts)
                    : cranFileToDockerFile(spec.cranOpts)
        }
    }

    static List<String> cranPackagesToList(String packages) {
        if( packages == null || packages.isEmpty() )
            return null
        return packages.split(' ')
                .findAll { !StringUtils.isEmpty(it) }
                .collect { trim0(it) }
    }

    protected static String trim0(String value) {
        if( value == null )
            return null
        value = value.trim()
        while( value.startsWith("'") && value.endsWith("'") )
            value = value.substring(1, value.length() - 1)
        while( value.startsWith('"') && value.endsWith('"') )
            value = value.substring(1, value.length() - 1)
        return value
    }

    static String cranPackagesToDockerFile(String packages, List<String> repositories, CranOpts opts) {
        return cranPackagesTemplate0(
                '/templates/cran-installr-v1/dockerfile-cran-packages.txt',
                packages,
                repositories,
                opts)
    }

    static String cranPackagesToSingularityFile(String packages, List<String> repositories, CranOpts opts) {
        return cranPackagesTemplate0(
                '/templates/cran-installr-v1/singularityfile-cran-packages.txt',
                packages,
                repositories,
                opts)
    }

    protected static String cranPackagesTemplate0(String template, String packages, List<String> repositories, CranOpts opts) {
        final List<String> repos0 = repositories != null ? repositories : []
        final boolean singularity = template.contains('/singularityfile')
        final String image = opts.rImage
        final String target = formatPackageTarget(packages)
        final String basePackages = rInstallBasePackage0(opts.basePackages, singularity)
        final String repoOpts = buildRepositoryOptions(repos0, singularity)
        final Map<String, String> binding = [:]
        binding.put('base_image', image)
        binding.put('repo_opts', repoOpts)
        binding.put('target', target)
        String basePackagesStr = singularity ? '' : '\\'
        if( basePackages != null ) {
            if( singularity ) {
                basePackagesStr += '\n    ' + basePackages
            }
            else {
                basePackagesStr += '\n    && ' + basePackages + ' \\'
            }
        }
        binding.put('base_packages', basePackagesStr)

        final String result = renderTemplate0(template, binding)
        return addCommands(result, opts.commands, singularity)
    }

    static String cranFileToDockerFile(CranOpts opts) {
        return cranFileTemplate0('/templates/cran-installr-v1/dockerfile-cran-file.txt', opts)
    }

    static String cranFileToSingularityFile(CranOpts opts) {
        return cranFileTemplate0('/templates/cran-installr-v1/singularityfile-cran-file.txt', opts)
    }

    protected static String cranFileTemplate0(String template, CranOpts opts) {
        final boolean singularity = template.contains('/singularityfile')
        final String basePackages = rInstallBasePackage0(opts.basePackages, singularity)
        final Map<String, String> binding = [:]
        binding.put('base_image', opts.rImage)
        binding.put('base_packages', basePackages != null ? basePackages : '')

        final String result = renderTemplate0(template, binding, ['wave_context_dir'])
        return addCommands(result, opts.commands, singularity)
    }

    private static String buildRepositoryOptions(List<String> repositories, boolean singularity) {
        String repoSetup
        if( repositories.isEmpty() ) {
            // Default to CRAN if no repositories specified
            repoSetup = 'R -e "options(repos = c(CRAN = \'https://cloud.r-project.org/\'))"'
        }
        else {
            final String repoCommands = repositories.collect { repo ->
                if( 'bioconductor'.equalsIgnoreCase(repo) ) {
                    return "options(BioC_mirror = 'https://bioconductor.org')"
                }
                else if( 'cran'.equalsIgnoreCase(repo) ) {
                    return "options(repos = c(CRAN = 'https://cloud.r-project.org/'))"
                }
                else {
                    return "options(repos = c(CRAN = '${repo}'))"
                }
            }.join('; ')
            repoSetup = "R -e \"${repoCommands}\""
        }

        if( singularity ) {
            return repoSetup
        }
        else {
            // For dockerfile: add proper line continuation
            return repoSetup + ' '
        }
    }

    private static String formatPackageTarget(String packages) {
        if( packages.startsWith('http://') || packages.startsWith('https://') ) {
            return packages
        }

        final List<String> packageList = cranPackagesToList(packages)
        if( packageList == null || packageList.isEmpty() ) {
            return ''
        }

        return packageList.collect { pkg ->
            if( pkg.startsWith('bioc::') ) {
                return "BiocManager::install('${pkg.substring(6)}')"
            }
            else {
                return "'${pkg}'"
            }
        }.join(' ')
    }

    private static String renderTemplate0(String templatePath, Map<String, String> binding) {
        return renderTemplate0(templatePath, binding, [])
    }

    private static String renderTemplate0(String templatePath, Map<String, String> binding, List<String> ignore) {
        final URL template = CranHelper.class.getResource(templatePath)
        if( template == null )
            throw new IllegalStateException("Unable to load template '${templatePath}' from classpath")
        try {
            final InputStream reader = template.openStream()
            return new TemplateRenderer()
                    .withIgnore(ignore)
                    .render(reader, binding)
        }
        catch( IOException e ) {
            throw new IllegalStateException("Unable to read classpath template '${templatePath}'", e)
        }
    }

    private static String rInstallBasePackage0(String basePackages, boolean singularity) {
        if( StringUtils.isEmpty(basePackages) ) {
            return null
        }

        return "apt-get update && apt-get install -y ${basePackages}"
    }

    private static String addCommands(String result, List<String> commands, boolean singularity) {
        if( commands == null || commands.isEmpty() )
            return result
        if( singularity )
            result += '%post\n'
        for( String cmd : commands ) {
            if( singularity ) result += '    '
            result += cmd + '\n'
        }
        return result
    }
}

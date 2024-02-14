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

package io.seqera.wave.service.conda

import java.nio.file.Files
import java.nio.file.Path

import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.service.persistence.CondaPackageRecord
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class AbstractCondaFetcher implements CondaFetcherService{

    @Inject
    private BuildConfig buildConfig

    @Inject
    private PersistenceService persistenceService

    private List<String> channels = ['seqera']

    protected List<String> fetchCommand(String channel, String target) {
        List.of('bash','-c', "conda search -q -c $channel | head 10 > $target".toString())
    }

    @Override
    void fetchCondaPackages() {
        final workDir = Path.of(buildConfig.buildWorkspace).resolve('conda-' + System.currentTimeMillis())
        // create the work dir
        Files.createDirectory(workDir)

        for( String it : channels ) {
            processChannel(it, workDir)
        }

    }

    protected void processChannel(String channel, Path workDir) {
        final target = "ch-${channel}.txt"
        run(fetchCommand(channel, target), workDir)
        // read the result file and parse it
        processResult(workDir.resolve(target))
    }

    protected void processResult(Path file) {
        final reader = Files.newBufferedReader(file)
        String line
        boolean begin=false
        while( (line=reader.readLine()) ) {
            if( !begin ) {
                begin = line.startsWith("# Name")
                continue
            }
            line.tokenize(' ')
            final entry = new CondaPackageRecord(line[3], line[0], line[1])
            persistenceService.saveCondaPackage(entry)
        }
    }


    abstract protected boolean run(List<String> command, Path workDir)

}

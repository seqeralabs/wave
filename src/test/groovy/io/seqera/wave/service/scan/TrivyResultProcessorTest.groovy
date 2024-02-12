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

package io.seqera.wave.service.scan


import spock.lang.Specification

import io.seqera.wave.exception.ScanRuntimeException

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

class TrivyResultProcessorTest extends Specification {

    def "process should return a map of vulnerabilities"() {
        given:
        def trivyDockerResulJson = """
            {"Results": [
              {
                 "Target": "redis (debian 12.0)",
                 "Class": "os-pkgs",
                 "Type": "debian",
                 "Vulnerabilities": [
        
                    {
                       "VulnerabilityID": "CVE-2010-4756",
                       "PkgID": "libc-bin@2.36-9",
                       "PkgName": "libc-bin",
                       "InstalledVersion": "2.36-9",
                       "FixedVersion": "1.1.1n-0+deb11u5",
                       "Layer": {
                          "Digest": "sha256:faef57eae888cbe4a5613eca6741b5e48d768b83f6088858aee9a5a2834f8151",
                          "DiffID": "sha256:24839d45ca455f36659219281e0f2304520b92347eb536ad5cc7b4dbb8163588"
                       },
                       "SeveritySource": "debian",
                       "PrimaryURL": "https://avd.aquasec.com/nvd/cve-2010-4756",
                       "DataSource": {
                          "ID": "debian",
                          "Name": "Debian Security Tracker",
                          "URL": "https://salsa.debian.org/security-tracker-team/security-tracker"
                       },
                       "Title": "glibc: glob implementation can cause excessive CPU and memory consumption due to crafted glob expressions",
                       "Description": "The glob implementation in the GNU C Library (aka glibc or libc6) allows remote authenticated users to cause a denial of service (CPU and memory consumption) via crafted glob expressions that do not match any pathnames, as demonstrated by glob expressions in STAT commands to an FTP daemon, a different vulnerability than CVE-2010-2632.",
                       "Severity": "LOW",
                       "CweIDs": [
                          "CWE-399"
                       ],
                       "CVSS": {
                          "nvd": {
                             "V2Vector": "AV:N/AC:L/Au:S/C:N/I:N/A:P",
                             "V2Score": 4
                          },
                          "redhat": {
                             "V2Vector": "AV:N/AC:L/Au:N/C:N/I:N/A:P",
                             "V2Score": 5
                          }
                       },
                       "References": [
                          "http://cxib.net/stuff/glob-0day.c",
                          "http://securityreason.com/achievement_securityalert/89",
                          "http://securityreason.com/exploitalert/9223",
                          "https://access.redhat.com/security/cve/CVE-2010-4756",
                          "https://bugzilla.redhat.com/show_bug.cgi?id=681681",
                          "https://bugzilla.redhat.com/show_bug.cgi?id=CVE-2010-4756",
                          "https://nvd.nist.gov/vuln/detail/CVE-2010-4756",
                          "https://www.cve.org/CVERecord?id=CVE-2010-4756"
                       ],
                       "PublishedDate": "2011-03-02T20:00:00Z",
                       "LastModifiedDate": "2021-09-01T12:15:00Z"
                    }]}
                 ]
              }      
        """

        when:
        def result = TrivyResultProcessor.process(trivyDockerResulJson)

        then:
        def vulnerability = result[0]
        vulnerability.id == "CVE-2010-4756"
        vulnerability.severity == "LOW"
        vulnerability.title == "glibc: glob implementation can cause excessive CPU and memory consumption due to crafted glob expressions"
        vulnerability.pkgName == "libc-bin"
        vulnerability.installedVersion == "2.36-9"
        vulnerability.fixedVersion == "1.1.1n-0+deb11u5"
        vulnerability.primaryUrl == "https://avd.aquasec.com/nvd/cve-2010-4756"

    }

    def "process should throw exception if json is not correct"() {
        when:
        TrivyResultProcessor.process("invalid json")
        then:
        thrown ScanRuntimeException
    }
}

/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import net.swiftzer.semver.SemVer
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import java.io.OutputStream
import java.io.PrintStream


class Utils {
    companion object {

        // Find the version.txt in the root of the repository, independent of
        // which build is started from where.
        @JvmStatic
        fun Directory.versionTxt(): RegularFile =
            file("version.txt").let { if (it.asFile.exists()) it else this.dir("..").versionTxt() }

        @JvmStatic
        fun updateVersion(project: Project, newVersion: SemVer) {
            project.layout.projectDirectory.versionTxt().asFile.writeText(newVersion.toString())
        }

        @JvmStatic
        fun generateProjectVersionReport(rootProject: Project, ostream: OutputStream) {
            val writer = PrintStream(ostream, false, Charsets.UTF_8)
            val version = rootProject.layout.projectDirectory.versionTxt().asFile.readText()

            ostream.use {
                writer.use {
                    // Writer headers
                    writer.println("### Deployed Version Information")
                    writer.println()
                    writer.println("| Artifact Name | Version Number |")
                    writer.println("| --- | --- |")
                    // Write table rows
                    writer.printf("| %s | %s |\n", "hedera-node", version)
                    writer.printf("| %s | %s |\n", "platform-sdk", version)
                    writer.flush()
                    ostream.flush()
                }
            }
        }
    }
}

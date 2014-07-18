/*
 * Copyright 2014 the original author or authors.
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
 */
package org.kordamp.gradle.stats

import groovy.xml.MarkupBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * @author Andres Almiray
 */
class StatsTask extends DefaultTask {
    @Optional @Input Map<String, Class<? extends Counter>> counters = [:]
    @Optional @Input Map<String, Map<String, Object>> paths = [:]
    @Optional @Input List<String> formats = []
    @Optional @Input File reportDir

    int totalFiles = 0
    int totalLOC = 0

    private static final String XML = 'xml'
    private static final String HTML = 'html'
    private static final String TXT = 'txt'

    StatsTask() {
        reportDir = project.file("${project.buildDir}/reports/stats")
    }

    @TaskAction
    void computeLoc() {
        Map<String, Counter> counterInstances = resolveCounterInstances()
        Map<String, Map<String, Object>> work = [:]

        work.groovy = [name: 'Groovy Sources', path: 'src/main/groovy']
        work.java = [name: 'Java Sources', path: 'src/main/java']
        work.testGroovy = [name: 'Groovy Test Sources', path: 'src/test/groovy']
        work.testJava = [name: 'Java Test Sources', path: 'src/test/java']

        Map merged = [:]
        merged.putAll(work)

        if (!paths) {
            project.sourceSets.main.allSource.srcDirs.each { File dir ->
                if (!dir.exists()) return
                dir.eachFileRecurse { File file ->
                    if (file.file) {
                        if (file.name.endsWith('.groovy')) {
                            StatsTask.countLines(merged.groovy, counterInstances.groovy, file)
                        } else if (file.name.endsWith('.java')) {
                            StatsTask.countLines(merged.java, counterInstances.java, file)
                        }
                    }
                }
            }

            project.sourceSets.test.allSource.srcDirs.each { File dir ->
                if (!dir.exists()) return
                dir.eachFileRecurse { File file ->
                    if (file.file) {
                        if (file.name.endsWith('.groovy')) {
                            StatsTask.countLines(merged.testGroovy, counterInstances.groovy, file)
                        } else if (file.name.endsWith('.java')) {
                            StatsTask.countLines(merged.testJava, counterInstances.java, file)
                        }
                    }
                }
            }
        } else {
            merged.putAll(paths)
            merged.each { type, info ->
                File dir = project.file(info.path)
                if (!dir.exists()) return
                dir.eachFileRecurse { File file ->
                    if (file.file) {
                        if (file.name.endsWith('.groovy')) {
                            StatsTask.countLines(merged[type], counterInstances.groovy, file)
                        } else if (file.name.endsWith('.java')) {
                            StatsTask.countLines(merged[type], counterInstances.java, file)
                        }
                    }
                }
            }
        }

        merged.each { type, info ->
            if (info.files) {
                totalFiles += info.files
                totalLOC += info.lines
            }
        }

        if (totalFiles) {
            output(merged, totalFiles.toString(), totalLOC.toString(), new PrintWriter(System.out))
            if (XML in formats) xmlOutput(merged, totalFiles.toString(), totalLOC.toString())
            if (HTML in formats) htmlOutput(merged, totalFiles.toString(), totalLOC.toString())
            if (TXT in formats) output(merged, totalFiles.toString(), totalLOC.toString(), new PrintWriter(getOutputFile(TXT)))
        }
    }

    private
    static void countLines(Map<String, Object> work, Counter counter, File file) {
        int numFiles = work.get('files', 0)
        work.files = ++numFiles
        int lines = counter.count(file)
        int numLines = work.get('lines', 0)
        work.lines = numLines + lines
    }

    private Map<String, Counter> resolveCounterInstances() {
        Map<String, Counter> instances = counters.inject([:]) { map, entry ->
            map[entry.key] = entry.value.newInstance()
        }

        if (!instances.java) instances.java = new JavaCounter()
        if (!instances.groovy) instances.groovy = new JavaCounter()
        instances
    }

    private void output(Map<String, Map<String, Object>> work, String totalFiles, String totalLOC, Writer out) {
        out.println '''
            +----------------------+-------+-------+
            | Name                 | Files |  LOC  |
            +----------------------+-------+-------+'''.stripIndent(8)

        work.each { type, info ->
            if (info.files) {
                out.println '    | ' +
                    info.name.padRight(20, ' ') + ' | ' +
                    info.files.toString().padLeft(5, ' ') + ' | ' +
                    info.lines.toString().padLeft(5, ' ') + ' | '
            }
        }

        out.println '    +----------------------+-------+-------+'
        out.println '    | Totals               | ' + totalFiles.padLeft(5, ' ') + ' | ' + totalLOC.padLeft(5, ' ') + ' | '
        out.println '    +----------------------+-------+-------+\n'
        out.flush()
    }

    private void xmlOutput(Map<String, Map<String, Object>> work, String totalFiles, String totalLOC) {
        new MarkupBuilder(new FileWriter(getOutputFile(XML))).stats {
            work.each { type, info ->
                if (info.files) {
                    category(name: info.name) {
                        fileCount(info.files.toString())
                        loc(info.lines.toString())
                    }
                }
            }
            category {
                name('Total')
                fileCount(totalFiles)
                loc(totalLOC)
            }
        }
    }

    private void htmlOutput(Map<String, Map<String, Object>> work, String totalFiles, String totalLOC) {
        int i = 0
        new MarkupBuilder(new FileWriter(getOutputFile(HTML))).html {
            table(border: 1) {
                tr {
                    th('Name')
                    th('Files')
                    th('LOC')
                }
                work.each { type, info ->
                    if (info.files) {
                        tr(style: (i++) % 2 ? 'background-color:lightblue' : 'background-color:FFF') {
                            td(info.name)
                            td(info.files.toString())
                            td(info.lines.toString())
                        }
                    }
                }
                tr(style: 'background-color:lightgreen') {
                    b {
                        td('Total')
                        td(totalFiles)
                        td(totalLOC)
                    }
                }
            }
        }
    }

    private getOutputFile(String suffix) {
        reportDir.mkdirs()
        new File(reportDir, project.name + '.' + suffix)
    }
}

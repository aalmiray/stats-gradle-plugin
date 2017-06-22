/*
 * Copyright 2014-2016 the original author or authors.
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
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * @author Andres Almiray
 */
class AggregateStatsReportTask extends DefaultTask {
    @Optional @Input Collection<String> projects = []
    @Optional @Input File reportDir

    File xmlReport

    private static final String XML = 'xml'
    private static final String HTML = 'html'
    private static final String TXT = 'txt'

    AggregateStatsReportTask() {
        reportDir = project.file("${project.buildDir}/reports/stats")
        xmlReport = project.file("${reportDir}/${project.name}-all.xml")
        projects = project.subprojects.name
    }

    @TaskAction
    void computeAggregate() {

        Map<String, Map<String, Integer>> stats = [:]

        projects.each { name ->
            Project p = project.project(name)
            StatsTask t = p.tasks.findByName('stats')
            if (!t || !t.xmlReport.exists()) return
            def xml = new XmlSlurper().parse(t.xmlReport)
            xml.category.each { category ->
                String n = category.@name.text() ? category.@name.text() : 'Totals'
                Map<String, Integer> map = stats.get(n, [fileCount: 0, locCount: 0])
                map.fileCount += category.fileCount.text().toInteger()
                map.locCount += category.loc.text().toInteger()
            }
        }

        Map totals = stats.remove('Totals')
        stats = stats.sort { it.key }

        if (stats) {
            int max = 0
            stats.keySet().each { max = Math.max(max, it.size()) }
            max = Math.max(max, 22)

            output(stats, max, totals.fileCount.toString(), totals.locCount.toString(), new PrintWriter(System.out))
            xmlOutput(stats, totals.fileCount.toString(), totals.locCount.toString())
        }
    }

    private void output(Map<String, Map<String, Object>> stats, int max, String totalFiles, String totalLOC, Writer out) {
        out.println '    +-' + ('-' * max) + '-+---------+---------+'
        out.println '    | ' + 'Name'.padRight(max, ' ') + ' |   Files |     LOC |'
        out.println '    +-' + ('-' * max) + '-+---------+---------+'

        stats.each { category, info ->
            out.println '    | ' +
                category.padRight(max, ' ') + ' | ' +
                info.fileCount.toString().padLeft(7, ' ') + ' | ' +
                info.locCount.toString().padLeft(7, ' ') + ' |'
        }

        out.println '    +-' + ('-' * max) + '-+---------+---------+'
        out.println '    | ' + 'Totals'.padRight(max, ' ') + ' | ' + totalFiles.padLeft(7, ' ') + ' | ' + totalLOC.padLeft(7, ' ') + ' |'
        out.println '    +-' + ('-' * max) + '-+---------+---------+\n'

        out.flush()
    }

    private void xmlOutput(Map<String, Map<String, Object>> stats, String totalFiles, String totalLOC) {
        new MarkupBuilder(new FileWriter(getOutputFile(XML))).stats {
            stats.each { c, info ->
                category(name: c) {
                    fileCount(info.fileCount.toString())
                    loc(info.locCount.toString())
                }
            }
            category {
                name('Total')
                fileCount(totalFiles)
                loc(totalLOC)
            }
        }
    }

    private getOutputFile(String suffix) {
        reportDir.mkdirs()
        new File(reportDir, project.name + '-all.' + suffix)
    }
}

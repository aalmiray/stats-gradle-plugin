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
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction

/**
 * @author Andres Almiray
 */
class StatsTask extends DefaultTask {
    @Optional @Input Map<String, String> counters = [:]
    @Optional @Input Map<String, Map<String, String>> paths = [:]
    @Optional @Input List<String> formats = []
    @Optional @Input File reportDir

    File xmlReport

    int totalFiles = 0
    int totalLOC = 0

    private static final String XML = 'xml'
    private static final String HTML = 'html'
    private static final String TXT = 'txt'

    StatsTask() {
        reportDir = project.file("${project.buildDir}/reports/stats")
        xmlReport = project.file("${reportDir}/${project.name}.xml")
    }

    @TaskAction
    void computeLoc() {
        Map<String, Counter> counterInstances = resolveCounterInstances()
        Map<String, Map<String, String>> basePaths = [:]

        [
            java      : 'Java',
            groovy    : 'Groovy',
            scala     : 'Scala',
            kt        : 'Kotlin',
            js        : 'Javascript',
            css       : 'CSS',
            scss      : 'SASS',
            xml       : 'XML',
            html      : 'HTML',
            fxml      : 'FXML',
            properties: 'Properties',
            sql       : 'SQL'
        ].each { extension, name ->
            ['test', 'integration-test', 'functional-test'].each { source ->
                String classifier = StatsTask.getPropertyNameForLowerCaseHyphenSeparatedName(source)
                basePaths[classifier + extension.capitalize()] = [name: name + ' ' + StatsTask.getNaturalName(classifier) + ' Sources', path: 'src/' + source, extension: extension]
            }
        }

        Map merged = [:]
        merged.putAll(basePaths)
        merged.putAll(paths)

        merged.java = [name: 'Java Sources', path: '.*', extension: 'java']
        merged.groovy = [name: 'Groovy Sources', path: '.*', extension: 'groovy']
        merged.scala = [name: 'Scala Sources', path: '.*', extension: 'scala']
        merged.kt = [name: 'Kotlin Sources', path: '.*', extension: 'kt']
        merged.js = [name: 'Javascript Sources', path: '.*', extension: 'js']
        merged.css = [name: 'CSS Sources', path: '.*', extension: 'css']
        merged.scss = [name: 'SASS Sources', path: '.*', extension: 'scss']
        merged.xml = [name: 'XML Sources', path: '.*', extension: 'xml']
        merged.html = [name: 'HTML Sources', path: '.*', extension: 'html']
        merged.fxml = [name: 'FXML Sources', path: '.*', extension: 'fxml']
        merged.properties = [name: 'Properties', path: '.*', extension: 'properties']
        merged.sql = [name: 'SQL', path: '.*', extension: 'sql']

        resolveSourceSets().each { sourceSet ->
            sourceSet.allSource.srcDirs.each { File dir ->
                if (!dir.exists()) return
                dir.eachFileRecurse { File file ->
                    if (file.file) {
                        String extension = StatsTask.getFilenameExtension(file.name)
                        Map map = merged.find { file.absolutePath =~ it.value.path && !it.value.extension }?.value
                        if (!map) map = merged.find {
                            file.absolutePath =~ it.value.path && extension == it.value.extension
                        }?.value
                        if (!map) map = merged.find { file.absolutePath =~ it.value.path }?.value
                        if (!map || (map.extension && extension != map.extension)) return
                        if (counterInstances.containsKey(extension)) {
                            StatsTask.countLines(map, counterInstances[extension], file)
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
            int max = 0
            merged.values().each { if (it.files) max = Math.max(max, it.name.size()) }
            max = Math.max(max, 22)
            merged = merged.sort { it.value.name }

            output(merged, max, totalFiles.toString(), totalLOC.toString(), new PrintWriter(System.out))
            xmlOutput(merged, totalFiles.toString(), totalLOC.toString())
            if (HTML in formats) htmlOutput(merged, totalFiles.toString(), totalLOC.toString())
            if (TXT in formats) output(merged, max, totalFiles.toString(), totalLOC.toString(), new PrintWriter(getOutputFile(TXT)))
        }
    }

    private resolveSourceSets() {
        if (project.plugins.hasPlugin('com.android.library')) {
            project.android.sourceSets
        } else {
            project.sourceSets
        }
    }

    private static void countLines(Map<String, Object> work, Counter counter, File file) {
        int numFiles = work.get('files', 0)
        work.files = ++numFiles
        int lines = counter.count(file)
        int numLines = work.get('lines', 0)
        work.lines = numLines + lines
    }

    private Map<String, Counter> resolveCounterInstances() {
        Map<String, Counter> instances = [:]
        counters.collect { key, classname ->
            instances[key] = Class.forName(classname).newInstance()
        }

        if (!instances.java) instances.java = new JavaCounter()
        if (!instances.groovy) instances.groovy = new JavaCounter()
        if (!instances.js) instances.js = new JavaCounter()
        if (!instances.scala) instances.scala = new JavaCounter()
        if (!instances.kt) instances.kt = new JavaCounter()
        if (!instances.css) instances.css = new CssCounter()
        if (!instances.scss) instances.scss = new JavaCounter()
        if (!instances.xml) instances.xml = new XmlCounter()
        if (!instances.html) instances.html = new XmlCounter()
        if (!instances.fxml) instances.fxml = new XmlCounter()
        if (!instances.properties) instances.properties = new PropertiesCounter()
        if (!instances.sql) instances.sql = new SqlCounter()

        instances
    }

    private void output(Map<String, Map<String, Object>> work, int max, String totalFiles, String totalLOC, Writer out) {
        out.println '    +-' + ('-' * max) + '-+--------+--------+'
        out.println '    | ' + 'Name'.padRight(max, ' ') + ' |  Files |    LOC |'
        out.println '    +-' + ('-' * max) + '-+--------+--------+'

        work.each { type, info ->
            if (info.files) {
                out.println '    | ' +
                    info.name.padRight(max, ' ') + ' | ' +
                    info.files.toString().padLeft(6, ' ') + ' | ' +
                    info.lines.toString().padLeft(6, ' ') + ' |'
            }
        }

        out.println '    +-' + ('-' * max) + '-+--------+--------+'
        out.println '    | ' + 'Totals'.padRight(max, ' ') + ' | ' + totalFiles.padLeft(6, ' ') + ' | ' + totalLOC.padLeft(6, ' ') + ' |'
        out.println '    +-' + ('-' * max) + '-+--------+--------+\n'

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

    private static String getFilenameExtension(String path) {
        if (path == null) {
            return null;
        }
        int extIndex = path.lastIndexOf(".");
        if (extIndex == -1) {
            return null;
        }
        int folderIndex = path.lastIndexOf("/");
        if (folderIndex > extIndex) {
            return null;
        }
        return path.substring(extIndex + 1);
    }

    private static String getNaturalName(String name) {
        name = getShortName(name);
        if (isBlank(name)) return name;
        List<String> words = new ArrayList<>();
        int i = 0;
        char[] chars = name.toCharArray();
        for (char c : chars) {
            String w;
            if (i >= words.size()) {
                w = "";
                words.add(i, w);
            } else {
                w = words.get(i);
            }

            if (Character.isLowerCase(c) || Character.isDigit(c)) {
                if (Character.isLowerCase(c) && w.length() == 0) {
                    c = Character.toUpperCase(c);
                } else if (w.length() > 1 && Character.isUpperCase(w.charAt(w.length() - 1))) {
                    w = "";
                    words.add(++i, w);
                }

                words.set(i, w + c);
            } else if (Character.isUpperCase(c)) {
                if ((i == 0 && w.length() == 0) || Character.isUpperCase(w.charAt(w.length() - 1))) {
                    words.set(i, w + c);
                } else {
                    words.add(++i, String.valueOf(c));
                }
            }
        }

        StringBuilder buf = new StringBuilder();
        for (Iterator<String> j = words.iterator(); j.hasNext();) {
            String word = j.next();
            buf.append(word);
            if (j.hasNext()) {
                buf.append(' ');
            }
        }
        return buf.toString();
    }

    private static String getShortName(String className) {
        if (isBlank(className)) return className;
        int i = className.lastIndexOf(".");
        if (i > -1) {
            className = className.substring(i + 1, className.length());
        }
        return className;
    }

    private static boolean isBlank(String str) {
        if (str == null || str.length() == 0) {
            return true;
        }
        for (char c : str.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                return false;
            }
        }

        return true;
    }

    private static String getPropertyNameForLowerCaseHyphenSeparatedName(String name) {
        return getPropertyName(getClassNameForLowerCaseHyphenSeparatedName(name));
    }

    private static String getClassNameForLowerCaseHyphenSeparatedName(String name) {
        // Handle null and empty strings.
        if (isBlank(name)) return name;

        if (name.indexOf('-') > -1) {
            StringBuilder buf = new StringBuilder();
            String[] tokens = name.split("-");
            for (String token : tokens) {
                if (token == null || token.length() == 0) continue;
                buf.append(capitalize(token));
            }
            return buf.toString();
        }

        return capitalize(name);
    }

    private static String capitalize(String str) {
        if (isBlank(str)) return str;
        if (str.length() == 1) return str.toUpperCase();
        return str.substring(0, 1).toUpperCase(Locale.ENGLISH) + str.substring(1);
    }

    private static String getPropertyName(String name) {
        if (isBlank(name)) return name;
        // Strip any package from the name.
        int pos = name.lastIndexOf('.');
        if (pos != -1) {
            name = name.substring(pos + 1);
        }

        // Check whether the name begins with two upper case letters.
        if (name.length() > 1 && Character.isUpperCase(name.charAt(0)) && Character.isUpperCase(name.charAt(1))) {
            return name;
        }

        String propertyName = name.substring(0, 1).toLowerCase(Locale.ENGLISH) + name.substring(1);
        if (propertyName.indexOf(' ') > -1) {
            propertyName = propertyName.replaceAll("\\s", "");
        }
        return propertyName;
    }
}

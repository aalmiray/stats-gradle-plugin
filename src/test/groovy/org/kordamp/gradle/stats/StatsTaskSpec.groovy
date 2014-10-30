package org.kordamp.gradle.stats

import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * @author Andres Almiray
 */
@SuppressWarnings('MethodName')
class StatsTaskSpec extends Specification {
    private static final String STATS = 'stats'

    Project project
    File testRootDir

    void "Calculate stats on basic Java project"() {
        given:
        testRootDir = new File('src/test/projects/basic_java')
        project = ProjectBuilder.builder().withName('test')
            .withProjectDir(testRootDir).build()
        project.apply(plugin: JavaPlugin)
        project.apply(plugin: StatsPlugin)
        StatsTask task = project.tasks.findByName(STATS)

        when:
        task.computeLoc()

        then:
        2 == task.totalFiles
        13 == task.totalLOC
    }

    void "Calculate stats on basic Griffon project"() {
        given:
        testRootDir = new File('src/test/projects/basic_griffon')
        project = ProjectBuilder.builder().withName('test')
            .withProjectDir(testRootDir).build()
        project.apply(plugin: GroovyPlugin)
        project.apply(plugin: StatsPlugin)
        project.sourceSets.main.groovy.srcDirs = [
            'griffon-app/conf',
            'griffon-app/controllers',
            'griffon-app/models',
            'griffon-app/views',
            'griffon-app/services',
            'griffon-app/lifecycle',
            'src/main/groovy'
        ]
        project.sourceSets.main.resources.srcDirs = [
            'griffon-app/resources',
            'griffon-app/i18n',
            'src/main/resources'
        ]

        StatsTask task = project.tasks.findByName(STATS)
        task.paths = [
            model     : [name: 'Models', path: 'griffon-app/models'],
            view      : [name: 'Views', path: 'griffon-app/views'],
            controller: [name: 'Controllers', path: 'griffon-app/controllers'],
            service   : [name: 'Services', path: 'griffon-app/services'],
            config    : [name: 'Config', path: 'griffon-app/conf'],
            lifecycle : [name: 'Lifecycle', path: 'griffon-app/lifecycle']
        ]

        when:
        task.computeLoc()

        then:
        12 == task.totalFiles
        137 == task.totalLOC
    }
}

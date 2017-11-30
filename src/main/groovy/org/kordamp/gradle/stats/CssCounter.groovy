/*
 * Copyright 2014-2017 the original author or authors.
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

/**
 * @author Andres Almiray
 */
class CssCounter implements Counter {
    @Override
    int count(File file) {
        def loc = 0
        def comment = 0
        file.eachLine { line ->
            if (!line.trim().length() || line ==~ EMPTY) return

            def m = line =~ SLASH_STAR_STAR_SLASH
            if (m.count && m[0][1] ==~ EMPTY && m[0][3] ==~ EMPTY) return
            int open = line.indexOf('/*')
            int close = line.indexOf('*/')
            if (open != -1 && (close - open) <= 1) comment++
            else if (close != -1 && comment) {
                comment--
                if (!comment) return
            }

            if (!comment) loc++
        }

        loc
    }
}

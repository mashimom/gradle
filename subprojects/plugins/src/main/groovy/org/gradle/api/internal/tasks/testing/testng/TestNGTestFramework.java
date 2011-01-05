/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.testng;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.api.internal.tasks.testing.TestClassProcessor;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.WorkerTestClassProcessorFactory;
import org.gradle.api.internal.tasks.testing.junit.JULRedirector;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.testing.testng.TestNGOptions;
import org.gradle.process.internal.WorkerProcessBuilder;
import org.gradle.util.IdGenerator;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * @author Tom Eyckmans
 */
public class TestNGTestFramework implements TestFramework {

    private TestNGOptions options;
    private TestNGDetector detector;
    private final Test testTask;

    public TestNGTestFramework(Test testTask) {
        this.testTask = testTask;
        options = new TestNGOptions(testTask.getProject().getProjectDir());
        options.setAnnotationsOnSourceCompatibility(JavaVersion.toVersion(testTask.getProject().property("sourceCompatibility")));
        detector = new TestNGDetector(testTask.getTestClassesDir(), testTask.getClasspath());
    }

    public WorkerTestClassProcessorFactory getProcessorFactory() {
        options.setTestResources(testTask.getTestSrcDirs());
        List<File> suiteFiles = options.getSuites(testTask.getTemporaryDir());
        return new TestClassProcessorFactoryImpl(testTask.getTestReportDir(), options, suiteFiles);
    }

    public Action<WorkerProcessBuilder> getWorkerConfigurationAction() {
        return new Action<WorkerProcessBuilder>() {
            public void execute(WorkerProcessBuilder workerProcessBuilder) {
                workerProcessBuilder.sharedPackages("org.testng");
            }
        };
    }

    public void report() {
        // TODO currently reports are always generated because the antTestNGExecute task uses the
        // default listeners and these generate reports by default.
    }

    public TestNGOptions getOptions() {
        return options;
    }

    void setOptions(TestNGOptions options) {
        this.options = options;
    }

    public TestNGDetector getDetector() {
        return detector;
    }

    private static class TestClassProcessorFactoryImpl implements WorkerTestClassProcessorFactory, Serializable {
        private final File testReportDir;
        private final TestNGOptions options;
        private final List<File> suiteFiles;

        public TestClassProcessorFactoryImpl(File testReportDir, TestNGOptions options, List<File> suiteFiles) {
            this.testReportDir = testReportDir;
            this.options = options;
            this.suiteFiles = suiteFiles;
        }

        public TestClassProcessor create(ServiceRegistry serviceRegistry) {
            return new TestNGTestClassProcessor(testReportDir, options, suiteFiles, serviceRegistry.get(IdGenerator.class), new JULRedirector());
        }
    }
}

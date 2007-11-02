/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.apache.tools.ant.taskdefs.optional.testing;

import org.apache.tools.ant.Task;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.TaskAdapter;
import org.apache.tools.ant.util.WorkerAnt;
import org.apache.tools.ant.taskdefs.condition.Condition;
import org.apache.tools.ant.taskdefs.Parallel;
import org.apache.tools.ant.taskdefs.Sequential;
import org.apache.tools.ant.taskdefs.WaitFor;

/**
 * Task to provide functional testing under Ant, with a fairly complex worflow of:
 *
 * <ul>
 * <li>Conditional execution</li>
 * <li>Application to start</li>
 * <li>A probe to "waitfor" before running tests</li>
 * <li>A tests sequence</li>
 * <li>A reporting sequence that runs after the tests have finished</li>
 * <li>A "teardown" clause that runs after the rest.</li>
 * <li>Automated termination of the program it executes, if a timeout is not met</li>
 * <li>Checking of a failure property and automatic raising of a fault
 *     (with the text in failureText)
 * if test shutdown and reporting succeeded</li>
 *  </ul>
 *
 * The task is designed to be framework neutral; it will work with JUnit,
 *  TestNG and other test frameworks That can be
 * executed from Ant. It bears a resemblance to the FunctionalTest task from
 * SmartFrog, as the attribute names were
 * chosen to make migration easier. However, this task benefits from the
 * ability to tweak Ant's internals, and so
 * simplify the workflow, and from the experience of using the SmartFrog task.
 * No code has been shared.
 *
 * @since Ant 1.8
 */

public class Funtest extends Task {

    /**
     * A condition that must be true before the tests are run. This makes it
     * easier to define complex tests that only
     * run if certain conditions are met, such as OS or network state.
     */
    private Condition condition;


    /**
     * Used internally to set the workflow up
     */
    private Parallel timedTests;

    /**
     * Setup runs if the condition is met. Once setup is complete, teardown
     * will be run when the task finishes
     */
    private Sequential setup;

    /**
     * The application to run
     */
    private Sequential application;

    /**
     * A block that halts the tests until met.
     */
    private BlockFor block;

    /**
     * Tests to run
     */
    private Sequential tests;

    /**
     * Reporting only runs if the tests were executed. If the block stopped
     * them, reporting is skipped.
     */
    private Sequential reporting;

    /**
     * Any teardown operations.
     */
    private Sequential teardown;

    /**
     * time for the tests to time out
     */
    private long timeout;

    private long timeoutUnitMultiplier = WaitFor.ONE_MILLISECOND;

    /**
     * time for the execution to time out.
     */
    private long shutdownTime = 10 * WaitFor.ONE_SECOND;

    private long shutdownUnitMultiplier = WaitFor.ONE_MILLISECOND;

    /**
     * Name of a property to look for
     */
    private String failureProperty;

    /**
     * Message to send when tests failed
     */
    private String failureMessage = "Tests failed";

    /**
     * Flag to set to true if you don't care about any shutdown errors.
     * <p/>
     * In that situation, errors raised during teardown are logged but not
     * turned into BuildFault events. Similar to catching and ignoring
     * <code>finally {}</code> clauses in Java/
     */
    private boolean failOnTeardownErrors = true;


    /**
     * What was thrown in the test run (including reporting)
     */
    private BuildException testException;
    /**
     * What got thrown during teardown
     */
    private BuildException teardownException;

    /**
     * Did the application throw an exception
     */
    private BuildException applicationException;

    /**
     * Did the task throw an exception
     */
    private BuildException taskException;

    /** {@value} */
    public static final String WARN_OVERRIDING = "Overriding previous definition of ";
    /** {@value} */
    public static final String APPLICATION_FORCIBLY_SHUT_DOWN = "Application forcibly shut down";
    /** {@value} */
    public static final String SHUTDOWN_INTERRUPTED = "Shutdown interrupted";
    /** {@value} */
    public static final String SKIPPING_TESTS
        = "Condition failed -skipping tests";

    /**
     * Log if the definition is overriding something
     *
     * @param name       what is being defined
     * @param definition what should be null if you don't want a warning
     */
    private void logOverride(String name, Object definition) {
        if (definition != null) {
            log(WARN_OVERRIDING + '<' + name + '>', Project.MSG_WARN);
        }
    }

    public void addCondition(Condition newCondition) {
        logOverride("condition", condition);
        condition = newCondition;
    }

    public void addApplication(Sequential sequence) {
        logOverride("application", application);
        application = sequence;
    }

    public void addSetup(Sequential sequence) {
        logOverride("setup", setup);
        setup = sequence;
    }

    public void addBlock(BlockFor sequence) {
        logOverride("block", block);
        block = sequence;
    }

    public void addTests(Sequential sequence) {
        logOverride("tests", tests);
        tests = sequence;
    }

    public void addReporting(Sequential sequence) {
        logOverride("reporting", reporting);
        reporting = sequence;
    }

    public void addTeardown(Sequential sequence) {
        logOverride("teardown", teardown);
        teardown = sequence;
    }


    public void setFailOnTeardownErrors(boolean failOnTeardownErrors) {
        this.failOnTeardownErrors = failOnTeardownErrors;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public void setFailureProperty(String failureProperty) {
        this.failureProperty = failureProperty;
    }

    public void setShutdownTime(long shutdownTime) {
        this.shutdownTime = shutdownTime;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void setTimeoutUnit(WaitFor.Unit unit) {
        timeoutUnitMultiplier = unit.getMultiplier();
    }

    public void setShutdownUnit(WaitFor.Unit unit) {
        shutdownUnitMultiplier = unit.getMultiplier();
    }


    public BuildException getApplicationException() {
        return applicationException;
    }

    public BuildException getTeardownException() {
        return teardownException;
    }

    public BuildException getTestException() {
        return testException;
    }

    public BuildException getTaskException() {
        return taskException;
    }

    /**
     * Bind and initialise a task
     * @param task task to bind
     */
    private void bind(Task task) {
        task.bindToOwner(this);
        task.init();
    }

    /**
     * Create a newly bound parallel instance
     * @param parallelTimeout timeout
     * @return a bound and initialised parallel instance.
     */
    private Parallel newParallel(long parallelTimeout) {
        Parallel par = new Parallel();
        bind(par);
        par.setFailOnAny(true);
        par.setTimeout(parallelTimeout);
        return par;
    }

    /**
     * Create a newly bound parallel instance with one child
     * @param parallelTimeout timeout
     * @return a bound and initialised parallel instance.
     */
    private Parallel newParallel(long parallelTimeout, Task child) {
        Parallel par = newParallel(parallelTimeout);
        par.addTask(child);
        return par;
    }

    /**
     * Run the functional test sequence.
     * <p/>
     * This is a fairly complex workflow -what is going on is that we try to clean up
     * no matter how the run ended, and to retain the innermost exception that got thrown
     * during cleanup. That is, if teardown fails after the tests themselves failed, it is the
     * test failing that is more important.
     * @throws BuildException if something was caught during the run or teardown.
     */
    public void execute() throws BuildException {

        //before anything else, check the condition
        //and bail out if it is defined but not true
        if (condition != null && !condition.eval()) {
            //we are skipping the test
            log(SKIPPING_TESTS);
            return;
        }

        long timeoutMillis = timeout * timeoutUnitMultiplier;

        //set up the application to run in a separate thread
        Parallel applicationRun = newParallel(timeoutMillis);
        //with a worker which we can use to manage it
        WorkerAnt worker = new WorkerAnt(applicationRun, null);
        if (application != null) {
            applicationRun.addTask(application);
        }

        //The test run consists of the block followed by the tests.
        long testRunTimeout = 0;
        Sequential testRun = new Sequential();
        bind(testRun);
        if (block != null) {
            //waitfor is not a task, it needs to be adapted
            testRun.addTask(new TaskAdapter(block));
            //add the block time to the total test run timeout
            testRunTimeout = block.calculateMaxWaitMillis();
        }

        //add the tests and more delay
        if (tests != null) {
            testRun.addTask(tests);
            testRunTimeout += timeoutMillis;
        }
        //add the reporting and more delay
        if (reporting != null) {
            testRun.addTask(reporting);
            testRunTimeout += timeoutMillis;
        }

        //wrap this in a parallel purely to set up timeouts for the
        //test run
        timedTests = newParallel(testRunTimeout, testRun);

        try {
            //run any setup task
            if (setup != null) {
                Parallel setupRun = newParallel(timeoutMillis, setup);
                setupRun.execute();
            }
            //start the worker thread and leave it running
            worker.start();
            //start the probe+test sequence
            timedTests.execute();
        } catch (BuildException e) {
            //Record the exception and continue
            testException = e;
        } finally {
            //teardown always runs; its faults are filed away
            if (teardown != null) {
                try {
                    Parallel teardownRun = newParallel(timeoutMillis, teardown);
                    teardownRun.execute();
                } catch (BuildException e) {
                    teardownException = e;
                }
            }
        }

        //we get here whether or not the tests/teardown have thrown a BuildException.
        //do a forced shutdown of the running application, before processing the faults

        try {
            //wait for the worker to have finished
            long shutdownTimeMillis = shutdownTime * shutdownUnitMultiplier;
            worker.waitUntilFinished(shutdownTimeMillis);
            if (worker.isAlive()) {
                //then, if it is still running, interrupt it a second time.
                log(APPLICATION_FORCIBLY_SHUT_DOWN, Project.MSG_WARN);
                worker.interrupt();
                worker.waitUntilFinished(shutdownTimeMillis);
            }
        } catch (InterruptedException e) {
            //success, something interrupted the shutdown. There may be a leaked
            //worker;
            log(SHUTDOWN_INTERRUPTED, e, Project.MSG_VERBOSE);
        }
        applicationException = worker.getBuildException();

        /**Now faults are analysed
            the priority is
          -testexceptions, except those indicating a build timeout when the application itself
            failed.
            (because often it is the application fault that is more interesting than the probe
             failure, which is usually triggered by the application not starting
          -application exceptions (above test timeout exceptions)
          -teardown exceptions -except when they are being ignored
          -any
        */

        processExceptions();
    }

    /**
     * Now faults are analysed.
     * <p> The priority is
     * <ol>
     * <li>testexceptions, except those indicating a build timeout when the application itself
     failed.<br>
     (because often it is the application fault that is more interesting than the probe
     failure, which is usually triggered by the application not starting
     </li><li>
     Application exceptions (above test timeout exceptions)
     </li><li>
     Teardown exceptions -except when they are being ignored
     </li><li>
     Test failures as indicated by the failure property
     </li></ol>

     */
    protected void processExceptions() {
        taskException = testException;

        //look for an application fault
        if (applicationException != null) {
            if (taskException == null || taskException instanceof BuildTimeoutException) {
                taskException = applicationException;
            } else {
                log("Application Exception:" + applicationException.toString(),
                        applicationException,
                        Project.MSG_WARN);
            }
        }

        //now look for teardown faults, which may be ignored
        if (teardownException != null) {
            if (taskException == null && failOnTeardownErrors) {
                taskException = teardownException;
            } else {
                //don't let the cleanup exception get in the way of any other failure
                log("teardown exception" + teardownException.toString(),
                        teardownException,
                        Project.MSG_WARN);
            }
        }

        //now, analyse the tests
        if (failureProperty != null
             && getProject().getProperty(failureProperty) != null) {
            //we've failed
            log(failureMessage);
            if (taskException == null) {
                taskException = new BuildException(failureMessage);
            }
        }

        //at this point taskException is null or not.
        //if not, throw the exception
        if (taskException != null) {
            throw taskException;
        }
    }
}
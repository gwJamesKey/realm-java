/*
 * Copyright 2015 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.rule;

import android.os.Handler;
import android.os.Looper;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import io.realm.TestHelper;

import static org.junit.Assert.fail;


/**
 * Rule that runs the test inside a worker looper thread. This rule is responsible
 * of creating a temp directory containing a Realm instance then delete it, once the test finishes.
 * <p>
 * All Realms used in a method method annotated with {@code @RunTestInLooperThread } should use
 * {@link RunInLooperThread#createConfiguration()} and friends to create their configurations. Failing to do so can
 * result in the test failing because the Realm could not be deleted (Reason is that {@link TestRealmConfigurationFactory}
 * and this class does not agree in which order to delete all open Realms.
 */
public class RunInLooperThread extends TestRealmConfigurationFactory {
    private final Object lock = new Object();

    // Access synchronized on 'lock'
    private RealmConfiguration realmConfiguration;

    // Default Realm created by this Rule. It is guaranteed to be closed when the test finishes.
    // Access synchronized on 'lock'
    private Realm realm;

    // Access synchronized on 'lock'
    private Handler backgroundHandler;

    // the variables created inside the test are local and eligible for GC.
    // but sometimes we need the variables to survive across different Looper
    // events (Callbacks happening in the future), so we add a strong reference
    // to them for the duration of the test.
    private LinkedList<Object> keepStrongReference;

    // Custom Realm used by the test. Saving the reference here will guarantee the instance is closed when exiting the test.
    private final List<Realm> testRealms = new ArrayList<Realm>();

    private CountDownLatch signalTestCompleted;


    public RealmConfiguration getConfiguration() {
        synchronized (lock) {
            return realmConfiguration;
        }
    }

    public Realm getRealm() {
        synchronized (lock) {
            return realm;
        }
    }

    private void initRealm() {
        synchronized (lock) {
            realm = Realm.getInstance(realmConfiguration);
        }
    }

    private void closeRealms() {
        Realm r = getRealm();
        if (r != null) {
            r.close();
        }

        for (Realm testRealm : testRealms) {
            testRealm.close();
        }
    }

    public void keepStrongReference(Object obj) {
        keepStrongReference.add(obj);
    }

    public void addTestRealm(Realm realm) {
        testRealms.add(realm);
    }

    Handler getBackgroundHandler() {
        synchronized (lock) {
            return this.backgroundHandler;
        }
    }

    void setBackgroundHandler(Handler backgroundHandler) {
        synchronized (lock) {
            this.backgroundHandler = backgroundHandler;
        }
    }

    /**
     * Signal that the test has completed.
     */
    public void testComplete() {
        signalTestCompleted.countDown();
    }

    /**
     * Signal that the test has completed.
     *
     * @param latches additional latches to wait before set the test completed flag.
     */
    public void testComplete(CountDownLatch... latches) {
        for (CountDownLatch latch : latches) {
            TestHelper.awaitOrFail(latch);
        }
        testComplete();
    }

    @Override
    protected void before() throws Throwable {
        super.before();

        signalTestCompleted = new CountDownLatch(1);

        RealmConfiguration config = createConfiguration(UUID.randomUUID().toString());
        keepStrongReference = new LinkedList<>();

        synchronized (lock) {
            realmConfiguration = config;
        }
    }

    @Override
    protected void after() {
        super.after();

        testRealms.clear();
        keepStrongReference = null;

        synchronized (lock) {
            realm = null;
            realmConfiguration = null;
        }
    }

    @Override
    public Statement apply(Statement base, Description description) {
        final RunTestInLooperThread annotation = description.getAnnotation(RunTestInLooperThread.class);
        if (annotation == null) {
            return base;
        }
        return new RunInLooperThreadStatement(annotation, base);
    }

    /**
     * Posts a runnable to the currently running looper.
     */
    public void postRunnable(Runnable runnable) {
        getBackgroundHandler().post(runnable);
    }

    /**
     * Posts a runnable to this worker threads looper with a delay in milli second.
     */
    public void postRunnableDelayed(Runnable runnable, long delayMillis) {
        getBackgroundHandler().postDelayed(runnable, delayMillis);
    }

    /**
     * Tears down logic which is guaranteed to run after the looper test has either completed or failed.
     * This will run on the same thread as the looper test.
     */
    public void looperTearDown() {
    }


    /**
     * If an implementation of this is supplied with the annotation, the {@link RunnableBefore#run(RealmConfiguration)}
     * will be executed before the looper thread starts. It is normally for populating the Realm before the test.
     */
    public interface RunnableBefore {
        void run(RealmConfiguration realmConfig);
    }

    class RunInLooperThreadStatement extends Statement {
        private final RunTestInLooperThread annotation;
        private final Statement base;

        RunInLooperThreadStatement(RunTestInLooperThread annotation, Statement base) {
            this.annotation = annotation;
            this.base = base;
        }

        @Override
        @SuppressWarnings({"ClassNewInstance", "Finally"})
        public void evaluate() throws Throwable {
            before();

            Class<? extends RunnableBefore> runnableBefore = annotation.before();
            if (!runnableBefore.isInterface()) {
                runnableBefore.newInstance().run(getConfiguration()); // this is broken: config is mutable.
            }

            runTest(annotation.threadName());
        }

        @SuppressWarnings({"unused", "ThrowFromFinallyBlock"})
        private void runTest(final String threadName) throws Throwable {
            Exception testException = null;

            try {
                ExecutorService executorService = Executors.newSingleThreadExecutor(new ThreadFactory() {
                    @Override
                    public Thread newThread(Runnable runnable) { return new Thread(runnable, threadName); }
                });

                TestThread test = new TestThread(base);

                Future<?> ignored = executorService.submit(test);

                TestHelper.exitOrThrow(executorService, signalTestCompleted, test);
            } catch (Exception error) {
                // These exceptions should only come from TestHelper.awaitOrFail()
                testException = error;
            } finally {
                // Tries as hard as possible to close down gracefully, while still keeping all exceptions intact.
                cleanUp(testException);

                // Only TestHelper.awaitOrFail() threw an exception
                if (testException != null) {
                    //noinspection ThrowFromFinallyBlock
                    throw testException;
                }
            }
        }

        private void cleanUp(Exception testException) {
            try {
                after();
            } catch (Throwable e) {
                if (testException == null) {
                    // Only after() threw an exception
                    throw e;
                }

                // Both TestHelper.awaitOrFail() and after() threw an exception. Make sure we are aware of
                // that fact by printing both exceptions.
                StringWriter testStackTrace = new StringWriter();
                testException.printStackTrace(new PrintWriter(testStackTrace));

                StringWriter afterStackTrace = new StringWriter();
                e.printStackTrace(new PrintWriter(afterStackTrace));

                StringBuilder errorMessage = new StringBuilder()
                        .append("after() threw an error that shadows a test case error")
                        .append('\n')
                        .append("== Test case exception ==\n")
                        .append(testStackTrace.toString())
                        .append('\n')
                        .append("== after() exception ==\n")
                        .append(afterStackTrace.toString());

                fail(errorMessage.toString());
            }
        }
    }

    class TestThread implements Runnable, TestHelper.LooperTest {
        private final CountDownLatch signalClosedRealm = new CountDownLatch(1);
        private final Statement base;
        private Looper looper;
        private Throwable threadAssertionError;

        TestThread(Statement base) {
            this.base = base;
        }

        @Override
        public CountDownLatch getRealmClosedSignal() {
            return signalClosedRealm;
        }

        @Override
        public synchronized Looper getLooper() {
            return looper;
        }

        synchronized void setLooper(Looper looper) {
            this.looper = looper;
        }

        @Override
        public synchronized Throwable getAssertionError() {
            return threadAssertionError;
        }

        private synchronized void setException(Throwable threadAssertionError) {
            if (this.threadAssertionError == null) {
                this.threadAssertionError = threadAssertionError;
            }
        }

        @Override
        public void run() {
            Looper.prepare();
            setLooper(Looper.myLooper());
            setBackgroundHandler(new Handler(looper));
            try {
                initRealm();
                base.evaluate();
                Looper.loop();
            } catch (Throwable e) {
                setException(e);
                setUnitTestFailed();
            } finally {
                try {
                    looperTearDown();
                } catch (Throwable t) {
                    setUnitTestFailed();
                }
                testComplete();
                closeRealms();
                signalClosedRealm.countDown();
            }
        }
    }
}

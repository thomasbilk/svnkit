/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test;

import java.util.Iterator;
import java.util.ResourceBundle;
import java.util.Map;
import java.util.logging.Level;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.test.environments.AbstractSVNTestEnvironment;
import org.tmatesoft.svn.test.sandboxes.AbstractSVNSandbox;
import org.tmatesoft.svn.test.sandboxes.AbstractSVNSandboxFactory;
import org.tmatesoft.svn.test.sandboxes.SVNSandboxFactory;
import org.tmatesoft.svn.test.sandboxes.FSSandboxFactory;
import org.tmatesoft.svn.test.sandboxes.DAVSandboxFactory;
import org.tmatesoft.svn.test.tests.AbstractSVNTest;
import org.tmatesoft.svn.test.util.SVNResourceUtil;
import org.tmatesoft.svn.test.util.SVNTestDebugLog;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNTestRunner {

    public static void main(String[] args) {
        ResourceBundle bundle = SVNResourceUtil.createBundle();
        AbstractSVNTestEnvironment environment = SVNResourceUtil.createEnvironment(bundle);
        Map tests = SVNResourceUtil.createTests(bundle);

        FSSandboxFactory.setup(bundle);
        DAVSandboxFactory.setup(bundle);
        SVNSandboxFactory.setup(bundle);

        SVNTestDebugLog.init(bundle);

        for (Iterator iterator = tests.entrySet().iterator(); iterator.hasNext();) {
            Map.Entry entry = (Map.Entry) iterator.next();

            SVNTestDebugLog.getDebugLog().log(SVNLogType.DEFAULT, "Running test: " + entry.getKey(), Level.FINEST);
            SVNTestDebugLog.getDebugLog().log(SVNLogType.DEFAULT, "____________________________________", Level.FINEST);

            final AbstractSVNTest test = (AbstractSVNTest) entry.getValue();
            runSingleTest(test, environment, bundle);

            SVNTestDebugLog.getDebugLog().log(SVNLogType.DEFAULT, "____________________________________", Level.FINEST);
        }
    }

    private static void runSingleTest(AbstractSVNTest test, AbstractSVNTestEnvironment environment, ResourceBundle bundle) {
        Iterator sandboxes = AbstractSVNSandboxFactory.create();
        while (sandboxes.hasNext()) {
            AbstractSVNSandbox sandbox = (AbstractSVNSandbox) sandboxes.next();
            try {
                loadOptions(test, bundle);

                environment.init();
                sandbox.init(environment);
                test.init(sandbox, environment);
                test.run();
            } catch (SVNException e) {
                e.printStackTrace();
            } finally {
                disposeTest(test, bundle);
                disposeSandbox(sandbox, bundle);
                disposeEnvironment(environment, bundle);
            }
        }
    }

    private static void loadOptions(AbstractSVNTest test, ResourceBundle bundle) throws SVNException {
        ISVNTestOptions opts = test.getOptions();
        if (opts != null) {
            opts.load(bundle);
        }
    }

    private static void disposeTest(AbstractSVNTest test, ResourceBundle bundle) {
        boolean runCleanup = SVNResourceUtil.getBoolean("test.cleanup", bundle);
        if (runCleanup) {
            try {
                test.dispose();
            } catch (SVNException e) {
                SVNTestDebugLog.getDebugLog().log(SVNLogType.DEFAULT, e, Level.FINEST);
            }
        }
    }

    private static void disposeEnvironment(AbstractSVNTestEnvironment environment, ResourceBundle bundle) {
        boolean runCleanup = SVNResourceUtil.getBoolean("environment.cleanup", bundle);
        if (runCleanup) {
            try {
                environment.dispose();
            } catch (SVNException e) {
                SVNTestDebugLog.getDebugLog().log(SVNLogType.DEFAULT, e, Level.FINEST);
            }
        }
    }

    private static void disposeSandbox(AbstractSVNSandbox sandbox, ResourceBundle bundle) {
        boolean runCleanup = SVNResourceUtil.getBoolean("sandbox.cleanup", bundle);
        if (runCleanup) {
            try {
                sandbox.dispose();
            } catch (SVNException e) {
                SVNTestDebugLog.getDebugLog().log(SVNLogType.DEFAULT, e, Level.FINEST);
            }
        }
    }
}

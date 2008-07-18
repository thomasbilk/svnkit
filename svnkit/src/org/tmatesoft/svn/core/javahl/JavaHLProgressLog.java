/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.javahl;


import java.util.logging.Level;

import org.tigris.subversion.javahl.JavaHLObjectFactory;
import org.tigris.subversion.javahl.ProgressEvent;
import org.tigris.subversion.javahl.ProgressListener;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class JavaHLProgressLog extends SVNDebugLogAdapter {

    private ProgressListener myProgressListener;
    private long myProgress;

    public JavaHLProgressLog(ProgressListener progressListener) {
        myProgressListener = progressListener;
        reset();
    }

    public void log(String message, byte[] data) {
        myProgress += data.length;
        ProgressEvent event = JavaHLObjectFactory.createProgressEvent(myProgress, -1L);
        myProgressListener.onProgress(event);
    }

    public void reset() {
        myProgress = 0;
    }

    public void logSevere(String message) {
    }

    public void logSevere(Throwable th) {
    }

    public void logError(String message) {
    }

    public void logError(Throwable th) {
    }

    public void logFine(Throwable th) {
    }

    public void logFine(String message) {
    }

    public void logFiner(Throwable th) {
    }

    public void logFiner(String message) {
    }

    public void logFinest(Throwable th) {
    }

    public void logFinest(String message) {
    }

    public void log(Throwable th, Level logLevel) {
    }

    public void log(String message, Level logLevel) {
    }
}

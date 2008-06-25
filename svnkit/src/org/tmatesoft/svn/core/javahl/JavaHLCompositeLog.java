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

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;

import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class JavaHLCompositeLog extends SVNDebugLogAdapter {

    Set myLoggers;

    public JavaHLCompositeLog() {
        myLoggers = new HashSet();
    }

    public void addLogger(ISVNDebugLog debugLog) {
        myLoggers.add(debugLog);
    }

    public void removeLogger(ISVNDebugLog debugLog) {
        myLoggers.remove(debugLog);        
    }

    public void logError(String message) {
        log(message, Level.INFO);
    }

    public void logError(Throwable th) {
        log(th, Level.INFO);
    }

    public void logSevere(String message) {
        log(message, Level.SEVERE);
    }

    public void logSevere(Throwable th) {
        log(th, Level.SEVERE);
    }

    public void log(String message, byte[] data) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.log(message, data);
        }
    }

    public void logFine(Throwable th) {
        log(th, Level.FINE);
    }

    public void logFine(String message) {
        log(message, Level.FINE);
    }

    public void logFiner(Throwable th) {
        log(th, Level.FINER);
    }

    public void logFiner(String message) {
        log(message, Level.FINER);
    }

    public void logFinest(Throwable th) {
        log(th, Level.FINEST);
    }

    public void logFinest(String message) {
        log(message, Level.FINEST);
    }

    public void log(Throwable th, Level logLevel) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.log(th, logLevel);
        }
    }

    public void log(String message, Level logLevel) {
        for (Iterator iterator = myLoggers.iterator(); iterator.hasNext();) {
            ISVNDebugLog log = (ISVNDebugLog) iterator.next();
            log.log(message, logLevel);
        }
    }

}

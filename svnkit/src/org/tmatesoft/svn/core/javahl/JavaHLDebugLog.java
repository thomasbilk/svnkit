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

import java.io.File;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLogAdapter;

import org.tigris.subversion.javahl.JavaHLObjectFactory;
import org.tigris.subversion.javahl.SVNClientLogLevel;


/**
 * @author TMate Software Ltd.
 * @version 1.1.1
 */
public class JavaHLDebugLog extends SVNDebugLogAdapter {

    private static final String JAVAHL_LOGGER_NAME = "svnkit-javahl";

    private static JavaHLDebugLog ourInstance = new JavaHLDebugLog();

    private Map myHandlers = new HashMap();
    private Logger myLogger;

    public static JavaHLDebugLog getInstance() {
        return ourInstance;
    }

    public void enableLogging(int logLevel, File logPath, Formatter formatter) throws SVNException {
        logPath = logPath.getAbsoluteFile();
        if (logLevel == SVNClientLogLevel.NoLog) {
            if (logPath == null) {
                resetLogHandlers();
            } else {
                Handler handler = (Handler) myHandlers.remove(logPath);
                handler.close();
                getLogger().removeHandler(handler);
            }
            return;
        }

        Level level = JavaHLObjectFactory.getLoggingLevel(logLevel);
        Handler handler = (Handler) myHandlers.get(logPath);
        if (handler == null) {
            OutputStream logStream = SVNFileUtil.openFileForWriting(logPath);
            handler = new StreamHandler(logStream, formatter);
        }
        myHandlers.put(logPath, handler);
        handler.setFormatter(formatter);
        handler.setLevel(level);
        getLogger().addHandler(handler);
    }

    private void resetLogHandlers() {
        if (getLogger().getHandlers() == null) {
            return;
        }
        for (int i = 0; i < getLogger().getHandlers().length; i++) {
            Handler handler = getLogger().getHandlers()[i];
            handler.close();
            getLogger().removeHandler(handler);
        }
    }

    private Logger getLogger() {
        if (myLogger == null) {
            myLogger = Logger.getLogger(JAVAHL_LOGGER_NAME);
            myLogger.setUseParentHandlers(false);
            myLogger.setLevel(Level.ALL);
            resetLogHandlers();
        }
        return myLogger;
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
        if (getLogger().isLoggable(Level.FINEST)) {
            try {
                getLogger().log(Level.FINEST, message + "\n" + new String(data, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                getLogger().log(Level.FINEST, message + "\n" + new String(data));
            }
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
        if (getLogger().isLoggable(logLevel) && th != null) {
            getLogger().log(logLevel, th.getMessage(), th);
        }
    }

    public void log(String message, Level logLevel) {
        if (getLogger().isLoggable(logLevel) && message != null) {
            getLogger().log(logLevel, message);
        }
    }
}

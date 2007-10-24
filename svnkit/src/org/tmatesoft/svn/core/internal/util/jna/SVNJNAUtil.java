/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.util.jna;

import java.io.File;

import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNJNAUtil {
    
    private static boolean ourIsJNAPresent;
    private static final String JNA_CLASS_NAME = "com.sun.jna.Library";
    
    static {
        try {
            ClassLoader loader = SVNJNAUtil.class.getClassLoader();
            if (loader == null) {
                loader = ClassLoader.getSystemClassLoader();
            }
            if (loader != null && loader.loadClass(JNA_CLASS_NAME) != null) {
                ourIsJNAPresent = true;
            }
        } catch (ClassNotFoundException e) {
            ourIsJNAPresent = false;
        }
    }
    
    public static boolean isJNAPresent() {
        return ourIsJNAPresent;
    }

    // linux.
    
    public static SVNFileType getFileType(File file) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.getFileType(file);
        }
        return null;
    }

    public static Boolean isExecutable(File file) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.isExecutable(file);
        }
        return null;
    }

    public static String getLinkTarget(File file) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.getLinkTarget(file);
        }
        return null;
    }

    public static boolean setExecutable(File file, boolean set) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.setExecutable(file, set);
        }
        return false;
    }

    public static boolean createSymlink(File file, String linkName) {
        if (isJNAPresent()) {
            return SVNLinuxUtil.createSymlink(file, linkName);
        }
        return false;
    }

    // linux and win32.
    public static boolean setWritable(File file) {
        if (isJNAPresent()) {
            return SVNFileUtil.isWindows ?
                    SVNWin32Util.setWritable(file) :
                    SVNLinuxUtil.setWritable(file);
        }
        return false;
    }

    // win32
    public static boolean setHidden(File file) {
        if (isJNAPresent()) {
            return SVNWin32Util.setHidden(file);
        }
        return false;
    }

    public static boolean moveFile(File src, File dst) {
        if (isJNAPresent()) {
            return SVNWin32Util.moveFile(src, dst);
        }
        return false;
    }
    
    public static String decrypt(String encryptedData) {
        if (isJNAPresent()) {
            return SVNWinCrypt.decrypt(encryptedData);
        }
        return null;
    }
    
    public static String encrypt(String rawData) {
        if (isJNAPresent()) {
            return SVNWinCrypt.encrypt(rawData);
        }
        return null;
    }

    public synchronized static boolean isWinCryptEnabled() {
        return isJNAPresent() && SVNWinCrypt.isEnabled();
    }
}

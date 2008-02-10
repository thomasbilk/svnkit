/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc.admin3;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.Map;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;


/**
 * This class reads and writes information to the administrative area 
 * depending on the version of that very area.
 * 
 * This class uses SVNAdminLayout instance to open admin files.
 * 
 * @version 1.2
 * @author  TMate Software Ltd.
 */
public class SVNAdminArea {

    public static final int BASE_PROPERTIES     = 0;
    public static final int REVERT_PROPERTIES   = 1;
    public static final int WC_PROPERTIES       = 2;
    public static final int WORKING_PROPERTIES  = 3;
    
    private static SVNAdminArea ourInstance;
    
    private static final int MIN_SUPPORTED_FORMAT = 2;
    private static final int MAX_SUPPORTED_FORMAT = 9;
    
    private static final String[] DIR_PROPFILE_NAME = {
        SVNAdminLayout.FILE_DIR_PROP_BASE, 
        SVNAdminLayout.FILE_DIR_PROP_REVERT,
        SVNAdminLayout.FILE_DIR_WCPROPS,
        SVNAdminLayout.FILE_DIR_PROPS,
       };
    
    private static final String[] FILE_PROPFILE_DIR = {
        SVNAdminLayout.DIR_PROP_BASE, 
        SVNAdminLayout.DIR_PROP_BASE,
        SVNAdminLayout.DIR_WCPROPS,
        SVNAdminLayout.DIR_PROPS,
       };
    
    private static final String[] FILE_PROPFILE_EXT = {
        SVNAdminLayout.EXTENSION_BASE, 
        SVNAdminLayout.EXTENSION_REVERT,
        SVNAdminLayout.EXTENSION_WORK,
        SVNAdminLayout.EXTENSION_WORK,
       };
    
    private static final String DEFAULT_CACHABLE_PROPERTIES = SVNProperty.SPECIAL + " " + SVNProperty.EXTERNALS + " " + SVNProperty.NEEDS_LOCK;

    public static SVNAdminArea getAdminArea(int format) {
        if (format == -1) {
            // return default one.
            return ourInstance;
        }
        if (format >= MIN_SUPPORTED_FORMAT && format <= MAX_SUPPORTED_FORMAT) {
            if (ourInstance == null) {
                ourInstance = new SVNAdminArea();
            }
            return ourInstance;
        } 
        return null;
    }

    public static void assertWCFormatIsSupported(int format, File path) throws SVNException {
        if (format < MIN_SUPPORTED_FORMAT) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                    "Working copy format of ''{0}'' is too old {1}; " +
                    "please check out your working copy again", new Object[] {path, new Integer(format)});
            SVNErrorManager.error(err);
        } else if (format > MAX_SUPPORTED_FORMAT) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_UNSUPPORTED_FORMAT, 
                    "This client is too old to work with working copy ''{0}''. You need \n" +
                    "to get a newer Subversion client, or to downgrade this working copy.\n" +
                    "See http://subversion.tigris.org/faq.html#working-copy-format-change\nfor details.",
                    path);
            SVNErrorManager.error(err);
        }
    }
    
    protected SVNAdminArea() {
        
    }
    
    public String getThisDirName(SVNWCAccess wcAccess) {
        if (wcAccess.getFormat() < 4) {
            return "svn:this_dir";
        }
        return "";
    }
    
    public boolean hasPropcaching(SVNWCAccess wcAccess) {
        return wcAccess.getFormat() > 5;
    }
    
    public boolean hasSingleWCPropertiesFile(SVNWCAccess wcAccess) {
        return wcAccess.getFormat() > 7;
    }
    
    public boolean hasBinaryEntriesFile(SVNWCAccess wcAccess) {
        return wcAccess.getFormat() > 6;
    }
    
    public String getCachableProperties() {
        return DEFAULT_CACHABLE_PROPERTIES;
    }
    
    // read entries.
    public void readEntries(SVNWCAccess wcAccess) throws SVNException {
        InputStream is = null;
        try {
            is = getLayout().read(wcAccess, null, SVNAdminLayout.FILE_ENTRIES, null, false);
            Map entries = null;
            if (hasBinaryEntriesFile(wcAccess)) {
                entries = SVNEntriesUtil.readEntries(is, wcAccess.getPath(), getThisDirName(wcAccess));
            } else {
                // TODO read XML entries.
            }   
            wcAccess.setEntries(entries);
        } finally {
            getLayout().close(is);
        }
    }
    
    public Map readProperties(SVNWCAccess wcAccess, String path, SVNNodeKind kind, int propType, boolean tmp, Map map) throws SVNException {
        InputStream is = null;
        // TODO check for different formats, especially for wcprops.
        try {
            if (kind == SVNNodeKind.DIR) {
                wcAccess = wcAccess.retrive(path);
                if (!getLayout().exists(wcAccess, null, DIR_PROPFILE_NAME[propType], null, tmp)) {
                    return Collections.EMPTY_MAP;
                }
                is = getLayout().read(wcAccess, null, DIR_PROPFILE_NAME[propType], null, tmp);
            } else {
                String name = SVNPathUtil.tail(path);
                path = SVNPathUtil.removeTail(path);
                wcAccess = wcAccess.retrive(path);
                if (!getLayout().exists(wcAccess, FILE_PROPFILE_DIR[propType], name, FILE_PROPFILE_EXT[propType], tmp)) {
                    return Collections.EMPTY_MAP;
                }
                is = getLayout().read(wcAccess, FILE_PROPFILE_DIR[propType], name, FILE_PROPFILE_EXT[propType], tmp);
            }
            return SVNHashUtil.readHash(is, map);
        } catch (IOException e) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
        } finally {
            getLayout().close(is);
        }
        return null;
    }

    public String writeProperties(SVNWCAccess wcAccess, String path, SVNNodeKind kind, int propType, boolean tmp, Map map) throws SVNException {
        OutputStream os = null;
        String dir = kind == SVNNodeKind.DIR ? null : FILE_PROPFILE_DIR[propType];
        String name = kind == SVNNodeKind.DIR ? DIR_PROPFILE_NAME[propType] : SVNPathUtil.tail(path);
        String extension = kind == SVNNodeKind.DIR ? null : FILE_PROPFILE_EXT[propType];
        try {
            if (kind == SVNNodeKind.DIR) {
                wcAccess = wcAccess.retrive(path);
            } else {
                wcAccess = wcAccess.retrive(SVNPathUtil.removeTail(path));
            }
            os = getLayout().write(wcAccess, dir, name, extension, tmp);
            SVNHashUtil.writeHash(os, map, null, SVNHashUtil.HASH_TERMINATOR);            
        } catch (IOException e) {
            SVNFileUtil.closeFile(os);
            os = null;
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.IO_ERROR), e);
        } finally {
            if (os != null) {
                // no sync. 
                getLayout().close(wcAccess, dir, name, extension, os, false);
            }
        }
        return getPropertiesPath(wcAccess, path, kind, propType, tmp);
    }
    
    public String getPropertiesPath(SVNWCAccess wcAccess, String path, SVNNodeKind kind, int propType, boolean tmp) {
        String dir = kind == SVNNodeKind.DIR ? null : FILE_PROPFILE_DIR[propType];
        String name = kind == SVNNodeKind.DIR ? DIR_PROPFILE_NAME[propType] : SVNPathUtil.tail(path);
        String extension = kind == SVNNodeKind.DIR ? null : FILE_PROPFILE_EXT[propType];
        return getLayout().getAbsolutePath(wcAccess, dir, name, extension, tmp);
    }
    
    public void lock(SVNWCAccess wcAccess) throws SVNException {
        boolean exists = getLayout().exists(wcAccess, null, SVNAdminLayout.FILE_LOCK, null, false);
        if (exists) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.WC_LOCKED);
            SVNErrorManager.error(err);
        }
        getLayout().create(wcAccess, null, SVNAdminLayout.FILE_LOCK, null, SVNNodeKind.FILE, false);
    }
    
    public void unlock(SVNWCAccess wcAccess) throws SVNException {
        try {
            getLayout().delete(wcAccess, null, SVNAdminLayout.FILE_LOCK, null, false);
        } catch (SVNException e) {
            if (!getLayout().exists(wcAccess, null, SVNAdminLayout.FILE_LOCK, null, false)) {
                return;
            }
            throw e;
        }
    }

    // write entries.
    
    protected SVNAdminLayout getLayout() {
        return SVNAdminLayout.getInstance();
    }
}

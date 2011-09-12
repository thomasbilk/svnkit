/*
 * ====================================================================
 * Copyright (c) 2004 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */

package org.tmatesoft.svn.core.internal;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.tmatesoft.svn.core.ISVNDirectoryEntry;
import org.tmatesoft.svn.core.ISVNEntry;
import org.tmatesoft.svn.core.ISVNStatusHandler;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNStatus;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
class SVNStatusUtil {
    
    static void doStatus(SVNWorkspace ws, ISVNDirectoryEntry parent, SVNStatusEditor editor, ISVNStatusHandler handler, String path, Collection externals, boolean descend,
            boolean includeUnmodified, boolean includeIgnored, boolean descendInUnversioned) throws SVNException {
        externals = externals == null ? new HashSet() : externals;
        
        ISVNEntry entry = ws.locateEntry(path, true);
        if (entry != null && entry.isDirectory()) {
            externals = SVNExternal.create(entry.asDirectory(), externals);
        }
        Map statuses = localStatus(ws, parent, path, null, externals, !descend);
        if (editor != null) { 
            statuses = editor.completeStatus(statuses, path, descend);
        }
        // fill in remote information from editor.
        
        if (!entry.isDirectory()) {
            // ignore ignored, unchanged...
            handleStatus(handler, (SVNStatus) statuses.get(entry.getName()), includeUnmodified, includeIgnored);
            return;
        }
        SVNStatus dirStatus = (SVNStatus) statuses.remove("");
        if (dirStatus.getContentsStatus() == SVNStatus.EXTERNAL || (!descendInUnversioned && !entry.isManaged())) {
            handleStatus(handler, dirStatus, includeUnmodified, includeIgnored);
            return;
        }
        // descend
        if (descend && dirStatus.getContentsStatus() != SVNStatus.OBSTRUCTED) {
            for(Iterator names = statuses.keySet().iterator(); names.hasNext();) {
                String name = (String) names.next();
                SVNStatus status = (SVNStatus) statuses.get(name);
                if (status == null) {
                    if (!includeIgnored && entry.asDirectory().isIgnored(name)) {
                        continue;
                    }
                    doStatus(ws, entry.asDirectory(), editor, handler, PathUtil.append(path, name), externals, true, 
                            includeUnmodified, includeIgnored, descendInUnversioned);
                }
            }
        }
        
        for(Iterator names = statuses.keySet().iterator(); names.hasNext();) {
            String name = (String) names.next();
            SVNStatus status = (SVNStatus) statuses.get(name);
            if (status != null) {
                handleStatus(handler, status, includeUnmodified, includeIgnored);
            }
        }        
        handleStatus(handler, dirStatus, includeUnmodified, includeIgnored);
    }
    
    private static void handleStatus(ISVNStatusHandler handler, SVNStatus status, boolean includeUnmodified, boolean includeIgnored) {
        if (!includeIgnored && status.getContentsStatus() == SVNStatus.IGNORED) {
            return;
        }
        if (!includeUnmodified && 
                status.getContentsStatus() == SVNStatus.NOT_MODIFIED && 
                status.getPropertiesStatus() == SVNStatus.NOT_MODIFIED &&
                status.getRepositoryRevision() < 0 &&
                !status.isSwitched()) {
            return;
        }
        handler.handleStatus(status.getPath(), status);
            
    }
    
    /**
     * Creates statuses for a single directory (with directory itself?), 
     * including local and remote subdirectories and files
     */
    private static Map localStatus(SVNWorkspace ws, ISVNDirectoryEntry parent, String path, Map result, Collection externals, boolean includeDirs) throws SVNException {
        result = result == null ? new TreeMap() : result;
        
        // 1. get entry
        ISVNEntry entry = ws.locateEntry(path, true);
        if (entry == null) {
            // can't locate even entry itself, neither managed nor unmanaged.
            return result; 
        }
        
        // 2. fetch URL if exists, will be needed to compute "switched" status.
        // 2.1. create status for entry.
        if (entry != null && entry.isManaged()) {
            String parentURL = null;
            if (parent != null) {
                parentURL = parent.getPropertyValue(SVNProperty.URL);
            }
            result.put(entry.isDirectory() ? "" : entry.getName(), createManagedStatus(parentURL, entry));
        } else {
            result.put(entry.isDirectory() ? "" : entry.getName(), createUnmanagedStatus(externals, parent, entry));
        }
        if (!entry.isDirectory()) {
            return result;
        }
        String url = entry.getPropertyValue(SVNProperty.URL);
        // 3. compute children local statuses.
        for(Iterator children = entry.asDirectory().childEntries(); children.hasNext();) {
            ISVNEntry child = (ISVNEntry) children.next();
            if (!includeDirs && child.isDirectory()) {
                result.put(child.getName(), null);
                continue;
            }
            SVNStatus status = createManagedStatus(url, child);
            result.put(child.getName(), status);
        }        
        for(Iterator children = entry.asDirectory().unmanagedChildEntries(true); children.hasNext();) {
            ISVNEntry child = (ISVNEntry) children.next();
            
            if (!includeDirs && child.isDirectory()) {
                result.put(child.getName(), null);
                continue;
            }
            SVNStatus status = createUnmanagedStatus(externals, entry, child);
            result.put(child.getName(), status);
        }
        return result;
    }
    
    private static SVNStatus createUnmanagedStatus(Collection externals, ISVNEntry entry, ISVNEntry child) throws SVNException {
        int propStatus = SVNStatus.NOT_MODIFIED;
        int contentsStatus = SVNStatus.UNVERSIONED;
        boolean isDirectory = child.isDirectory();
        String childPath = child.getPath();
        if (entry != null && entry.asDirectory().isIgnored(child.getName())) {
            contentsStatus = SVNStatus.IGNORED;
        } else if (externals != null) {
            for(Iterator paths = externals.iterator(); paths.hasNext();) {
                SVNExternal externalPath = (SVNExternal) paths.next();
                if (externalPath.getPath().startsWith(childPath)) {
                    contentsStatus = SVNStatus.EXTERNAL;
                    break;
                }
            }
        } 
        SVNStatus status = new SVNStatus(childPath, propStatus, contentsStatus, -1, -1, false, false, isDirectory, null);
        return status;
    }

    private static SVNStatus createManagedStatus(String parentURL, ISVNEntry child) throws SVNException {
        boolean isDirectory = child.isDirectory();
        int propStatus = SVNStatus.NOT_MODIFIED;
        int contentsStatus = SVNStatus.NOT_MODIFIED;
        
        // may be prop-modified (dir or file).
        if (child.isPropertiesModified()) {
            propStatus = SVNStatus.MODIFIED;
        }
        // may be prop-conflict (dir or file)
        if (child.getPropertyValue(SVNProperty.PROP_REJECT_FILE) != null) {
            propStatus = SVNStatus.CONFLICTED;
        }
        boolean history = false;
        boolean switched = false;

        if (child.isMissing()) {
            // may be missed (dir or file).
            contentsStatus = SVNStatus.MISSING;
        } else if (child.isObstructed()) {
            contentsStatus = SVNStatus.OBSTRUCTED;
        } else if (child.isScheduledForAddition()) {
            // may be added (copied) or deleted (dir or file).
            contentsStatus = SVNStatus.ADDED;
            history = SVNProperty.booleanValue(child.getPropertyValue(SVNProperty.COPIED));
            if (child.isScheduledForDeletion()) {
                contentsStatus = SVNStatus.REPLACED;
            }
        } else if (child.isScheduledForDeletion()) {
            // may be deleted
            contentsStatus = SVNStatus.DELETED;
        } else if (!isDirectory) {
            // may be conflicted (file)
            if (child.getPropertyValue(SVNProperty.CONFLICT_OLD) != null || 
                    child.getPropertyValue(SVNProperty.CONFLICT_NEW) != null ||
                    child.getPropertyValue(SVNProperty.CONFLICT_WRK) != null) {
                contentsStatus = SVNStatus.CONFLICTED;
            } else if (child.asFile().isContentsModified()) {
                // may be modified (file)
                contentsStatus = SVNStatus.MODIFIED;
            }
        }  
        if (contentsStatus == SVNStatus.ADDED || contentsStatus == SVNStatus.DELETED) {
            propStatus = SVNStatus.NOT_MODIFIED;
            
        }
        if (isDirectory && SVNReporterBaton.isSwitched(parentURL, child)) {
            switched = true;
        }
        long revision = SVNProperty.longValue(child.getPropertyValue(SVNProperty.COMMITTED_REVISION));
        long wcRevision = SVNProperty.longValue(child.getPropertyValue(SVNProperty.REVISION));
        String author = child.getPropertyValue(SVNProperty.LAST_AUTHOR);
        SVNStatus status = new SVNStatus(child.getPath(), propStatus, contentsStatus, revision, wcRevision, history, switched, isDirectory, author);
        return status;
    }

    static SVNStatus createStatus(ISVNEntry entry, long remoteRevision, int remoteContents, int remoteProps, String parentURL) throws SVNException {
        int propStatus = SVNStatus.NOT_MODIFIED;
        if (entry.getPropertyValue(SVNProperty.PROP_REJECT_FILE) != null) {
            propStatus = SVNStatus.CONFLICTED;
        } else if (entry.isPropertiesModified()) {
            propStatus = SVNStatus.MODIFIED;
        } 
        int contentsStatus = SVNStatus.NOT_MODIFIED;
        boolean addedWithHistory = false;
        if (entry.getPropertyValue(SVNProperty.CONFLICT_OLD) != null || 
            entry.getPropertyValue(SVNProperty.CONFLICT_NEW) != null ||
            entry.getPropertyValue(SVNProperty.CONFLICT_WRK) != null) {
            contentsStatus = SVNStatus.CONFLICTED;
        } else if (entry.isMissing()) {
            contentsStatus = SVNStatus.MISSING;
        } else if (entry.isScheduledForAddition() && entry.isScheduledForDeletion()) { 
            contentsStatus = SVNStatus.REPLACED;
        } else if (entry.isScheduledForAddition()) {
            contentsStatus = SVNStatus.ADDED;
            addedWithHistory = SVNProperty.booleanValue(entry.getPropertyValue(SVNProperty.COPIED));
        } else if (entry.isScheduledForDeletion()) {
            contentsStatus = SVNStatus.DELETED;
        } else if (!entry.isDirectory() && entry.asFile().isContentsModified()) {
            contentsStatus = SVNStatus.MODIFIED;
        }
        
        boolean isSwitched = SVNReporterBaton.isSwitched(parentURL, entry);
        long revision = SVNProperty.longValue(entry.getPropertyValue(SVNProperty.REVISION));
        long wcRevision = SVNProperty.longValue(entry.getPropertyValue(SVNProperty.COMMITTED_REVISION));

        if (remoteRevision < 0) {
            remoteRevision = wcRevision;
        }
        boolean isDir = SVNProperty.KIND_DIR.equals(entry.getPropertyValue(SVNProperty.KIND));
        return new SVNStatus(entry.getPath(), propStatus, contentsStatus, revision, wcRevision, remoteRevision, remoteContents, remoteProps, addedWithHistory, isSwitched, isDir, null);
    }
}
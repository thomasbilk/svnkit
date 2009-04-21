/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.internal.wc.SVNConflictVersion;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNTreeConflictDescription extends SVNConflictDescription {

    private SVNOperation myOperation;
    private SVNConflictVersion mySourceLeftVersion;
    private SVNConflictVersion mySourceRightVersion;

    public SVNTreeConflictDescription(File path, SVNNodeKind nodeKind, SVNConflictAction conflictAction, SVNConflictReason conflictReason, SVNOperation operation, SVNConflictVersion sourceLeftVersion, SVNConflictVersion sourceRightVersion) {
        super(new SVNMergeFileSet(null, null, null, path, null, null, null, null, null), nodeKind, conflictAction, conflictReason);
        myOperation = operation;
        mySourceLeftVersion = sourceLeftVersion;
        mySourceRightVersion = sourceRightVersion;
    }

    public boolean isTextConflict() {
        return false;
    }

    public boolean isPropertyConflict() {
        return false;
    }

    public boolean isTreeConflict() {
        return true;
    }

    public File getPath() {
        return getMergeFiles().getLocalFile();
    }

    public SVNOperation getOperation() {
        return myOperation;
    }

    public SVNConflictVersion getSourceLeftVersion() {
        return mySourceLeftVersion;
    }

    public SVNConflictVersion getSourceRightVersion() {
        return mySourceRightVersion;
    }

    public String getPropertyName() {
        return null;
    }

    
}
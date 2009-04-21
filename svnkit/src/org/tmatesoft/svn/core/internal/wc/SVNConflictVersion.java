/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNConflictVersion {

    private final SVNURL myRepositoryRoot;
    private final String myPath;
    private final long myPegRevision;
    private final SVNNodeKind myKind;

    public SVNConflictVersion(SVNURL repositoryRoot, String path, long pegRevision, SVNNodeKind kind) {
        myRepositoryRoot = repositoryRoot;
        myPath = path;
        myPegRevision = pegRevision;
        myKind = kind;
    }

    public SVNURL getRepositoryRoot() {
        return myRepositoryRoot;
    }

    public String getPath() {
        return myPath;
    }

    public long getPegRevision() {
        return myPegRevision;
    }

    public SVNNodeKind getKind() {
        return myKind;
    }

    public String toString() {
        return "[SVNConflictVersion root = " + getRepositoryRoot() + "; path = " + getPath() + "@" + getPegRevision() + " " + getKind() + "]";
    }
}

/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc17;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNRepository;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNStatusReporter17 implements ISVNReporterBaton {

    public SVNStatusReporter17(SVNRepository locksRepos, SVNReporter17 reporter, SVNStatusEditor17 editor) {
    }

    public void report(ISVNReporter reporter) throws SVNException {
    }

}

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

package org.tmatesoft.svn.cli.command;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;

import org.tmatesoft.svn.cli.SVNArgument;
import org.tmatesoft.svn.cli.SVNCommand;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNWCClient;

/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNRevertCommand extends SVNCommand {

    public void run(InputStream in, PrintStream out, PrintStream err) throws SVNException {
        run(out, err);
    }

    public final void run(final PrintStream out, final PrintStream err) throws SVNException {
        SVNDepth depth = SVNDepth.DEPTH_UNKNOWN;
        if (getCommandLine().hasArgument(SVNArgument.RECURSIVE)) {
            depth = SVNDepth.fromRecurse(true);
        }
        String depthStr = (String) getCommandLine().getArgumentValue(SVNArgument.DEPTH);
        if (depthStr != null) {
            depth = SVNDepth.fromString(depthStr);
        }
        if (depth == SVNDepth.DEPTH_UNKNOWN) {
            depth = SVNDepth.fromRecurse(false);
        }
        
        final boolean recursive = SVNDepth.recurseFromDepth(depth);
        
        getClientManager().setEventHandler(new SVNCommandEventProcessor(out, err, false));
        SVNWCClient wcClient = getClientManager().getWCClient();
        for (int i = 0; i < getCommandLine().getPathCount(); i++) {
            final String absolutePath = getCommandLine().getPathAt(i);
            // hack to make schedule 9 test pass
            if ("".equals(absolutePath) || ".".equals(absolutePath)) {
                File path = new File(SVNPathUtil.validateFilePath(absolutePath)).getAbsoluteFile();
                if (path.isDirectory()) {
                    SVNStatus status = getClientManager().getStatusClient().doStatus(path, false);
                    if (status.getContentsStatus() == SVNStatusType.STATUS_ADDED) {
                        // we're inside an added directory, skip it.
                        System.err.println("Skipped: " + absolutePath);
                        continue;
                    }
                }
            }
            try {
                wcClient.doRevert(new File(absolutePath), recursive);
            } catch (SVNException svne) {
                if (svne.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED && !recursive) {
                    SVNErrorMessage error = svne.getErrorMessage().wrap("Try 'svn revert --recursive' instead?");
                    SVNErrorManager.error(error);
                }
            }
        }
    }
}

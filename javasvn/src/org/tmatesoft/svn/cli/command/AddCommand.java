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

package org.tmatesoft.svn.cli.command;

import java.io.*;
import org.tmatesoft.svn.cli.*;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.io.*;
import org.tmatesoft.svn.util.*;

/**
 * @author TMate Software Ltd.
 */
public class AddCommand extends LocalModificationCommand {

	protected void run(final PrintStream out, PrintStream err, final ISVNWorkspace workspace, String relativePath) throws SVNException {
		boolean recursive = !getCommandLine().hasArgument(SVNArgument.NON_RECURSIVE);
		workspace.add(relativePath, false, recursive);
	}

	protected void log(PrintStream out, String relativePath) {
		DebugLog.log("A  " + relativePath);
		out.println("A  " + relativePath);
	}
}

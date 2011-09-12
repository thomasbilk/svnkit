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
package org.tmatesoft.svn.cli2.svnlook;

import org.tmatesoft.svn.cli2.AbstractSVNCommand;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class SVNLookCommand extends AbstractSVNCommand {

    public SVNLookCommand(String name, String[] aliases) {
        super(name, aliases);
    }

    protected String getResourceBundleName() {
        return "org.tmatesoft.svn.cli2.svnsync.commands";
    }

}
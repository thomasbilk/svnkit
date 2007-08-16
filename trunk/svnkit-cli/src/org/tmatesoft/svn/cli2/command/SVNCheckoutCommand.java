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
package org.tmatesoft.svn.cli2.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.SVNCommandTarget;
import org.tmatesoft.svn.cli2.SVNCommandUtil;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNCheckoutCommand extends SVNCommand {

    public SVNCheckoutCommand() {
        super("checkout", new String[] {"co"});
    }

    protected Collection createSupportedOptions() {
        Collection options = new LinkedList();
        options.add(SVNOption.REVISION);
        options.add(SVNOption.NON_RECURSIVE);
        options.add(SVNOption.DEPTH);
        options.add(SVNOption.FORCE);
        options = SVNOption.addAuthOptions(options);
        options.add(SVNOption.CONFIG_DIR);
        options.add(SVNOption.IGNORE_EXTERNALS);
        return options;
    }

    public void run() throws SVNException {
        List targets = getSVNEnvironment().combineTargets(new ArrayList());
        if (targets.isEmpty()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        String lastTarget = (String) targets.get(targets.size() - 1);
        if (SVNCommandUtil.isURL(lastTarget)) {
            if (targets.size() == 1) {
                SVNCommandTarget target = new SVNCommandTarget(lastTarget, true);
                lastTarget = target.getURL().getPath();
                lastTarget = SVNPathUtil.tail(lastTarget);
            } else {
                lastTarget = "";
            }
            targets.add(lastTarget);
        } else if (targets.size() == 1){
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS));
        }
        SVNUpdateClient client = getSVNEnvironment().getClientManager().getUpdateClient();
        if (!getSVNEnvironment().isQuiet()) {
            client.setEventHandler(new SVNNotifyPrinter(getSVNEnvironment(), true, false, false));
        }

        SVNRevision revision = getSVNEnvironment().getStartRevision();
        for (int i = 0; i < targets.size() - 1; i++) {
            String targetName = (String) targets.get(i);
            SVNCommandTarget target = new SVNCommandTarget(targetName, true);
            if (!target.isURL()) {
                SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.BAD_URL, "''{0}'' doesn not appear to be a URL", targetName));
            }
            String targetDir;
            SVNCommandTarget dstTarget;
            if (targets.size() == 2) {
                // url + path
                targetDir = lastTarget;
                dstTarget = new SVNCommandTarget(targetDir);
            } else {
                // all urls + base dst.
                targetDir = target.getURL().getPath();
                targetDir = SVNPathUtil.tail(targetDir);
                targetDir = SVNPathUtil.append(lastTarget, targetDir);
                dstTarget = new SVNCommandTarget(targetDir);
            }
            SVNRevision pegRevision = target.getPegRevision();
            if (revision == SVNRevision.UNDEFINED) {
                revision = pegRevision != SVNRevision.UNDEFINED ? pegRevision : SVNRevision.HEAD;
            }
            client.doCheckout(target.getURL(), dstTarget.getFile(), pegRevision, revision, getSVNEnvironment().getDepth(), getSVNEnvironment().isForce());
        }
    }
}

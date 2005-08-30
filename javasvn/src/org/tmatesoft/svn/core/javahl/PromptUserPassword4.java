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
package org.tmatesoft.svn.core.javahl;

import org.tigris.subversion.javahl.PromptUserPassword3;


/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public interface PromptUserPassword4 extends PromptUserPassword3 {

    public boolean promptSSH(String realm, String username, int sshPort, boolean maySave);
    
    public String getSSHPrivateKeyPath();
    
    public String getSSHPrivateKeyPassphrase();

    public int getSSHPort();

}

/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://tmate.org/svn/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.dav.http;

import org.tmatesoft.svn.core.auth.SVNPasswordAuthentication;
import org.tmatesoft.svn.core.internal.util.SVNBase64;


class HTTPBasicAuthentication extends HTTPAuthentication {

    public HTTPBasicAuthentication (SVNPasswordAuthentication credentials) {
        super(credentials);
    }

    public String authenticate() {
        StringBuffer result = new StringBuffer();
        String authStr = getUserName() + ":" + getPassword();
        authStr = SVNBase64.byteArrayToBase64(authStr.getBytes());
        result.append("Basic ");
        result.append(authStr);

        return result.toString();
    }

}
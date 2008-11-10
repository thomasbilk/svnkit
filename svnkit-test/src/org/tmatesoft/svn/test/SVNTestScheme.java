/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.test;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class SVNTestScheme {

    public static final SVNTestScheme FILE = new SVNTestScheme("file:///");
    public static final SVNTestScheme DAV = new SVNTestScheme("http://");
    public static final SVNTestScheme SVN = new SVNTestScheme("svn://");


    private String myProtocol;

    private SVNTestScheme(String protocol) {
        myProtocol = protocol;
    }

    public String toString() {
        return myProtocol;
    }
}

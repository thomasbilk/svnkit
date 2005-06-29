/*
 * Created on 17.05.2005
 */
package org.tmatesoft.svn.core.wc;

import org.tmatesoft.svn.core.SVNCancelException;

public interface ISVNEventHandler {
    
    public static final double UNKNOWN = -1;
    
    public void handleEvent(SVNEvent event, double progress);
    
    public void checkCancelled() throws SVNCancelException;

}

/*
 * ====================================================================
 * Copyright (c) 2004-2006 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

//import java.io.File;
//import java.io.IOException;
//import java.io.InputStream;
import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
//import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
//import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.wc.SVNCancellableEditor;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
//import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNSynchronizeEditor;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * The <b>SVNAdminClient</b> class provides methods that brings repository-side functionality
 * and repository synchronizing features.
 * 
 * <p>
 * Repository administrative methods are analogues of the corresponding commands of the native 
 * Subversion 'svnadmin' utility, while repository synchronizing methods are the ones for the
 * 'svnsync' utility. 
 * 
 * <p>
 * Here's a list of the <b>SVNAdminClient</b>'s methods 
 * matched against corresponing commands of the Subversion svnsync command utility:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doInitialize()</td><td>'svnsync initialize'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doSynchronize()</td><td>'svnsync synchronize'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doCopyRevisionProperties()</td><td>'svnsync copy-revprops'</td>
 * </tr>
 * </table>
 * 
 * @version 1.1.0
 * @author  TMate Software Ltd.
 * @since   1.1
 */
public class SVNAdminClient extends SVNBasicClient {

/*    public static final String DUMPFILE_MAGIC_HEADER = "SVN-fs-dump-format-version";
    public static final String DUMPFILE_REVISION_NUMBER = "Revision-number";
    public static final String DUMPFILE_NODE_PATH = "Node-path";
    public static final String DUMPFILE_NODE_KIND = "Node-kind";
    public static final String DUMPFILE_NODE_ACTION = "Node-action";
    public static final String DUMPFILE_NODE_COPYFROM_REVISION = "Node-copyfrom-rev";
    public static final String DUMPFILE_NODE_COPYFROM_PATH = "Node-copyfrom-path";
    public static final String DUMPFILE_TEXT_CONTENT_LENGTH = "Text-content-length";

    private static final int DUMPFILE_FORMAT_VERSION = 3;
*/
    
    private ISVNLogEntryHandler mySyncHandler;
//    private ISVNLoadHandler myLoadHandler;
    
    /**
     * Creates a new admin client.
     * 
     * @param authManager   an auth manager
     * @param options       an options driver
     */
    public SVNAdminClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(authManager, options);
    }

    /**
     * Creates a new admin client.
     * 
     * @param repositoryPool a repository pool 
     * @param options        an options driver
     */
    protected SVNAdminClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(repositoryPool, options);
    }

    /**
     * Sets a replication handler that will receive a log entry object 
     * per each replayed revision.
     * 
     * <p>
     * Log entries dispatched to the handler may not contain changed paths and 
     * committed log message until this features are implemented in future releases. 
     * 
     * @param handler a replay handler
     */
    public void setReplayHandler(ISVNLogEntryHandler handler) {
        mySyncHandler = handler;
    }

/*    public void setLoadHandler(ISVNLoadHandler handler) {
        myLoadHandler = handler;
    }
*/

    /**
     * Creates an FSFS-type repository.
     * 
     * This implementation uses {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory#createLocalRepository(File, String, boolean, boolean)}}.
     * <p>
     * If <code>uuid</code> is <span class="javakeyword">null</span> a new uuid will be generated, otherwise 
     * the specified will be used.
     * 
     * <p>
     * If <code>enableRevisionProperties</code> is <span class="javakeyword">true</span>, an empty 
     * pre-revprop-change hook will be placed into the repository /hooks subdir. This enables changes to 
     * revision properties of the newly created repository. 
     * 
     * <p>
     * If <code>force</code> is <span class="javakeyword">true</span> and <code>path</code> already 
     * exists, deletes that path and creates a repository in its place.
     *  
     * @param path                        a repository root dir path
     * @param uuid                        a repository uuid
     * @param enableRevisionProperties    enables/disables changes to revision properties
     * @param force                       forces operation to run
     * @return                            a local URL (file:///) of a newly created repository
     * @throws SVNException
     */
    public SVNURL doCreateRepository(File path, String uuid, boolean enableRevisionProperties, boolean force) throws SVNException {
        return SVNRepositoryFactory.createLocalRepository(path, uuid, enableRevisionProperties, force);
    }
    
    /**
     * Copies revision properties from the source repository that the destination one is synchronized with 
     * to the given revision of the destination repository itself.
     * 
     * <p>
     * This method is equivalent to the command 'copy-revprops' of the native Subversion <i>svnsync</i> utility. 
     * Note that the destination repository given as <code>toURL</code> must be synchronized with a source 
     * repository. Please, see {@link #doInitialize(SVNURL, SVNURL)}} how to initialize such a synchronization.  
     * 
     * @param  toURL          a url to the destination repository which must be synchronized
     *                        with another repository 
     * @param  revision       a particular revision of the source repository to copy revision properties
     *                        from 
     * @throws SVNException   
     */
    public void doCopyRevisionProperties(SVNURL toURL, long revision) throws SVNException {
        SVNRepository toRepos = createRepository(toURL, true);
        checkIfRepositoryIsAtRoot(toRepos, toURL);

        SVNException error = null;
        SVNException error2 = null;
        lock(toRepos);
        try {
            SessionInfo info = openSourceRepository(toRepos);
            if (revision > info.myLastMergedRevision) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot copy revprops for a revision that has not been synchronized yet");
                SVNErrorManager.error(err);
            }
            copyRevisionProperties(info.myRepository, toRepos, revision, false);
        } catch (SVNException svne) {
            error = svne;
        } finally {
            try {
                unlock(toRepos);
            } catch (SVNException svne) {
                error2 = svne;
            }
        }

        if (error != null) {
            throw error;
        } else if (error2 != null) {
            throw error2;
        }
    }

    /**
     * Initializes synchronization between source and target repositories.
     * 
     * <p>
     * This method is equivalent to the command 'initialize' ('init') of the native Subversion <i>svnsync</i> 
     * utility. Initialization places information of a source repository to a destination one (setting special 
     * revision properties in revision 0) as well as copies all revision props from revision 0 of the source 
     * repository to revision 0 of the destination one.   
     * 
     * @param  fromURL         a source repository url
     * @param  toURL           a destination repository url
     * @throws SVNException   
     */
    public void doInitialize(SVNURL fromURL, SVNURL toURL) throws SVNException {
        SVNRepository toRepos = createRepository(toURL, true);
        checkIfRepositoryIsAtRoot(toRepos, toURL);

        SVNException error = null;
        SVNException error2 = null;
        lock(toRepos);
        try {
            long latestRevision = toRepos.getLatestRevision();
            if (latestRevision != 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot initialize a repository with content in it");
                SVNErrorManager.error(err);
            }

            String fromURLProp = toRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL);
            if (fromURLProp != null) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Destination repository is already synchronizing from ''{0}''", fromURLProp);
                SVNErrorManager.error(err);
            }

            SVNRepository fromRepos = createRepository(fromURL, false);
            checkIfRepositoryIsAtRoot(fromRepos, fromURL);

            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL, fromURL.toDecodedString());
            String uuid = fromRepos.getRepositoryUUID(true);
            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.FROM_UUID, uuid);
            toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, "0");

            copyRevisionProperties(fromRepos, toRepos, 0, false);
        } catch (SVNException svne) {
            error = svne;
        } finally {
            try {
                unlock(toRepos);
            } catch (SVNException svne) {
                error2 = svne;
            }
        }

        if (error != null) {
            throw error;
        } else if (error2 != null) {
            throw error2;
        }
    }

    /**
     * Completely synchronizes two repositories.
     * 
     * <p>
     * This method initializes the destination repository and then copies all revision
     * changes (including revision properties) 
     * from the given source repository to the destination one. First it 
     * tries to use synchronization features similar to the native Subversion 
     * 'svnsync' capabilities. But if a server does not support 
     * <code>replay</code> functionality, SVNKit uses its own repository 
     * replication feature (see {@link org.tmatesoft.svn.core.replicator.SVNRepositoryReplicator}})
     * 
     * @param  fromURL        a url of a repository to copy from     
     * @param  toURL          a destination repository url
     * @throws SVNException
     */
    public void doCompleteSynchronize(SVNURL fromURL, SVNURL toURL) throws SVNException {
        try {
            doInitialize(fromURL, toURL);
            doSynchronize(toURL);
            return;
        } catch (SVNException svne) {
            if (svne.getErrorMessage().getErrorCode() != SVNErrorCode.RA_NOT_IMPLEMENTED) {
                throw svne;
            }
        }

        SVNRepositoryReplicator replicator = SVNRepositoryReplicator.newInstance();
        SVNRepository fromRepos = createRepository(fromURL, true);
        SVNRepository toRepos = createRepository(toURL, false);
        replicator.replicateRepository(fromRepos, toRepos, 1, -1);
    }

    /**
     * Synchronizes the repository at the given url.
     * 
     * <p>
     * Synchronization means copying revision changes and revision properties from the source 
     * repository (that the destination one is synchronized with) to the destination one starting at 
     * the last merged revision. This method is equivalent to the command 'synchronize' ('sync') of 
     * the native Subversion <i>svnsync</i> utility. 
     * 
     * @param  toURL          a destination repository url
     * @throws SVNException
     */
    public void doSynchronize(SVNURL toURL) throws SVNException {
        SVNRepository toRepos = createRepository(toURL, true);
        checkIfRepositoryIsAtRoot(toRepos, toURL);

        SVNException error = null;
        SVNException error2 = null;

        lock(toRepos);
        try {
            SessionInfo info = openSourceRepository(toRepos);
            SVNRepository fromRepos = info.myRepository;
            long lastMergedRevision = info.myLastMergedRevision;
            String currentlyCopying = toRepos.getRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING);
            long toLatestRevision = toRepos.getLatestRevision();

            if (currentlyCopying != null) {
                long copyingRev = Long.parseLong(currentlyCopying);
                if (copyingRev < lastMergedRevision || copyingRev > lastMergedRevision + 1 || (toLatestRevision != lastMergedRevision && toLatestRevision != copyingRev)) {
                    SVNErrorMessage err = SVNErrorMessage
                            .create(
                                    SVNErrorCode.IO_ERROR,
                                    "Revision being currently copied ({0,number,integer}), last merged revision ({1,number,integer}), and destination HEAD ({2,number,integer}) are inconsistent; have you committed to the destination without using svnsync?",
                                    new Long[] {
                                            new Long(copyingRev), new Long(lastMergedRevision), new Long(toLatestRevision)
                                    });
                    SVNErrorManager.error(err);
                } else if (copyingRev == toLatestRevision) {
                    if (copyingRev > lastMergedRevision) {
                        copyRevisionProperties(fromRepos, toRepos, toLatestRevision, true);
                        lastMergedRevision = copyingRev;
                    }
                    toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, SVNProperty.toString(lastMergedRevision));
                    toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, null);
                } 
            } else {
                if (toLatestRevision != lastMergedRevision) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Destination HEAD ({0,number,integer}) is not the last merged revision ({1,number,integer}); have you committed to the destination without using svnsync?", new Long[] {new Long(toLatestRevision), new Long(lastMergedRevision)});
                    SVNErrorManager.error(err);
                }
            }

            long fromLatestRevision = fromRepos.getLatestRevision();
            if (fromLatestRevision < lastMergedRevision) {
                return;
            }

            for (long currentRev = lastMergedRevision + 1; currentRev <= fromLatestRevision; currentRev++) {
                toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, SVNProperty.toString(currentRev));
                SVNSynchronizeEditor syncEditor = new SVNSynchronizeEditor(toRepos, mySyncHandler, currentRev - 1);
                ISVNEditor cancellableEditor = SVNCancellableEditor.newInstance(syncEditor, this, getDebugLog());
                fromRepos.replay(0, currentRev, true, cancellableEditor);
                cancellableEditor.closeEdit();
                if (syncEditor.getCommitInfo().getNewRevision() != currentRev) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Commit created rev {0,number,integer} but should have created {1,number,integer}", new Long[] {
                            new Long(syncEditor.getCommitInfo().getNewRevision()), new Long(currentRev)
                    });
                    SVNErrorManager.error(err);
                }
                copyRevisionProperties(fromRepos, toRepos, currentRev, true);
                toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION, SVNProperty.toString(currentRev));
                toRepos.setRevisionPropertyValue(0, SVNRevisionProperty.CURRENTLY_COPYING, null);
            }
        } catch (SVNException svne) {
            error = svne;
        } finally {
            try {
                unlock(toRepos);
            } catch (SVNException svne) {
                error2 = svne;
            }
        }

        if (error != null) {
            throw error;
        } else if (error2 != null) {
            throw error2;
        }
    }

/*    public void load(File repositoryRoot, InputStream dumpStream, boolean usePreCommitHook, boolean usePostCommitHook, String parentDir) throws SVNException {
        FSFS fsfs = openRepository(repositoryRoot);
        ISVNLoadHandler handler = getLoadHandler(usePreCommitHook, usePostCommitHook, parentDir);
        
        String line = null;
        StringBuffer buffer = new StringBuffer();
        try {
            line = SVNFileUtil.readLineFromStream(dumpStream, buffer);
            if (line == null) {
                generateIncompleteDataError();
            }
            
            //parse format
            if (!line.startsWith(DUMPFILE_MAGIC_HEADER + ":")) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header");
                SVNErrorManager.error(err);
            }
            try {
                int version = Integer.parseInt(line.substring(DUMPFILE_MAGIC_HEADER.length() + 1));
                if (version > DUMPFILE_FORMAT_VERSION) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Unsupported dumpfile version: {0,number,integer}", new Integer(version));
                    SVNErrorManager.error(err);
                }
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Malformed dumpfile header");
                SVNErrorManager.error(err);
            }
            

            while (true) {
                checkCancelled();

                boolean foundNode = false;
                
                //skip empty lines
                buffer.setLength(0);
                line = SVNFileUtil.readLineFromStream(dumpStream, buffer);
                if (line == null) {
                    if (buffer.length() > 0) {
                        generateIncompleteDataError();
                    } else {
                        break;
                    }
                } 
                
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }
                
                Map headers = readHeaderBlock(dumpStream, line);
                if (headers.containsKey(DUMPFILE_REVISION_NUMBER)) {
                    handler.closeRevision();
                    handler.openRevision(headers);
                } else if (headers.containsKey(DUMPFILE_NODE_PATH)) {
                    handler.openNode(headers);
                    foundNode = true;
                }
                
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe);
        }
    }
*/
    
/*    private ISVNLoadHandler getLoadHandler(boolean usePreCommitHook, boolean usePostCommitHook, String parentDir) {
        if (myLoadHandler == null) {
            myLoadHandler = new DefaultLoadHandler(false, false, parentDir);
        }
        return myLoadHandler;
    }
    
    private Map readHeaderBlock(InputStream dumpStream, String firstHeader) throws SVNException, IOException {
        Map headers = new HashMap();
        StringBuffer buffer = new StringBuffer();
        while (true) {
            String header = null;
            buffer.setLength(0);
            if (firstHeader != null) {
                header = firstHeader;
                firstHeader = null;
            } else {
                header = SVNFileUtil.readLineFromStream(dumpStream, buffer);
            }
            
            if (header == null && buffer.length() > 0) {
                generateIncompleteDataError();
            } else if (buffer.length() == 0) {
                break;
            }
            
            int colonInd = header.indexOf(':');
            if (colonInd == -1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Dump stream contains a malformed header (with no '':'') at ''{0}''", header.length() > 20 ? header.substring(0, 19) : header);
                SVNErrorManager.error(err);
            }
            
            String name = header.substring(0, colonInd);
            if (colonInd + 2 > header.length()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, "Dump stream contains a malformed header (with no value) at ''{0}''", header.length() > 20 ? header.substring(0, 19) : header);
                SVNErrorManager.error(err);
            }
            String value = header.substring(colonInd + 2);
            headers.put(name, value);
        }
        
        return headers;
    }
    
    private void generateIncompleteDataError() throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.INCOMPLETE_DATA, "Premature end of content data in dumpstream");
        SVNErrorManager.error(err);
    }
    
    private FSFS openRepository(File reposRootPath) throws SVNException {
        FSFS fsfs = new FSFS(reposRootPath);
        fsfs.open();
        return fsfs;
    }
*/
    
    private void copyRevisionProperties(SVNRepository fromRepository, SVNRepository toRepository, long revision, boolean sync) throws SVNException {
        Map existingRevProps = null;
        if (sync) {
            existingRevProps = toRepository.getRevisionProperties(revision, null);
        }
        
        boolean sawSyncProperties = false;
        Map revProps = fromRepository.getRevisionProperties(revision, null);
        for (Iterator propNames = revProps.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            String propValue = (String) revProps.get(propName);
            if (propName.startsWith("sync-")) {
                sawSyncProperties = true;
            } else {
                toRepository.setRevisionPropertyValue(revision, propName, propValue);
            }
            
            if (sync) {
                existingRevProps.remove(propName);
            }
        }
        
        if (sync) {
            for (Iterator propNames = existingRevProps.keySet().iterator(); propNames.hasNext();) {
                String propName = (String) propNames.next();
                toRepository.setRevisionPropertyValue(revision, propName, null);
            }            
        }
        
        if (sawSyncProperties) {
            SVNDebugLog.getDefaultLog().info("Copied properties for revision " + revision + " (sync-* properties skipped).\n");
        } else {
            SVNDebugLog.getDefaultLog().info("Copied properties for revision " + revision + ".\n");
        }
    }

    private SessionInfo openSourceRepository(SVNRepository targetRepos) throws SVNException {
        String fromURL = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_URL);
        String fromUUID = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.FROM_UUID);
        String lastMergedRev = targetRepos.getRevisionPropertyValue(0, SVNRevisionProperty.LAST_MERGED_REVISION);

        if (fromURL == null || fromUUID == null || lastMergedRev == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Destination repository has not been initialized");
            SVNErrorManager.error(err);
        }

        SVNURL srcURL = SVNURL.parseURIDecoded(fromURL);
        SVNRepository srcRepos = createRepository(srcURL, false);

        checkIfRepositoryIsAtRoot(srcRepos, srcURL);

        String reposUUID = srcRepos.getRepositoryUUID(true);
        if (!fromUUID.equals(reposUUID)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "UUID of destination repository ({0}) does not match expected UUID ({1})", new String[] {
                    reposUUID, fromUUID
            });
            SVNErrorManager.error(err);
        }

        return new SessionInfo(srcRepos, Long.parseLong(lastMergedRev));
    }

    private void checkIfRepositoryIsAtRoot(SVNRepository repos, SVNURL url) throws SVNException {
        SVNURL reposRoot = repos.getRepositoryRoot(true);
        if (!reposRoot.equals(url)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Session is rooted at ''{0}'' but the repos root is ''{1}''", new SVNURL[] {
                    url, reposRoot
            });
            SVNErrorManager.error(err);
        }
    }

    private void lock(SVNRepository repos) throws SVNException {
        String hostName = null;
        try {
            hostName = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can't get local hostname");
            SVNErrorManager.error(err, e);
        }

        if (hostName.length() > 256) {
            hostName = hostName.substring(0, 256);
        }

        String lockToken = hostName + ":" + SVNUUIDGenerator.formatUUID(SVNUUIDGenerator.generateUUID());
        int i = 0;
        for (i = 0; i < 10; i++) {
            String reposLockToken = repos.getRevisionPropertyValue(0, SVNRevisionProperty.LOCK);
            if (reposLockToken != null) {
                if (reposLockToken.equals(lockToken)) {
                    return;
                }
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    //
                }
            } else {
                repos.setRevisionPropertyValue(0, SVNRevisionProperty.LOCK, lockToken);
            }
        }

        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Couldn''t get lock on destination repos after {0,number,integer} attempts\n", new Integer(i));
        SVNErrorManager.error(err);
    }

    private void unlock(SVNRepository repos) throws SVNException {
        repos.setRevisionPropertyValue(0, SVNRevisionProperty.LOCK, null);
    }

    private class SessionInfo {

        SVNRepository myRepository;
        long myLastMergedRevision;

        public SessionInfo(SVNRepository repos, long lastMergedRev) {
            myRepository = repos;
            myLastMergedRevision = lastMergedRev;
        }
    }
}

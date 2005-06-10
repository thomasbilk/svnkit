/*
 * Created on 10.05.2005
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.cli.command.SVNCommandEventProcessor;
import org.tmatesoft.svn.cli.SVNCommandStatusHandler;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepositoryLocation;
import org.tmatesoft.svn.core.io.SVNSimpleCredentialsProvider;
import org.tmatesoft.svn.core.wc.ISVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.SVNStatusClient;
import org.tmatesoft.svn.core.wc.SVNOptions;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNCopyClient;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.TimeUtil;

public class SVNReporter implements ISVNReporterBaton {

    private SVNWCAccess myWCAccess;
    private boolean myIsRecursive;

    public SVNReporter(SVNWCAccess wcAccess, boolean recursive) {
        myWCAccess = wcAccess;
        myIsRecursive = recursive;
    }

    public void report(ISVNReporter reporter) throws SVNException {
        try {
            SVNEntries targetEntries = myWCAccess.getTarget().getEntries();
            SVNEntries anchorEntries = myWCAccess.getAnchor().getEntries();
            SVNEntry targetEntry = anchorEntries.getEntry(myWCAccess.getTargetName());

            if (targetEntry == null || targetEntry.isHidden() || 
                    (targetEntry.isDirectory() && targetEntry.isScheduledForAddition())) {
                DebugLog.log("deleted file reported");
                long revision = anchorEntries.getEntry("").getRevision();
                reporter.setPath("", null, revision, targetEntry != null ? targetEntry.isIncomplete() : true);
                reporter.deletePath("");
                reporter.finishReport();
                return;
            }
            long revision = targetEntry.isFile() ? targetEntry.getRevision() : 
                targetEntries.getEntry("").getRevision();
            if (revision < 0) {                
                revision = anchorEntries.getEntry("").getRevision();
            }
            reporter.setPath("", null, revision, targetEntry.isIncomplete());
            boolean missing = !targetEntry.isScheduledForDeletion() &&  
                !myWCAccess.getAnchor().getFile(myWCAccess.getTargetName(), false).exists();
            
            if (targetEntry.isDirectory()) {
                if (missing) {
                    reporter.deletePath("");
                } else {
                    DebugLog.log("reporting dir entries: " + myIsRecursive);
                    reportEntries(reporter, myWCAccess.getTarget(), "", targetEntry.isIncomplete(), myIsRecursive);
                }
            } else if (targetEntry.isFile()){
                if (missing) {
                    restoreFile(myWCAccess.getAnchor(), targetEntry.getName());
                }
                // report either linked path or entry path
                String url = targetEntry.getURL();
                SVNEntry parentEntry = targetEntries.getEntry("");
                String parentURL = parentEntry.getURL();
                String expectedURL = PathUtil.append(parentURL, PathUtil.encode(targetEntry.getName()));
                if (!expectedURL.equals(url)) {
                    reporter.linkPath(SVNRepositoryLocation.parseURL(url), "", targetEntry.getLockToken(), targetEntry.getRevision(), false);
                } else if (targetEntry.getRevision() != parentEntry.getRevision() || targetEntry.getLockToken() != null) {
                    reporter.setPath("", targetEntry.getLockToken(), targetEntry.getRevision(), false);
                }            
            }
            reporter.finishReport();
        } catch (Throwable th) {
            th.printStackTrace();
            DebugLog.error(th);
            reporter.abortReport();
            SVNErrorManager.error(0, th);
        }
    }
    
    private void reportEntries(ISVNReporter reporter, SVNDirectory directory, String dirPath, boolean reportAll, boolean recursive) throws SVNException {
        SVNEntries entries = directory.getEntries();
        long baseRevision = entries.getEntry("").getRevision();

        SVNExternalInfo[] externals = myWCAccess.addExternals(directory,
                directory.getProperties("", false).getPropertyValue(SVNProperty.EXTERNALS));
        for(int i = 0; externals != null && i < externals.length; i++) {
            externals[i].setOldExternal(externals[i].getNewURL(), externals[i].getNewRevision());
        }
        
        for (Iterator e = entries.entries(); e.hasNext();) {
            SVNEntry entry = (SVNEntry) e.next();
            if ("".equals(entry.getName())) {
                continue;
            }
            String path = "".equals(dirPath) ? entry.getName() : PathUtil.append(dirPath, entry.getName());
            if (entry.isDeleted() || entry.isAbsent()) {
                if (!reportAll) {
                    reporter.deletePath(path);
                }
                continue;
            }
            if (entry.isScheduledForAddition()) {
                continue;
            }        
            File file = directory.getFile(entry.getName(), false);
            boolean missing = !file.exists(); 
            if (entry.isFile()) {
                if (!reportAll) {
                    // check svn:special files -> symlinks that could be directory.
                    boolean special = SVNFileUtil.isWindows && 
                        directory.getProperties(entry.getName(), false).getPropertyValue(SVNProperty.SPECIAL) != null;
                    
                    if ((special && !file.exists()) || (!special && file.isDirectory())) {
                        reporter.deletePath(path);
                        continue;
                    }
                }
                if (missing && !entry.isScheduledForDeletion() && !entry.isScheduledForReplacement()) {
                    restoreFile(directory, entry.getName());
                }
                String url = entry.getURL();
                String parentURL = entries.getPropertyValue("", SVNProperty.URL);
                String expectedURL = PathUtil.append(parentURL, PathUtil.encode(entry.getName()));
                if (reportAll) {
                    if (!url.equals(expectedURL) && !entry.isScheduledForAddition() && !entry.isScheduledForReplacement()) {
                        reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, entry.getLockToken(), entry.getRevision(), false);
                    } else {
                        reporter.setPath(path, entry.getLockToken(), entry.getRevision(), false);
                    }
                } else if (!entry.isScheduledForReplacement() && !url.equals(expectedURL)) {
                    // link path
                    reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, entry.getLockToken(), entry.getRevision(), false);
                } else if (entry.getRevision() != baseRevision || entry.getLockToken() != null) {
                    reporter.setPath(path, entry.getLockToken(), entry.getRevision(), false);
                }
            } else if (entry.isDirectory() && recursive) {
                if (missing || directory.getChildDirectory(entry.getName()) == null) {
                    if (!reportAll) {
                        reporter.deletePath(path);
                    }
                    return;
                }
                if (file.isFile()) {
                    SVNErrorManager.error(3, null);
                }
                SVNDirectory childDir = directory.getChildDirectory(entry.getName());
                SVNEntry childEntry = childDir.getEntries().getEntry("");
                String url = childEntry.getURL();
                if (reportAll) {
                    if (!url.equals(entry.getURL())) {
                        reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.isIncomplete());
                    } else {
                        reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), childEntry.isIncomplete());
                    }
                } else if (!url.equals(entry.getURL())) {
                    reporter.linkPath(SVNRepositoryLocation.parseURL(url), path, childEntry.getLockToken(), childEntry.getRevision(), 
                            childEntry.isIncomplete());
                } else if (childEntry.getLockToken() != null || childEntry.getRevision() != baseRevision) {
                    reporter.setPath(path, childEntry.getLockToken(), childEntry.getRevision(), 
                            childEntry.isIncomplete());
                }
                reportEntries(reporter, childDir, path, childEntry.isIncomplete(), recursive);
            }
        }
    }
    
    private void restoreFile(SVNDirectory dir, String name) throws SVNException {
        SVNProperties props = dir.getProperties(name, false);
        SVNEntry entry = dir.getEntries().getEntry(name);
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        
        File src = dir.getBaseFile(name, false);
        File dst = dir.getFile(name, false);
        SVNTranslator.translate(dir, name, SVNFileUtil.getBasePath(src), SVNFileUtil.getBasePath(dst), true, true);
        dir.markResolved(name, true, false);
        
        boolean executable = props.getPropertyValue(SVNProperty.EXECUTABLE) != null;
        boolean needsLock = props.getPropertyValue(SVNProperty.NEEDS_LOCK) != null;
        if (executable) {
            SVNFileUtil.setExecutable(dst, true);
        }
        if (needsLock) {
            try {
                SVNFileUtil.setReadonly(dst, entry.getLockToken() == null);
            } catch (IOException e) {
                SVNErrorManager.error(0, e);
            }
        }
        long tstamp = dst.lastModified();
        if (myWCAccess.getOptions().isUseCommitTimes() && !special) {
            entry.setTextTime(entry.getCommittedDate());
            tstamp = TimeUtil.parseDate(entry.getCommittedDate()).getTime();
            dst.setLastModified(tstamp);
        } else {
            entry.setTextTime(TimeUtil.formatDate(new Date(tstamp)));
        }
        dir.getEntries().save(false);
        
        myWCAccess.svnEvent(SVNEventFactory.createRestoredEvent(myWCAccess, dir, entry));
    }
    
    public static void main(String[] args) {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        
        ISVNRepositoryFactory repositoryFactory = new ISVNRepositoryFactory() {
            public SVNRepository createRepository(String url) throws SVNException {
                SVNRepository repos = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(url));
                repos.setCredentialsProvider(new SVNSimpleCredentialsProvider("alex", "cvs"));
                return repos;
            }            
        };
        try {
            SVNStatusClient stClient = new SVNStatusClient(repositoryFactory, new SVNOptions(), new SVNCommandEventProcessor(System.out, System.err, false));
            SVNUpdateClient upClient = new SVNUpdateClient(repositoryFactory, new SVNOptions(), new SVNCommandEventProcessor(System.out, System.err, true));
            SVNCopyClient cpClient = new SVNCopyClient(repositoryFactory, new SVNOptions(), new SVNCommandEventProcessor(System.out, System.err, true));

            SVNWCClient wcClient = new SVNWCClient(repositoryFactory, new SVNOptions(), new SVNCommandEventProcessor(System.out, System.err, true));
            File revertDir = new File("C:\\i\\small\\new");
            File dst = new File("C:\\i\\test5\\branchesX");
            File dst2 = new File("C:\\i\\test5\\branchesY");
//            cpClient.doCopy(src, dst, false, false);
            wcClient.doRevert(revertDir, false);

//            SVNInfo info = wcClient.doInfo(dst, SVNRevision.WORKING);
            /*
            boolean verbose = true;
            boolean remote = false;
            boolean quiet = false;
            boolean ignored = false;
            boolean recusive = true;
            //ISVNStatusHandler handler = new SVNCommandStatusHandler(System.out, verbose || remote, verbose, quiet, remote);
            //stClient.doStatus(dst, recusive, remote, verbose, ignored, handler);
//            wcClient.doUnlock(new File[] {dst}, false);
            String url = "http://svn.osafoundation.org/chandler";
            SVNRepository repos = SVNRepositoryFactory.create(SVNRepositoryLocation.parseURL(url));
            repos.testConnection();
            System.out.println("repository root: " + repos.getRepositoryRoot());
            System.out.println("repository uuid: " + repos.getRepositoryUUID());
            upClient.doCheckout("http://svn.osafoundation.org/chandler", new File("C:\\i\\moved"), SVNRevision.HEAD, SVNRevision.HEAD, false);
            */
        } catch (Throwable e) {
            e.printStackTrace();
        } 
        
    }
}

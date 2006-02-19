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
package org.tmatesoft.svn.core.internal.io.fs.test;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Collection;
import java.util.Iterator;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.io.ISVNReporter;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.ISVNReporterBaton;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNWCUtil;
import org.tmatesoft.svn.core.wc.SVNWCClient;
import org.tmatesoft.svn.core.wc.ISVNPropertyHandler;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;



/**
 * Program arguments:
 * 
 * sourceRepositoryURL targetRepositoryURL sourceWCRoot targetWCRoot [topRevision]
 * 
 * sourceRepositoryURL - the url of the repository to copy from
 * targetRepositoryURL - the url of the repository to copy into 
 * sourceWCRoot        - the root dir path where revisions of the source
 *                       repository (sourceRepositoryURL) will be checked 
 *                       out 
 * targetWCRoot        - the root dir path where revisions of the target
 *                       repository (targetRepositoryURL) will be checked 
 *                       out
 * topRevision         - optional: used to restrict the range of revisions
 *                       (from 0 to topRevision)                       
 * 
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSRepositoryTest {
    
    public static void main(String[] args){
        if(args.length < 4){
            System.out.println("Expected input values: 1. source repository url. 2. destination repository url. 3. source working copy dir. 4. destination working copy dir. [5.] top revision to copy up to");
            SVNDebugLog.logInfo("Expected input values: 1. source repository url. 2. destination repository url. 3. source working copy dir. 4. destination working copy dir. [5.] top revision to copy up to");
            System.exit(1);
        }
        String sourceRepositoryUrl = args[0];
        String targetRepositoryUrl = args[1];
        String sourceWC = args[2];
        String targetWC = args[3];
        long topRev = args.length > 4 ? Long.parseLong(args[4]) : -1;
        boolean passed = false;
        FSRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        DAVRepositoryFactory.setup();
        try{
            SVNURL srcURL = SVNURL.parseURIDecoded(sourceRepositoryUrl);
            SVNURL dstURL = SVNURL.parseURIDecoded(targetRepositoryUrl);
            topRev = copyRepository(srcURL, dstURL, topRev);
            passed = compareRepositories1(srcURL, dstURL, topRev, new File(sourceWC), new File(targetWC));
        }catch(SVNException svne){
            SVNDebugLog.logInfo("Repositories comparing test FAILED with errors: " + svne.getErrorMessage().getMessage());
            System.out.println("Repositories comparing test FAILED with errors: " + svne.getErrorMessage().getMessage());
            System.out.println(svne.getErrorMessage().getMessage());
            System.exit(1);
        }
        if(passed){
            SVNDebugLog.logInfo("Repositories comparing test PASSED");
            System.out.println("Repositories comparing test PASSED");
        }else{
            SVNDebugLog.logInfo("Repositories comparing test FAILED");
            System.out.println("Repositories comparing test FAILED");
            System.exit(1);
        }
    }
    
    private static boolean compareRepositories1(SVNURL srcURL, SVNURL dstURL, long top, File srcWCRoot, File dstWCRoot) throws SVNException {
        SVNClientManager manager = SVNClientManager.newInstance();
        SVNUpdateClient updateClient = manager.getUpdateClient();
        for(long i = 1; i <= top; i++){
            SVNDebugLog.logInfo("Checking revision #" + i);
            System.out.println("Checking revision #" + i);
            if(!compareRevisionProps(srcURL, dstURL, i)){
                return false;
            }
            if(srcWCRoot.exists()){
                SVNFileUtil.deleteAll(srcWCRoot, true);
            }
            if(dstWCRoot.exists()){
                SVNFileUtil.deleteAll(dstWCRoot, true);
            }
            updateClient.doCheckout(srcURL, srcWCRoot, SVNRevision.create(i), SVNRevision.create(i), true);
            updateClient.doCheckout(dstURL, dstWCRoot, SVNRevision.create(i), SVNRevision.create(i), true);
            if(!compareDirs(srcWCRoot, dstWCRoot, true)){
                SVNDebugLog.logInfo("Unequal Working Copies at revision " + i);
                return false;
            }
        }
        return true;
    }
    
    private static boolean compareRevisionProps(SVNURL srcURL, SVNURL dstURL, long revision) throws SVNException {
        SVNRepository srcRep = SVNRepositoryFactory.create(srcURL);
        SVNRepository dstRep = SVNRepositoryFactory.create(dstURL);
        Map srcRevProps = new HashMap();
        Map dstRevProps = new HashMap();
        srcRep.getRevisionProperties(revision, srcRevProps);
        dstRep.getRevisionProperties(revision, dstRevProps);
        if(!compareProps(srcRevProps, dstRevProps)){
            SVNDebugLog.logInfo("Unequal revision props at revision " + revision);
            return false;
        }
        return true;
    }
    
    private static boolean compareProps(Map props1, Map props2){
        if(props1.size() != props2.size()){
            return false;
        }
        for(Iterator names = props1.keySet().iterator(); names.hasNext();){
            String propName = (String)names.next();
            String propValue = (String)props1.get(propName);
            String propValue2 = (String)props2.get(propName);
            if(propValue2 == null || !propValue2.equals(propValue)){
                return false;
            }
        }
        return true;
    }
    
    private static boolean compareDirs(File dir1, File dir2, boolean isRoot) throws SVNException {
        if(".svn".equals(dir1.getName())){
            return true;
        }
        if(!dir1.isDirectory()){
            SVNDebugLog.logInfo("'" + dir1 + "' is not a directory");
            return false;
        }
        if(!dir2.isDirectory()){
            SVNDebugLog.logInfo("'" + dir2 + "' is not a directory");
            return false;
        }
        SVNClientManager manager = SVNClientManager.newInstance();
        SVNWCClient wcClient = manager.getWCClient();
        if(!isRoot && !dir1.getName().equals(dir2.getName())){
            SVNDebugLog.logInfo("Unequal dir names: '" + dir1.getName() + "' vs. '" + dir2.getName() + "'");
            return false;
        }
        Map props1 = new HashMap();
        Map props2 = new HashMap();
        ISVNPropertyHandler handler1 = new PropertyHandler(props1);
        ISVNPropertyHandler handler2 = new PropertyHandler(props2);
        wcClient.doGetProperty(dir1, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler1);
        wcClient.doGetProperty(dir2, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler2);
        if(!compareProps(props1, props2)){
            SVNDebugLog.logInfo("Unequal dir props: '" + dir1 + "' vs. '" + dir2 + "'");
            return false;
        }
        File[] entries1 = dir1.listFiles();
        File[] entries2 = dir2.listFiles();
        if(entries1.length != entries2.length){
            SVNDebugLog.logInfo("Unequal dirs (different number of children): '" + dir1 + "' (found " + entries1.length + " children) vs. '" + dir2 + "' (found " + entries2.length + " children)");
            return false;
        }
        for(int i = 0; i < entries1.length; i++){
            File entry = entries1[i];
            if(".svn".equals(entry.getName())){
                continue;
            }
            File entryToCompareWith = null;
            for(int j = 0; j < entries2.length; j++){
                File candidateEntry = entries2[j];
                if(candidateEntry.getName().equals(entry.getName())){
                    entryToCompareWith = candidateEntry;
                    break;
                }
            }
            if(entryToCompareWith == null){
                SVNDebugLog.logInfo("Missing entry named '" + entry.getName() + "' in folder '" + dir2 + "'");
                return false;
            }
            if(entry.isDirectory()){
                if(!compareDirs(entry, entryToCompareWith, false)){
                    return false;
                }
            }else{
                if(!compareFiles(entry, entryToCompareWith)){
                    return false;
                }
            }
        }
        return true;
    }
    
    private static boolean compareFiles(File file1, File file2) throws SVNException {
        if(!file1.isFile()){
            SVNDebugLog.logInfo("'" + file1 + "' is not a file");
            return false;
        }
        if(!file2.isFile()){
            SVNDebugLog.logInfo("'" + file2 + "' is not a file");
            return false;
        }
        SVNClientManager manager = SVNClientManager.newInstance();
        SVNWCClient wcClient = manager.getWCClient();
        SVNInfo file1Info = wcClient.doInfo(file1, SVNRevision.WORKING);
        SVNInfo file2Info = wcClient.doInfo(file2, SVNRevision.WORKING);
        if(!file1.getName().equals(file2.getName())){
            SVNDebugLog.logInfo("Unequal file names: '" + file1.getName() + "' vs. '" + file2.getName() + "'");
            return false;
        }
        Map props1 = new HashMap();
        Map props2 = new HashMap();
        ISVNPropertyHandler handler1 = new PropertyHandler(props1);
        ISVNPropertyHandler handler2 = new PropertyHandler(props2);
        wcClient.doGetProperty(file1, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler1);
        wcClient.doGetProperty(file2, null, SVNRevision.HEAD, SVNRevision.WORKING, false, handler2);
        if(!compareProps(props1, props2)){
            SVNDebugLog.logInfo("Unequal file props: '" + file1 + "' vs. '" + file2 + "'");
            return false;
        }
        String checksum1 = file1Info.getChecksum();//SVNFileUtil.computeChecksum(file1);
        String checksum2 = file2Info.getChecksum();//SVNFileUtil.computeChecksum(file2);
        if(!checksum1.equals(checksum2)){
            SVNDebugLog.logInfo("Unequal file contents: '" + file1 + "' (" + checksum1 + ") vs. '" + file2 + "' (" + checksum2 + ")");
            return false;
        }
        return true;
    }
    
    private static long copyRepository(SVNURL srcURL, SVNURL dstURL, long top) throws SVNException {
        SVNRepository sourceRepos = SVNRepositoryFactory.create(srcURL);
        sourceRepos.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
        SVNRepository targetRepos = SVNRepositoryFactory.create(dstURL);
        targetRepos.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
        long latestRev = sourceRepos.getLatestRevision();
        latestRev = top > 0 && top <= latestRev? top : latestRev;
        ISVNEditor bridgeEditor = null;
        String commitMessage = "Copy repositories message #";
        Map copiedPaths = new HashMap();
        SVNDebugLog.logInfo("Started copying repository from '" + srcURL.toDecodedString() + "' to '" + dstURL.toDecodedString() + "', number of revisions to copy: " + latestRev);
        System.out.println("Started copying repository from '" + srcURL.toDecodedString() + "' to '" + dstURL.toDecodedString() + "', number of revisions to copy: " + latestRev);
        for(long i = 1; i <= latestRev; i++){
            SVNDebugLog.logInfo("Copying revision #" + i);
            System.out.println("Copying revision #" + i);
            //1. first investigate paths for copies if there're any 
            copiedPaths.clear();
            Collection logEntries = sourceRepos.log(null, null, i, i, true, false);
            ISVNEditor commitEditor = targetRepos.getCommitEditor(commitMessage + i, new CommitMediator());
            try{
                //open root
                commitEditor.openRoot(i);
                SVNLogEntry logEntry = (SVNLogEntry)logEntries.toArray()[0];
                Map changedPaths = logEntry.getChangedPaths();
                for(Iterator paths = changedPaths.keySet().iterator(); paths.hasNext();){
                    String path = (String)paths.next();
                    SVNLogEntryPath pathChange = (SVNLogEntryPath)changedPaths.get(path);
                    //make sure it's a copy
                    if(pathChange.getType() == 'A' && pathChange.getCopyPath() != null && pathChange.getCopyRevision() >= 0){
                        copiedPaths.put(path, pathChange);
                    }
                }
                //2. now run the update-commit
                bridgeEditor = new UpdatesToCommitsEditor(sourceRepos, changedPaths, copiedPaths, commitEditor, i);
                final long previousRev = i - 1;
                sourceRepos.update(i, null, true, new ISVNReporterBaton(){
                    public void report(ISVNReporter reporter) throws SVNException {
                        reporter.setPath("", null, previousRev, false);
                        reporter.finishReport();
                    }            
                }, bridgeEditor);
                /* for now we must have all explicitly deleted
                 * entries removed from changedPaths except those that
                 * were deleted from a locally copied dir (they're just not 
                 * sent by the server, we need to delete them for ourselves)
                 */
                for(Iterator paths = changedPaths.keySet().iterator(); paths.hasNext();){
                    String path = (String)paths.next();
                    SVNLogEntryPath pathChange = (SVNLogEntryPath)changedPaths.get(path);
                    //make sure it's a copy
                    if(pathChange.getType() == 'D'){
                        String[] entries = path.split("/");
                        String currentOpened = "";
                        for(int j = 0; j < entries.length - 1; j++){
                            currentOpened += entries[j];
                            commitEditor.openDir(entries[j], i);
                            currentOpened += "/";
                        }
                        commitEditor.deleteEntry(path, i);
                        for(int j = 0; j < entries.length - 1; j++){
                            commitEditor.closeDir();
                        }
                    }
                }
                //close root & finish commit
                commitEditor.closeDir();
                commitEditor.closeEdit();
            }catch(SVNException svne){
                commitEditor.abortEdit();
                throw svne;
            }
        }
        return latestRev;
    }
}

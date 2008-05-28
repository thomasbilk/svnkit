/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.wc;

import java.io.File;
import java.io.OutputStream;
import java.util.Collection;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNStatusType;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNRemoteDiffEditor implements ISVNEditor {

    private SVNRepository myRepos;
    private long myRevision1;
    private long myRevision2;
    private File myTarget;
    private SVNAdminArea myAdminArea;
    private boolean myIsDryRun;
    
    private SVNDeltaProcessor myDeltaProcessor;
    private ISVNEventHandler myEventHandler;
    private ISVNEventHandler myCancelHandler;
    private AbstractDiffCallback myDiffCallback;

    private SVNDirectoryInfo myCurrentDirectory;
    private SVNFileInfo myCurrentFile;
    private File myTempDirectory;
    private Collection myTempFiles;
    private Map myDeletedPaths;
    
    public SVNRemoteDiffEditor(SVNAdminArea adminArea, File target, AbstractDiffCallback callback,
                               SVNRepository repos, long revision1, long revision2, boolean dryRun, 
                               ISVNEventHandler handler, ISVNEventHandler cancelHandler) {
        myAdminArea = adminArea;
        myTarget = target;
        myDiffCallback = callback;
        myRepos = repos;
        myRevision1 = revision1;
        myRevision2 = revision2;
        myEventHandler = handler;
        myCancelHandler = cancelHandler;
        myDeltaProcessor = new SVNDeltaProcessor();
        myIsDryRun = dryRun;
        myDeletedPaths = new SVNHashMap();
    }

    public void reset(long revision1, long revision2) {
        myRevision1 = revision1;
        myRevision2 = revision2;
    }
    
    public void targetRevision(long revision) throws SVNException {
        myRevision2 = revision;
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(null, "", false);
        myCurrentDirectory.loadFromRepository(revision);
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        SVNStatusType type = SVNStatusType.INAPPLICABLE;
        SVNEventAction action = SVNEventAction.SKIP;
        SVNEventAction expectedAction = SVNEventAction.UPDATE_DELETE;
        
        SVNNodeKind nodeKind = myRepos.checkPath(path, myRevision1);
        SVNAdminArea dir = retrieve(myCurrentDirectory.myWCFile, true);
        
        if (myAdminArea == null || dir != null) {
            if (nodeKind == SVNNodeKind.FILE) {
                SVNFileInfo file = new SVNFileInfo(path, false);
                file.loadFromRepository(myRevision1);
                String baseType = file.myBaseProperties.getStringValue(SVNProperty.MIME_TYPE);
                type = getDiffCallback().fileDeleted(path, file.myBaseFile, null, baseType, null, file.myBaseProperties);
            } else if (nodeKind == SVNNodeKind.DIR) {
                type = getDiffCallback().directoryDeleted(path);
            }
            if (type != SVNStatusType.MISSING && type != SVNStatusType.OBSTRUCTED) {
                action = SVNEventAction.UPDATE_DELETE;
                if (myIsDryRun) {
                    getDiffCallback().addDeletedPath(path);
                }
            }
        }
        if (myEventHandler != null) {
            File deletedPath = new File(myTarget, path);
            KindActionState kas = new KindActionState();
            kas.myAction = action;
            kas.myKind = nodeKind;
            kas.myStatus = type;
            kas.myExpectedAction = expectedAction;
            myDeletedPaths.put(deletedPath, kas);
        }
    }

    public void addDir(String path, String copyFromPath, long copyFromRevision)  throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path, true);
        myCurrentDirectory.myBaseProperties = new SVNProperties();
        
        SVNEventAction expectedAction = SVNEventAction.UPDATE_ADD;
        SVNEventAction action = expectedAction;
        SVNStatusType type = getDiffCallback().directoryAdded(path, myRevision2);
        if (type == SVNStatusType.MISSING || type == SVNStatusType.OBSTRUCTED) {
            action = SVNEventAction.SKIP; 
        }
        
        if (myEventHandler != null) {
            boolean isReplace = false;
            KindActionState kas = (KindActionState) myDeletedPaths.get(myCurrentDirectory.myWCFile);
        	if (kas != null) {
        		SVNEventAction newAction = null;
        		if (kas.myAction == SVNEventAction.UPDATE_DELETE && action == SVNEventAction.UPDATE_ADD) {
        			isReplace = true;
        			newAction = SVNEventAction.UPDATE_REPLACE;
        		} else {
        			newAction = kas.myAction;
        		}
                SVNEvent event = SVNEventFactory.createSVNEvent(myCurrentDirectory.myWCFile, kas.myKind, null, 
                		SVNRepository.INVALID_REVISION, kas.myStatus, kas.myStatus, SVNStatusType.INAPPLICABLE, 
                		newAction, expectedAction, null, null);
                myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
                myDeletedPaths.remove(myCurrentDirectory.myWCFile);
        	
        	}
        	if (!isReplace) {
                // TODO prop type?
        		SVNEvent event = SVNEventFactory.createSVNEvent(myCurrentDirectory.myWCFile, SVNNodeKind.DIR, 
                		null, SVNRepository.INVALID_REVISION, type, type, null, action, expectedAction, null, null);
                myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        	}
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        myCurrentDirectory = new SVNDirectoryInfo(myCurrentDirectory, path, false);
        myCurrentDirectory.loadFromRepository(revision);
    }

    public void changeDirProperty(String name, SVNPropertyValue value) throws SVNException {
        myCurrentDirectory.myPropertyDiff.put(name, value);
    }

    public void closeDir() throws SVNException {
        SVNStatusType type = SVNStatusType.UNKNOWN;
        SVNEventAction expectedAction = SVNEventAction.UPDATE_UPDATE;
        SVNEventAction action = expectedAction;
        
        if (myIsDryRun) {
            getDiffCallback().clearDeletedPaths();
        }
        
        SVNAdminArea dir = null;
        if (!myCurrentDirectory.myPropertyDiff.isEmpty()) {
            try {
                dir = retrieve(myCurrentDirectory.myWCFile, myIsDryRun);
            } catch (SVNException e) {
                if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                    if (myEventHandler != null) {
                        action = SVNEventAction.SKIP;
                        SVNEvent event = SVNEventFactory.createSVNEvent(myCurrentDirectory.myWCFile, 
                        		SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNStatusType.MISSING, 
                        		SVNStatusType.MISSING, null, action, expectedAction, null, null);
                        myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
                    }
                    return;
                } 
                throw e;
            }
            if (!myIsDryRun || dir != null) {
                type = getDiffCallback().propertiesChanged(myCurrentDirectory.myRepositoryPath, 
                        myCurrentDirectory.myBaseProperties, myCurrentDirectory.myPropertyDiff);
            }
        }

        if (type == SVNStatusType.UNKNOWN) {
            action = SVNEventAction.UPDATE_NONE;
        }

        if (!myCurrentDirectory.myIsAdded && myEventHandler != null) {
            for (Iterator deletedPathsIter = myDeletedPaths.keySet().iterator(); deletedPathsIter.hasNext();) {
            	File deletedPath = (File) deletedPathsIter.next();
            	KindActionState kas = (KindActionState) myDeletedPaths.get(deletedPath);
                SVNEvent event = SVNEventFactory.createSVNEvent(deletedPath, kas.myKind, null, 
                		SVNRepository.INVALID_REVISION, kas.myStatus, kas.myStatus, SVNStatusType.INAPPLICABLE, 
                		kas.myAction, kas.myExpectedAction, null, null);
                myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
                deletedPathsIter.remove();
            }
            
            SVNEvent event = SVNEventFactory.createSVNEvent(myCurrentDirectory.myWCFile,
            		SVNNodeKind.DIR, null, SVNRepository.INVALID_REVISION, SVNStatusType.INAPPLICABLE, type, 
            		SVNStatusType.INAPPLICABLE, action, expectedAction, null, null);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
        myCurrentDirectory = myCurrentDirectory.myParent;
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        myCurrentFile = new SVNFileInfo(path, true);
        myCurrentFile.myBaseProperties = new SVNProperties();
        myCurrentFile.myBaseFile = SVNFileUtil.createUniqueFile(getTempDirectory(), ".diff", ".tmp");
        SVNFileUtil.createEmptyFile(myCurrentFile.myBaseFile);
    }

    public void openFile(String path, long revision) throws SVNException {
        myCurrentFile = new SVNFileInfo(path, false);
        myCurrentFile.loadFromRepository(revision);
    }

    public void changeFileProperty(String commitPath, String name, SVNPropertyValue value) throws SVNException {
        myCurrentFile.myPropertyDiff.put(name, value);
    }

    public void applyTextDelta(String commitPath, String baseChecksum) throws SVNException {
        SVNAdminArea dir = null;
        try {
            dir = retrieveParent(myCurrentFile.myWCFile, true);
        } catch (SVNException e) {
            dir = null;
        }
        myCurrentFile.myFile = createTempFile(dir, SVNPathUtil.tail(commitPath));
        myDeltaProcessor.applyTextDelta(myCurrentFile.myBaseFile, myCurrentFile.myFile, false);
    }

    public OutputStream textDeltaChunk(String commitPath, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }

    public void textDeltaEnd(String commitPath) throws SVNException {
        myDeltaProcessor.textDeltaEnd();
    }

    public void closeFile(String commitPath, String textChecksum) throws SVNException {
        SVNEventAction expectedAction = myCurrentFile.myIsAdded ? SVNEventAction.UPDATE_ADD : SVNEventAction.UPDATE_UPDATE;
        SVNEventAction action = expectedAction;
        SVNStatusType[] type = {SVNStatusType.UNKNOWN, SVNStatusType.UNKNOWN};
        try {
            retrieveParent(myCurrentFile.myWCFile, myIsDryRun);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.WC_NOT_LOCKED) {
                if (myEventHandler != null) {
                    action = SVNEventAction.SKIP;
                    SVNEvent event = SVNEventFactory.createSVNEvent(myCurrentFile.myWCFile, 
                    		SVNNodeKind.FILE, null, SVNRepository.INVALID_REVISION, SVNStatusType.MISSING, 
                    		SVNStatusType.UNKNOWN, null, action, expectedAction, null, null);
                    myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
                }
                return;
            } 
            throw e;
        }
        if (myCurrentFile.myFile != null || !myCurrentFile.myPropertyDiff.isEmpty()) {
            String baseMimeType = myCurrentFile.myBaseProperties.getStringValue(SVNProperty.MIME_TYPE);
            String mimeType = myCurrentFile.myPropertyDiff.getStringValue(SVNProperty.MIME_TYPE);
            if (myCurrentFile.myIsAdded) {
                type = getDiffCallback().fileAdded(commitPath, 
                        myCurrentFile.myFile != null ? myCurrentFile.myBaseFile : null, myCurrentFile.myFile, 
                        0, myRevision2, baseMimeType, mimeType, 
                        myCurrentFile.myBaseProperties, myCurrentFile.myPropertyDiff);
            } else {
                type = getDiffCallback().fileChanged(commitPath, 
                        myCurrentFile.myFile != null ? myCurrentFile.myBaseFile : null, myCurrentFile.myFile, 
                        myRevision1, myRevision2, baseMimeType, mimeType, 
                        myCurrentFile.myBaseProperties, myCurrentFile.myPropertyDiff);
            }
        }

    	if (type[0] == SVNStatusType.MISSING || type[0] == SVNStatusType.OBSTRUCTED) {
            action = SVNEventAction.SKIP;
        } else if (myCurrentFile.myIsAdded) {
            action = SVNEventAction.UPDATE_ADD;
        } else {
            action = SVNEventAction.UPDATE_UPDATE;
        }
        
        if (myEventHandler != null) {
            boolean isReplace = false;
        	KindActionState kas = (KindActionState) myDeletedPaths.get(myCurrentFile.myWCFile);
        	if (kas != null) {
        		SVNEventAction newAction = kas.myAction;
        		if (kas.myAction == SVNEventAction.UPDATE_DELETE && action == SVNEventAction.UPDATE_ADD) {
        			isReplace = true;
        			newAction = SVNEventAction.UPDATE_REPLACE;
        		}
                SVNEvent event = SVNEventFactory.createSVNEvent(myCurrentFile.myWCFile, kas.myKind, null, 
                		SVNRepository.INVALID_REVISION, kas.myStatus, kas.myStatus, SVNStatusType.INAPPLICABLE, 
                		newAction, expectedAction, null, null);
                myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
                myDeletedPaths.remove(myCurrentFile.myWCFile);
        	}
        
        	if (!isReplace) {
                SVNEvent event = SVNEventFactory.createSVNEvent(myCurrentFile.myWCFile, SVNNodeKind.FILE, 
                		null, SVNRepository.INVALID_REVISION, type[0], type[1], null, action, expectedAction, 
                		null, null);
                myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        	}
        }
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        return null;
    }

    public void abortEdit() throws SVNException {
    }

    public void absentDir(String path) throws SVNException {
        if (myCurrentDirectory.myWCFile != null) {
            File dir = new File(myCurrentDirectory.myWCFile, SVNPathUtil.tail(path));
            SVNEvent event = SVNEventFactory.createSVNEvent(dir, SVNNodeKind.DIR, 
                    null, SVNRepository.INVALID_REVISION, SVNStatusType.MISSING, SVNStatusType.MISSING, SVNStatusType.MISSING, SVNEventAction.SKIP, SVNEventAction.SKIP, 
                    null, null);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

    public void absentFile(String path) throws SVNException {
        if (myCurrentDirectory.myWCFile != null) {
            File file = new File(myCurrentDirectory.myWCFile, SVNPathUtil.tail(path));
            SVNEvent event = SVNEventFactory.createSVNEvent(file, SVNNodeKind.FILE, 
                    null, SVNRepository.INVALID_REVISION, SVNStatusType.MISSING, SVNStatusType.MISSING, SVNStatusType.MISSING, SVNEventAction.SKIP, SVNEventAction.SKIP, 
                    null, null);
            myEventHandler.handleEvent(event, ISVNEventHandler.UNKNOWN);
        }
    }

    public void cleanup() throws SVNException {
        if (myTempDirectory != null) {
            SVNFileUtil.deleteAll(myTempDirectory, true);
            myTempDirectory = null;
        }
        if (myTempFiles != null) {
            for(Iterator files = myTempFiles.iterator(); files.hasNext();) {
                SVNFileUtil.deleteFile((File) files.next());
            }
        }
    }
    
    protected SVNAdminArea retrieve(File path, boolean lenient) throws SVNException {
        if (myAdminArea == null) {
            return null;
        }
        try {
            return myAdminArea.getWCAccess().retrieve(path);
        } catch (SVNException e) {
            if (lenient) {
                return null;
            }
            throw e;
        }
    }

    protected SVNAdminArea retrieveParent(File path, boolean lenient) throws SVNException {
        if (myAdminArea == null) {
            return null;
        }
        return retrieve(path.getParentFile(), lenient);
    }
    
    protected AbstractDiffCallback getDiffCallback() {
        return myDiffCallback;
    }
    
    protected File getTempDirectory() throws SVNException {
        if (myTempDirectory == null) {
            myTempDirectory = getDiffCallback().createTempDirectory();
        }
        return myTempDirectory;
    }
    
    protected File createTempFile(SVNAdminArea dir, String name) throws SVNException {
        if (dir != null && dir.isLocked()) {
            File tmpFile = dir.getBaseFile(name, true);
            if (myTempFiles == null) {
                myTempFiles = new HashSet();
            }
            myTempFiles.add(tmpFile);
            return tmpFile;
        }
        return SVNFileUtil.createUniqueFile(getTempDirectory(), ".diff", ".tmp");
        
    }

    private class SVNDirectoryInfo {

        public SVNDirectoryInfo(SVNDirectoryInfo parent, String path, boolean added) {
            myParent = parent;
            myRepositoryPath = path;
            myWCFile = myTarget != null ? new File(myTarget, path) : null;
            myIsAdded = added;
            myPropertyDiff = new SVNProperties();
        }

        public void loadFromRepository(long baseRevision) throws SVNException {
            myBaseProperties = new SVNProperties();
            myRepos.getDir(myRepositoryPath, baseRevision, myBaseProperties, (ISVNDirEntryHandler) null);
        }

        private boolean myIsAdded;
        private String myRepositoryPath;
        private File myWCFile;
        
        private SVNProperties myBaseProperties;
        private SVNProperties myPropertyDiff;
        
        private SVNDirectoryInfo myParent;
    }

    private class SVNFileInfo {

        public SVNFileInfo(String path, boolean added) {
            myRepositoryPath = path;
            myIsAdded = added;
            myWCFile = myTarget != null ? new File(myTarget, path) : null;
            myPropertyDiff = new SVNProperties();
        }

        public void loadFromRepository(long revision) throws SVNException {
            myBaseFile = SVNFileUtil.createUniqueFile(getTempDirectory(), ".diff", ".tmp");
            OutputStream os = null;
            myBaseProperties = new SVNProperties();
            try {
                os = SVNFileUtil.openFileForWriting(myBaseFile);
                myRepos.getFile(myRepositoryPath, revision, myBaseProperties, new SVNCancellableOutputStream(os, myCancelHandler));
            } finally {
                SVNFileUtil.closeFile(os);
            }
        }

        private String myRepositoryPath;
        private File myWCFile;
        private boolean myIsAdded;
        
        private File myFile;
        private File myBaseFile;
        private SVNProperties myBaseProperties;
        private SVNProperties myPropertyDiff;
    }
    
    private class KindActionState {
    	private SVNNodeKind myKind;
    	private SVNEventAction myAction;
    	private SVNEventAction myExpectedAction;
    	private SVNStatusType myStatus;
    }
}

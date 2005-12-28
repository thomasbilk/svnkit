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

package org.tmatesoft.svn.core.internal.io.dav;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVMergeHandler;
import org.tmatesoft.svn.core.internal.io.dav.handlers.DAVProppatchHandler;
import org.tmatesoft.svn.core.internal.io.dav.http.HTTPStatus;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindowBuilder;

class DAVCommitEditor implements ISVNEditor {
    
    private String myLogMessage;
    private DAVConnection myConnection;
    private SVNURL myLocation;
	private DAVRepository myRepository;
    private Runnable myCloseCallback;
    private String myActivity;

    private Stack myDirsStack;
    private ISVNWorkspaceMediator myCommitMediator;
    private Map myPathsMap;
    private Map myFilesMap;

    public DAVCommitEditor(DAVRepository repository, DAVConnection connection, String message, ISVNWorkspaceMediator mediator, Runnable closeCallback) {
        myConnection = connection;
        myLogMessage = message;
        myLocation = repository.getLocation();
        myRepository = repository;
        myCloseCallback = closeCallback;
        myCommitMediator = mediator;

        myDirsStack = new Stack();
        myPathsMap = new HashMap();
        myFilesMap = new HashMap();
    }

    /* do nothing */
    public void targetRevision(long revision) throws SVNException {
    }
    public void absentDir(String path) throws SVNException {
    }
    public void absentFile(String path) throws SVNException {
    }

    public void openRoot(long revision) throws SVNException {
        // make activity
        myActivity = createActivity(myLogMessage);
        DAVResource root = new DAVResource(myCommitMediator, myConnection, "", revision);
        root.fetchVersionURL(false);
        myDirsStack.push(root);
        myPathsMap.put(root.getURL(), root.getPath());
    }

    public void deleteEntry(String path, long revision) throws SVNException {
        path = SVNEncodingUtil.uriEncode(path);
        // get parent's working copy. (checkout? or use checked out?)
        DAVResource parentResource = (DAVResource) myDirsStack.peek();
        checkoutResource(parentResource, true);
        String wPath = parentResource.getWorkingURL();
		// get root wURL and delete from it!

        // append name part of the path to the checked out location
		// should we append full name here?
        String url;
		if (myDirsStack.size() == 1) {
			wPath = SVNPathUtil.append(parentResource.getWorkingURL(), path);
            url = SVNPathUtil.append(parentResource.getURL(), path);
		} else {
			// we are inside openDir()...
			wPath = SVNPathUtil.append(wPath, SVNPathUtil.tail(path));
            url = SVNPathUtil.append(parentResource.getURL(), SVNPathUtil.tail(path));
		}
        // call DELETE for the composed path
        myConnection.doDelete(url, wPath, revision);
		if (myDirsStack.size() == 1) {
			myPathsMap.put(SVNPathUtil.append(parentResource.getURL(), path), path);
		} else {
			myPathsMap.put(SVNPathUtil.append(parentResource.getURL(), SVNPathUtil.tail(path)), path);
		}
    }


    public void addDir(String path, String copyPath, long copyRevision) throws SVNException {
        path = SVNEncodingUtil.uriEncode(path);

        DAVResource parentResource = (DAVResource) myDirsStack.peek();
        checkoutResource(parentResource, true);
        String wPath = parentResource.getWorkingURL();

        DAVResource newDir = new DAVResource(myCommitMediator, myConnection, path, -1, copyPath != null);
        newDir.setWorkingURL(SVNPathUtil.append(wPath, SVNPathUtil.tail(path)));

        myDirsStack.push(newDir);
        myPathsMap.put(newDir.getURL(), path);
        if (copyPath != null) {
            // convert to full path?
            copyPath = myRepository.getFullPath(copyPath);
            copyPath = SVNEncodingUtil.uriEncode(copyPath);
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, myRepository, copyPath, copyRevision, false, false, null);
            copyPath = SVNPathUtil.append(info.baselineBase, info.baselinePath);

            // full url.
            wPath = myLocation.getProtocol() + "://" + myLocation.getHost() + ":" + myLocation.getPort() +
            	newDir.getWorkingURL();
            myConnection.doCopy(copyPath, wPath, 1);
        } else {
            myConnection.doMakeCollection(newDir.getWorkingURL());
        }
    }

    public void openDir(String path, long revision) throws SVNException {
        path = SVNEncodingUtil.uriEncode(path);
        // do nothing,
        DAVResource parent = myDirsStack.peek() != null ? (DAVResource) myDirsStack.peek() : null;
        DAVResource directory = new DAVResource(myCommitMediator, myConnection, path, revision, parent == null ? false : parent.isCopy());
        if (parent != null && parent.isCopy()) {
            // part of copied structure -> derive wurl
            directory.setWorkingURL(SVNPathUtil.append(parent.getWorkingURL(), SVNPathUtil.tail(path)));
        } else {
            directory.fetchVersionURL(false);
        }
        myDirsStack.push(directory);
        myPathsMap.put(directory.getURL(), directory.getPath());
    }

    public void changeDirProperty(String name, String value) throws SVNException {
        DAVResource directory = (DAVResource) myDirsStack.peek();
        checkoutResource(directory, true);
        directory.putProperty(name, value);
        myPathsMap.put(directory.getURL(), directory.getPath());
    }

    public void closeDir() throws SVNException {
        DAVResource resource = (DAVResource) myDirsStack.pop();
        // do proppatch if there were property changes.
        if (resource.getProperties() != null) {
            StringBuffer request = DAVProppatchHandler.generatePropertyRequest(null, resource.getProperties());
            myConnection.doProppatch(resource.getURL(), resource.getWorkingURL(), request, null);
        }
        resource.dispose();
    }

    public void addFile(String path, String copyPath, long copyRevision) throws SVNException {
        String originalPath = path;
        path = SVNEncodingUtil.uriEncode(path);
        // checkout parent collection.
        DAVResource parentResource = (DAVResource) myDirsStack.peek();
        if (parentResource.getWorkingURL() == null) {
        	String filePath = SVNPathUtil.append(parentResource.getURL(), SVNPathUtil.tail(path));
            SVNErrorMessage err = null;
            try {
                DAVUtil.getResourceProperties(myConnection, filePath, null, DAVElement.STARTING_PROPERTIES);
            } catch (SVNException e) {
                if (e.getErrorMessage() == null) {
                    throw e;
                }
                err = e.getErrorMessage();
            }
            if (err == null) {
                err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_ALREADY_EXISTS, "File ''{0}'' already exists", filePath);
                SVNErrorManager.error(err);
            } else if (err.getErrorCode() != SVNErrorCode.RA_DAV_PATH_NOT_FOUND) {
                SVNErrorManager.error(err);
            } 
        }
        checkoutResource(parentResource, true);
        String wPath = parentResource.getWorkingURL();
        // create child resource.
        DAVResource newFile = new DAVResource(myCommitMediator, myConnection, path, -1, copyPath != null);
        newFile.setWorkingURL(SVNPathUtil.append(wPath, SVNPathUtil.tail(path)));
        // put to have working URL to make PUT or PROPPATCH later (in closeFile())
        myPathsMap.put(newFile.getURL(), newFile.getPath());
        myFilesMap.put(originalPath, newFile);

        if (copyPath != null) {
            copyPath = myRepository.getFullPath(copyPath);
            copyPath = SVNEncodingUtil.uriEncode(copyPath);
            DAVBaselineInfo info = DAVUtil.getBaselineInfo(myConnection, myRepository, copyPath, copyRevision, false, false, null);
            copyPath = SVNPathUtil.append(info.baselineBase, info.baselinePath);

            // do "COPY" copyPath to parents working url ?
            wPath = myLocation.getProtocol() + "://" + myLocation.getHost() + ":" + myLocation.getPort() +
            	newFile.getWorkingURL();
            myConnection.doCopy(copyPath, wPath, 0);
            newFile.setAdded(false);
        } else {
            newFile.setAdded(true);
        }
    }

    public void openFile(String path, long revision) throws SVNException {
        String originalPath = path;
        path = SVNEncodingUtil.uriEncode(path);
        DAVResource file = new DAVResource(myCommitMediator, myConnection, path, revision);
        DAVResource parent = (DAVResource) myDirsStack.peek();
        if (parent.isCopy()) {
            // part of copied structure -> derive wurl
            file.setWorkingURL(SVNPathUtil.append(parent.getWorkingURL(), SVNPathUtil.tail(path)));
        }
        checkoutResource(file, true);
        myPathsMap.put(file.getURL(), file.getPath());
        myFilesMap.put(originalPath, file);
    }
    
    public void applyTextDelta(String path, String baseChecksum) throws SVNException {
        myCurrentDelta = null;
        myRealDeltaStream = null;
    }
    
    private OutputStream myCurrentDelta = null;
    private OutputStream myRealDeltaStream = null;
    
    public OutputStream textDeltaChunk(String path, SVNDiffWindow diffWindow) throws SVNException {
        // save window, create temp file.
        DAVResource currentFile = (DAVResource) myFilesMap.get(path);
        try {
            boolean firstWindow = myCurrentDelta == null;
            myCurrentDelta = myCurrentDelta == null ? currentFile.addTextDelta() : myCurrentDelta;
            if (firstWindow) {
                myRealDeltaStream = myCurrentDelta;
                myCurrentDelta = new FilterOutputStream(myCurrentDelta) {
                    public void close() throws IOException {
                        // do not close this stream, will close on txtdelta-end
                    }
                    public void write(byte[] b, int off, int len) throws IOException {
                        super.out.write(b, off, len);
                    }
                    
                };
            }
            SVNDiffWindowBuilder.save(diffWindow, firstWindow, myCurrentDelta);
            return myCurrentDelta;
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
            SVNErrorManager.error(err, e);
            return null;
        }
    }
    public void textDeltaEnd(String path) throws SVNException {
        SVNFileUtil.closeFile(myRealDeltaStream);
    }

    public void changeFileProperty(String path, String name, String value)  throws SVNException {
        DAVResource currentFile = (DAVResource) myFilesMap.get(path);
        currentFile.putProperty(name, value);
    }

    public void closeFile(String path, String textChecksum) throws SVNException {
        // do PUT of delta if there was one (diff window + temp file).
        // do subsequent PUT of all diff windows...
        DAVResource currentFile = (DAVResource) myFilesMap.get(path);
        try {
            if (currentFile.isAdded() && currentFile.getDeltaCount() == 0) {
                OutputStream os = textDeltaChunk(path, SVNDiffWindowBuilder.createReplacementDiffWindow(0));
                try {
                    os.close();
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getMessage());
                    SVNErrorManager.error(err, e);
                }
                
            } else if (currentFile.getDeltaCount() > 0){
                InputStream combinedData = null;
                try {
                    combinedData = currentFile.getTextDelta(0);
                    myConnection.doPutDiff(currentFile.getURL(), currentFile.getWorkingURL(), combinedData);
                } catch (IOException e1) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e1.getLocalizedMessage());
                    SVNErrorManager.error(err, e1);
                } finally {
                    SVNFileUtil.closeFile(combinedData);
                }
            }
            // do proppatch if there were property changes.
            if (currentFile.getProperties() != null) {
                StringBuffer request = DAVProppatchHandler.generatePropertyRequest(null, currentFile.getProperties());
                myConnection.doProppatch(currentFile.getURL(), currentFile.getWorkingURL(), request, null);
            }
        } finally {
            currentFile.dispose();
            myCurrentDelta = null;
            myRealDeltaStream = null;
            myFilesMap.remove(path);
        }
    }
    
    public SVNCommitInfo closeEdit() throws SVNException {
        if (!myDirsStack.isEmpty()) {
            DAVResource resource = (DAVResource) myDirsStack.pop();
            // do proppatch if there were property changes.
            if (resource.getProperties() != null) {
                StringBuffer request = DAVProppatchHandler.generatePropertyRequest(null, resource.getProperties());
                myConnection.doProppatch(resource.getURL(), resource.getWorkingURL(), request, null);
            }
            resource.dispose();
        }
        DAVMergeHandler handler = new DAVMergeHandler(myCommitMediator, myPathsMap);
        HTTPStatus status = myConnection.doMerge(myActivity, true, handler);
        abortEdit();
        if (status.getError() != null) {
            SVNErrorManager.error(status.getError());
        }
        return handler.getCommitInfo();
    }
    
    public void abortEdit() throws SVNException {
        // DELETE activity
        if (myActivity != null) {
            myConnection.doDelete(myActivity);
        }
        // dispose all resources!
        if (myFilesMap != null) {
            for (Iterator files = myFilesMap.values().iterator(); files.hasNext();) {
                DAVResource file = (DAVResource) files.next();
                file.dispose();
            }
            myFilesMap = null;
        }
        for(Iterator files = myDirsStack.iterator(); files.hasNext();) {
            DAVResource resource = (DAVResource) files.next();
            resource.dispose();            
        }
        myDirsStack = null;
        myCloseCallback.run();
    }
    
    private String createActivity(String logMessage) throws SVNException {
        String activity = myConnection.doMakeActivity();
        // checkout head...
        String path = SVNEncodingUtil.uriEncode(myLocation.getPath());
        String vcc = DAVUtil.getPropertyValue(myConnection, path, null, DAVElement.VERSION_CONTROLLED_CONFIGURATION);
        
        // TODO implement retry line in native subversion.
        String head = DAVUtil.getPropertyValue(myConnection, vcc, null, DAVElement.CHECKED_IN);
        HTTPStatus status = myConnection.doCheckout(activity, null, head, false);
        String location = (String) status.getHeader().get("Location");
        if (location == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "The CHECKOUT response did not contain a 'Location:' header");
            SVNErrorManager.error(err);
        }
        // proppatch log message.
        logMessage = logMessage == null ? "" : logMessage;
        StringBuffer request = DAVProppatchHandler.generatePropertyRequest(null, SVNRevisionProperty.LOG, logMessage);
        try {
            myConnection.doProppatch(null, location, request, null);
        } catch (SVNException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "applying log message to {0}", path);
            SVNErrorManager.error(err);
        }
        return activity;
    }
    
    private void checkoutResource(DAVResource resource, boolean allow404) throws SVNException {
        if (resource.getWorkingURL() != null) {
            return;
        }
        HTTPStatus status = myConnection.doCheckout(myActivity, resource.getURL(), resource.getVersionURL(), allow404);
        if (allow404 && status.getCode() == 404) {
            resource.fetchVersionURL(true);
            status = myConnection.doCheckout(myActivity, resource.getURL(), resource.getVersionURL(), false);
        }
        String location = (String) status.getHeader().get("Location");
        if (location == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "The CHECKOUT response did not contain a 'Location:' header");
            SVNErrorManager.error(err);
        }
        resource.setWorkingURL(location);
    }
}

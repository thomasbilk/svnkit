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

package org.tmatesoft.svn.core.internal.io.svn;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;

import org.tmatesoft.svn.core.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.diff.SVNDiffWindowBuilder;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.io.SVNCommitInfo;
import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;
import org.tmatesoft.svn.util.PathUtil;

/**
 * @author TMate Software Ltd.
 */
class SVNCommitEditor implements ISVNEditor {
    
    private ISVNWorkspaceMediator myMediator;
    private SVNConnection myConnection;
    private SVNRepositoryImpl myRepository;
    
    private String myCurrentPath;
    private Runnable myCloseCallback;
    
    public SVNCommitEditor(SVNRepositoryImpl location, SVNConnection connection, ISVNWorkspaceMediator mediator,
            Runnable closeCallback) {
        myRepository = location;
        myConnection = connection;
        myMediator = mediator;
        myCloseCallback = closeCallback;
    }

    /* do nothing */
    public void targetRevision(long revision) throws SVNException {
    }
    public void absentDir(String path) throws SVNException {
    }
    public void absentFile(String path) throws SVNException {
    }

    public void openRoot(long revision) throws SVNException {
        myCurrentPath = "/";
        myConnection.write("(w((n)s))", new Object[] {"open-root", getRevisionObject(revision), "/"});
    }
    public void deleteEntry(String path, long revision) throws SVNException {
        myConnection.write("(w(s(n)s))", new Object[] {"delete-entry", path, getRevisionObject(revision), myCurrentPath});
    }
    
    public void addDir(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        if (copyFromPath != null) {
            copyFromPath = PathUtil.append(myRepository.getLocation().toString(), copyFromPath);
            myConnection.write("(w(sss(sn)))", new Object[] {"add-dir", path, myCurrentPath, path, copyFromPath, getRevisionObject(copyFromRevision)});
        } else {
            myConnection.write("(w(sss()))", new Object[] {"add-dir", path, myCurrentPath, path});
        }
        myCurrentPath = path;
    }
    public void openDir(String path, long revision) throws SVNException {
        myCurrentPath = path;
        myConnection.write("(w(sss(n)))", new Object[] {"open-dir", path, computeParentPath(path), path, getRevisionObject(revision)});
    }
    public void changeDirProperty(String name, String value)  throws SVNException {
        myConnection.write("(w(ss(s)))", new Object[] {"change-dir-prop", myCurrentPath, name, value});
    }
    public void closeDir() throws SVNException {
        myConnection.write("(w(s))", new Object[] {"close-dir", myCurrentPath});
        myCurrentPath = computeParentPath(myCurrentPath);
    }

    public void addFile(String path, String copyFromPath, long copyFromRevision) throws SVNException {
        if (copyFromPath != null) {
            copyFromPath = PathUtil.append(myRepository.getLocation().toString(), copyFromPath);
            myConnection.write("(w(sss(sn)))", new Object[] {"add-file", path, myCurrentPath, path, copyFromPath, getRevisionObject(copyFromRevision)});
        } else {
            myConnection.write("(w(sss()))", new Object[] {"add-file", path, myCurrentPath, path});
        }
        myCurrentPath = path;
    }
    public void openFile(String path, long revision) throws SVNException {
        myCurrentPath = path;
        myConnection.write("(w(sss(n)))", new Object[] {"open-file", path, computeParentPath(path), path, getRevisionObject(revision)});
    }

    public void applyTextDelta(String baseChecksum) throws SVNException {
        myConnection.write("(w(s(s)))", new Object[] {"apply-textdelta", myCurrentPath, baseChecksum});
    }
    public OutputStream textDeltaChunk(SVNDiffWindow diffWindow) throws SVNException {
        myConnection.write("(w(s", new Object[] {"textdelta-chunk", myCurrentPath});
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            SVNDiffWindowBuilder.save(diffWindow, bos);
            myConnection.write("b))", new Object[] {bos.toByteArray()});
            DebugLog.log(myCurrentPath + " window sent: " + bos.size());
            OutputStream os = myMediator.createTemporaryLocation(myCurrentPath);
            return os;
        } catch (IOException e) {
            throw new SVNException(e);
        }
    }
    public void textDeltaEnd() throws SVNException {
        InputStream is;
        try {
            DebugLog.log("closing delta for " + myCurrentPath);
            long length = myMediator.getLength(myCurrentPath);
            if (myMediator.getLength(myCurrentPath) > 0) {
                is = myMediator.getTemporaryLocation(myCurrentPath);
                // create
                SVNDataSource source = new SVNDataSource(is, length);
                myConnection.write("(w(si))", new Object[] {"textdelta-chunk", myCurrentPath, source});
                is.close();
            }
            DebugLog.log("new data sent" + length);
        } catch (IOException e) {
            throw new SVNException();
        } finally {
            myMediator.deleteTemporaryLocation(myCurrentPath);
        }
        myConnection.write("(w(s))", new Object[] {"textdelta-end", myCurrentPath});
    }

    public void changeFileProperty(String name, String value) throws SVNException {
        myConnection.write("(w(ss(s)))", new Object[] {"change-file-prop", myCurrentPath, name, value});
    }
    public void closeFile(String textChecksum) throws SVNException {
        myConnection.write("(w(s(s)))", new Object[] {"close-file", myCurrentPath, textChecksum});
        myCurrentPath = computeParentPath(myCurrentPath);
    }

    public SVNCommitInfo closeEdit() throws SVNException {
        myConnection.write("(w())", new Object[] {"close-edit"});

        myConnection.read("[()]", null);
        myRepository.authenticate();
        
        Object[] items = myConnection.read("(N(?S)(?S))", new Object[3]);
        long revision = SVNReader.getLong(items, 0);
        Date date = SVNReader.getDate(items, 1);

        myCloseCallback.run();
        return new SVNCommitInfo(revision, (String) items[2], date);
    }
    
    public void abortEdit() throws SVNException {
        myConnection.write("(w())", new Object[] {"abort-edit"});
        myCloseCallback.run();
    }
    
    private static String computeParentPath(String path) {
        return PathUtil.removeTail(path); 
    }
    
    private static Long getRevisionObject(long rev) {
        return rev >= 0 ? new Long(rev) : null;
    }
}

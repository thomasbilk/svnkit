package org.tmatesoft.svn.core.internal.wc;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.io.ISVNWorkspaceMediator;
import org.tmatesoft.svn.core.wc.SVNCommitItem;
import org.tmatesoft.svn.util.PathUtil;
import org.tmatesoft.svn.util.DebugLog;

import java.io.OutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: alex
 * Date: 16.06.2005
 * Time: 5:16:55
 * To change this template use File | Settings | File Templates.
 */
public class SVNCommitMediator implements ISVNWorkspaceMediator {

    private Collection myTmpFiles;
    private Map myTmpFilesMap;
    private Map myWCPropsMap;
    private SVNWCAccess myWCAccess;
    private Map myCommitItems;

    public SVNCommitMediator(SVNWCAccess wcAccess, Map commitItems) {
        myTmpFiles = new ArrayList();
        myTmpFilesMap = new HashMap();
        myWCPropsMap = new HashMap();
        myWCAccess = wcAccess;
        myCommitItems = commitItems;
    }

    public Map getWCProperties(SVNCommitItem item) {
        return (Map) myWCPropsMap.get(item);
    }

    public Collection getTmpFiles() {
        return myTmpFiles;
    }

    public String getWorkspaceProperty(String path, String name) throws SVNException {
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
        if (item == null) {
            return null;
        }
        SVNDirectory dir;
        String target;
        if (item.getKind() == SVNNodeKind.DIR) {
            dir = myWCAccess.getDirectory(item.getPath());
            target = "";
        } else {
            dir = myWCAccess.getDirectory(PathUtil.removeTail(item.getPath()));
            target = PathUtil.tail(item.getPath());
        }
        SVNProperties wcProps = dir.getWCProperties(target);
        return wcProps.getPropertyValue(name);
    }

    public void setWorkspaceProperty(String path, String name, String value) throws SVNException {
        if (name == null) {
            return;
        }
        DebugLog.log("setting wc props for path: " + path);
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
        DebugLog.log("item: " + item);
        if (!myWCPropsMap.containsKey(item)) {
            myWCPropsMap.put(item, new HashMap());
        }

        ((Map) myWCPropsMap.get(item)).put(name, value);
    }

    public OutputStream createTemporaryLocation(String path, Object id) throws IOException {
        SVNCommitItem item = (SVNCommitItem) myCommitItems.get(path);
        SVNDirectory dir;
        String target;
        if (item.getKind() == SVNNodeKind.DIR) {
            dir = myWCAccess.getDirectory(item.getPath());
            target = "";
        } else {
            dir = myWCAccess.getDirectory(PathUtil.removeTail(item.getPath()));
            target = PathUtil.tail(item.getPath());
        }
        File tmpFile = dir.getFile(".svn/tmp/text-base", false);
        tmpFile = SVNFileUtil.createUniqueFile(tmpFile, target, ".tmp");
        myTmpFiles.add(tmpFile);
        myTmpFilesMap.put(id, tmpFile);
        try {
            return SVNFileUtil.openFileForWriting(tmpFile);
        } catch (SVNException e) {
            throw new IOException(e.getMessage());
        }
    }

    public InputStream getTemporaryLocation(Object id) throws IOException {
        File file = (File) myTmpFilesMap.get(id);
        try {
            return SVNFileUtil.openFileForReading(file);
        } catch (SVNException e) {
            throw new IOException(e.getMessage());
        }
    }

    public long getLength(Object id) throws IOException {
        File file = (File) myTmpFilesMap.get(id);
        if (file != null) {
            return file.length();
        }
        return 0;
    }

    public void deleteTemporaryLocation(Object id) {
        File file = (File) myTmpFilesMap.remove(id);
        if (file != null) {
            file.delete();
            myTmpFiles.remove(file);
        }
    }

    public void deleteAdminFiles(String path) {
    }
}

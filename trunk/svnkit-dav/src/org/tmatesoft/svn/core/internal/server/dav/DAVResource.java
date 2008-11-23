/*
 * ====================================================================
 * Copyright (c) 2004-2008 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.server.dav;

import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSRevisionNode;
import org.tmatesoft.svn.core.internal.io.fs.FSRoot;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @author TMate Software Ltd.
 * @version 1.2.0
 */
public class DAVResource {

    public static final long INVALID_REVISION = SVNRepository.INVALID_REVISION;

    public static final String DEFAULT_COLLECTION_CONTENT_TYPE = "text/html; charset=\"utf-8\"";
    public static final String DEFAULT_FILE_CONTENT_TYPE = "text/plain";

    private DAVResourceURI myResourceURI;
    private FSRepository myRepository;
    private long myRevision;
    private long myVersion;
    private boolean myIsCollection;
    private boolean myIsSVNClient;
    private boolean myIsAutoCheckedOut;
    private String myDeltaBase;
    private String myClientOptions;
    private String myBaseChecksum;
    private String myResultChecksum;
    private String myUserName;
    private SVNProperties mySVNProperties;
    private Collection myDeadProperties;
    private Collection myEntries;
    private File myActivitiesDB;
    private FSFS myFSFS;
    private String myTxnName;
    private FSRoot myRoot;
    private FSTransactionInfo myTxnInfo;
    
    /**
     * DAVResource  constructor
     *
     * @param repository   repository resource connect to
     * @param context      contains requested url requestContext and name of repository if servlet use SVNParentPath directive.
     * @param uri          special uri for DAV requests can be /path or /SPECIAL_URI/xxx/path
     * @param label        request's label header
     * @param useCheckedIn special case for VCC resource
     * @throws SVNException if an error occurs while fetching repository properties.
     */
    public DAVResource(SVNRepository repository, DAVResourceURI resourceURI, boolean isSVNClient, String deltaBase, long version, 
            String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) throws SVNException {
        myRepository = (FSRepository) repository;
        myRepository.testConnection();//this should create an FSFS object
        myFSFS = myRepository.getFSFS();
        myResourceURI = resourceURI;
        myIsSVNClient = isSVNClient;
        myDeltaBase = deltaBase;
        myVersion = version;
        myClientOptions = clientOptions;
        myBaseChecksum = baseChecksum;
        myResultChecksum = resultChecksum;
        myRevision = resourceURI.getRevision();
        myUserName = userName;
        myActivitiesDB = activitiesDB;
        prepare();
    }

    public DAVResource(SVNRepository repository, DAVResourceURI resourceURI, long revision, boolean isSVNClient, String deltaBase, 
            long version, String clientOptions, String baseChecksum, String resultChecksum, String userName, File activitiesDB) {
        myResourceURI = resourceURI;
        myRepository = (FSRepository) repository;
        myFSFS = myRepository.getFSFS();
        myRevision = revision;
        myIsSVNClient = isSVNClient;
        myDeltaBase = deltaBase;
        myVersion = version;
        myClientOptions = clientOptions;
        myBaseChecksum = baseChecksum;
        myResultChecksum = resultChecksum;
        myUserName = userName;
        myActivitiesDB = activitiesDB;
    }

    public DAVResource() {
    }
    
    public void setRoot(FSRoot root) {
        myRoot = root;
    }

    public FSRoot getRoot() {
        return myRoot;
    }
    
    public FSTransactionInfo getTxnInfo() {
        return myTxnInfo;
    }
    
    public void setTxnInfo(FSTransactionInfo txnInfo) {
        myTxnInfo = txnInfo;
    }

    public DAVResourceURI getResourceURI() {
        return myResourceURI;
    }

    public SVNRepository getRepository() {
        return myRepository;
    }

    public long getRevision() {
        return myRevision;
    }

    public boolean exists() {
        return myResourceURI.exists();
    }

    public boolean isVersioned() {
        return myResourceURI.isVersioned();
    }
    
    public boolean isWorking() {
        return myResourceURI.isWorking();
    }
    
    public boolean isBaseLined() {
        return myResourceURI.isBaseLined();
    }
    
    public DAVResourceType getType() {
        return getResourceURI().getType();
    }

    public boolean lacksETagPotential() {
        DAVResourceType type = getResourceURI().getType();
        return !exists() || (type != DAVResourceType.REGULAR && type != DAVResourceType.VERSION) || 
               (type == DAVResourceType.VERSION && isBaseLined()); 
    }
    
    public boolean canBeActivity() {
        return isAutoCheckedOut() || (getType() == DAVResourceType.ACTIVITY && !exists());
    }
    
    public boolean isCollection() {
        return myIsCollection;
    }

    public boolean isSVNClient() {
        return myIsSVNClient;
    }
    
    public String getUserName() {
        return myUserName;
    }

    public String getDeltaBase() {
        return myDeltaBase;
    }

    public long getVersion() {
        return myVersion;
    }

    public String getClientOptions() {
        return myClientOptions;
    }

    public String getBaseChecksum() {
        return myBaseChecksum;
    }

    public String getResultChecksum() {
        return myResultChecksum;
    }

    public File getActivitiesDB() {
        return myActivitiesDB;
    }

    public Iterator getChildren() throws SVNException {
        return new Iterator() {
            Iterator entriesIterator = getEntries().iterator();

            public void remove() {
            }

            public boolean hasNext() {
                return entriesIterator.hasNext();
            }

            public Object next() {
                SVNDirEntry entry = (SVNDirEntry) entriesIterator.next();
                String childURI = DAVPathUtil.append(getResourceURI().getURI(), entry.getName());
                try {
                    DAVResourceURI newResourceURI = new DAVResourceURI(getResourceURI().getContext(), childURI, null, false);
                    return new DAVResource(getRepository(), newResourceURI, getRevision(), isSVNClient(), getDeltaBase(), 
                            getVersion(), getClientOptions(), null, null, getUserName(), getActivitiesDB());
                } catch (SVNException e) {
                    return null;
                }
            }
        };
    }

    public Collection getEntries() throws SVNException {
        if (isCollection() && myEntries == null) {
            myEntries = new LinkedList();
            getRepository().getDir(getResourceURI().getPath(), getRevision(), null, SVNDirEntry.DIRENT_KIND, myEntries);
        }
        return myEntries;
    }

    public long getCreatedRevision() throws SVNException {
        String revisionParameter = getProperty(SVNProperty.COMMITTED_REVISION);
        try {
            return Long.parseLong(revisionParameter);
        } catch (NumberFormatException e) {
            return getRevision();
        }
    }

    public long getCreatedRevision(String path, long revision) throws SVNException {
        if (path == null) {
            return INVALID_REVISION;
        } else if (path.equals(getResourceURI().getPath())) {
            return getCreatedRevision();
        } else {
            SVNDirEntry currentEntry = getRepository().getDir(path, revision, false, null);
            return currentEntry.getRevision();
        }
    }

    public long getCreatedRevisionUsingFS(String path) throws SVNException {
        path = path == null ? getResourceURI().getPath() : path;
        FSRevisionNode node = myRoot.getRevisionNode(path);
        return node.getCreatedRevision();
    }
    
    public Date getLastModified() throws SVNException {
        if (lacksETagPotential()) {
            return null;
        }
        return getRevisionDate(getCreatedRevision());
    }

    public Date getRevisionDate(long revision) throws SVNException {
        return SVNDate.parseDate(getRevisionProperty(revision, SVNRevisionProperty.DATE));
    }

    public String getETag() {
        if (lacksETagPotential()) {
            return null;
        }
        
        long createdRevision = -1;
        try {
            FSRevisionNode revNode = myRoot.getRevisionNode(getResourceURI().getPath());
            createdRevision = revNode.getCreatedRevision();
        } catch (SVNException svne) {
            return null;
        }
        
        StringBuffer eTag = new StringBuffer();
        eTag.append(isCollection() ? "W/" : "");
        eTag.append("\"");
        
        eTag.append(createdRevision);
        eTag.append("/");
        eTag.append(SVNEncodingUtil.uriEncode(getResourceURI().getPath()));
        eTag.append("\"");
        return eTag.toString();
    }

    public String getRepositoryUUID(boolean forceConnect) throws SVNException {
        return getRepository().getRepositoryUUID(forceConnect);
    }

    public String getContentType() throws SVNException {
        if (getResourceURI().isBaseLined() && getResourceURI().getType() == DAVResourceType.VERSION) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        if (getResourceURI().getType() == DAVResourceType.PRIVATE && getResourceURI().getKind() == DAVResourceKind.VCC) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_PROPS_NOT_FOUND, "Failed to determine property"), SVNLogType.NETWORK);
            return null;
        }
        if (isCollection()) {
            return DEFAULT_COLLECTION_CONTENT_TYPE;
        }
        String contentType = getProperty(SVNProperty.MIME_TYPE);
        if (contentType != null) {
            return contentType;
        }
        return DEFAULT_FILE_CONTENT_TYPE;
    }

    public long getLatestRevision() throws SVNException {
        return getRepository().getLatestRevision();
    }

    public long getContentLength() throws SVNException {
        SVNDirEntry entry = getRepository().getDir(getResourceURI().getPath(), getRevision(), false, null);
        return entry.getSize();
    }

    public SVNLock[] getLocks() throws SVNException {
        if (getResourceURI().getPath() == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED, "get-locks-report run on resource which doesn't represent a path within a repository."), SVNLogType.NETWORK);
        }
        return getRepository().getLocks(getResourceURI().getPath());
    }
    
    public SVNLock getLock() throws SVNException {
        return getRepository().getLock(getResourceURI().getPath());
    }

    public String getAuthor(long revision) throws SVNException {
        return getRevisionProperty(revision, SVNRevisionProperty.AUTHOR);
    }

    public String getMD5Checksum() throws SVNException {
        return getProperty(SVNProperty.CHECKSUM);
    }

    public Collection getDeadProperties() throws SVNException {
        if (myDeadProperties == null) {
            myDeadProperties = new ArrayList();
            for (Iterator iterator = getSVNProperties().nameSet().iterator(); iterator.hasNext();) {
                String propertyName = (String) iterator.next();
                if (SVNProperty.isRegularProperty(propertyName)) {
                    myDeadProperties.add(propertyName);
                }
            }
        }
        return myDeadProperties;
    }

    public String getLog(long revision) throws SVNException {
        return getRevisionProperty(revision, SVNRevisionProperty.LOG);
    }

    public String getProperty(String propertyName) throws SVNException {
        return getSVNProperties().getStringValue(propertyName);
    }

    public String getRevisionProperty(long revision, String propertyName) throws SVNException {
        SVNPropertyValue value = getRepository().getRevisionPropertyValue(revision, propertyName);
        return value == null ? null : value.getString();
    }

    public void writeTo(OutputStream out) throws SVNException {
        if (isCollection()) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.RA_DAV_REQUEST_FAILED), SVNLogType.NETWORK);
        }
        getRepository().getFile(getResourceURI().getPath(), getRevision(), null, out);
    }

    public boolean isAutoCheckedOut() {
        return myIsAutoCheckedOut;
    }

    public void setIsAutoCkeckedOut(boolean isAutoCheckedOut) {
        myIsAutoCheckedOut = isAutoCheckedOut;
    }
    
    public String getTxnName() {
        return myTxnName;
    }

    public void setExists(boolean exists) {
        myResourceURI.setExists(exists);
    }
    
    public void setVersioned(boolean isVersioned) {
        myResourceURI.setVersioned(isVersioned);
    }

    public void setWorking(boolean isWorking) {
        myResourceURI.setWorking(isWorking);
    }
    
    public void setBaseLined(boolean isBaseLined) {
        myResourceURI.setBaseLined(isBaseLined);
    }
    
    public void setCollection(boolean isCollection) {
        myIsCollection = isCollection;
    }
    
    public void setTxnName(String txnName) {
        myTxnName = txnName;
    }
    
    public void setRevision(long revision) {
        myRevision = revision;
        myResourceURI.setRevision(revision);
    }
    
    public void setResourceURI(DAVResourceURI resourceURI) {
        myResourceURI = resourceURI;
    }

    public boolean equals(Object o) {
        if (o == null || o.getClass() != this.getClass()) {
            return false;
        }
        
        if (o == this) {
            return true;
        }
        
        DAVResource otherResource = (DAVResource) o;
        File reposRoot1 = myFSFS.getDBRoot();
        File reposRoot2 = otherResource.myFSFS.getDBRoot();
        if (!reposRoot1.equals(reposRoot2)) {
            return false;
        }
        return true;
    }
    
    public DAVResource dup() {
        DAVResource copy = new DAVResource();
        copyTo(copy);
        return copy;
    }
    
    public void prepare() throws DAVException {
        DAVResourceHelper.prepareResource(this);
    }

    public FSFS getFSFS() {
        return myFSFS;
    }
    
    public String getTxn() {
        DAVResourceURI resourceURI = getResourceURI();
        return DAVServletUtil.getTxn(getActivitiesDB(), resourceURI.getActivityID());
    }

    private SVNProperties getSVNProperties() throws SVNException {
        if (mySVNProperties == null) {
            mySVNProperties = new SVNProperties();
            if (getResourceURI().getType() == DAVResourceType.REGULAR) {
                if (isCollection()) {
                    getRepository().getDir(getResourceURI().getPath(), getRevision(), mySVNProperties, (ISVNDirEntryHandler) null);
                } else {
                    getRepository().getFile(getResourceURI().getPath(), getRevision(), mySVNProperties, null);
                }
            }
        }
        return mySVNProperties;
    }

    protected void copyTo(DAVResource copy) {
        copy.myResourceURI = myResourceURI.dup();
        copy.myRepository = myRepository;
        copy.myRevision = myRevision;
        copy.myIsCollection = myIsCollection;
        copy.myIsSVNClient = myIsCollection;
        copy.myIsAutoCheckedOut = myIsAutoCheckedOut;
        copy.myDeltaBase = myDeltaBase;
        copy.myVersion = myVersion;
        copy.myClientOptions = myClientOptions;
        copy.myBaseChecksum = myBaseChecksum;
        copy.myResultChecksum = myResultChecksum;
        copy.myUserName = myUserName;
        copy.mySVNProperties = mySVNProperties;
        copy.myDeadProperties = myDeadProperties;
        copy.myEntries = myEntries;
        copy.myActivitiesDB = myActivitiesDB;
        copy.myFSFS = myFSFS;
        copy.myTxnName = myTxnName;
        copy.myRoot = myRoot;
        copy.myTxnInfo = myTxnInfo;
    }

}

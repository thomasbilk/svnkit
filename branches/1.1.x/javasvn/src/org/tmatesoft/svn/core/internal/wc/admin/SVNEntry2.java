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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.util.Iterator;
import java.util.Map;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

/**
 * @version 1.0
 * @author TMate Software Ltd.
 */
public class SVNEntry2 implements Comparable {

    private Map myAttributes;
    private SVNAdminArea myAdminArea;
    private String myName;

    public SVNEntry2(Map attributes, SVNAdminArea adminArea, String name) {
        myAttributes = attributes;
        myName = name;
        myAdminArea = adminArea;
        if (!myAttributes.containsKey(SVNProperty.NAME)) {
            myAttributes.put(SVNProperty.NAME, name);
        }
    }

    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != SVNEntry2.class) {
            return false;
        }
        SVNEntry2 entry = (SVNEntry2) obj;
        return entry.myAttributes == myAttributes && entry.myName.equals(myName);
    }

    public int hashCode() {
        return myAttributes.hashCode() + 17 * myName.hashCode();
    }

    public int compareTo(Object obj) {
        if (obj == null || obj.getClass() != SVNEntry2.class) {
            return 1;
        }
        return myName.compareTo(((SVNEntry2) obj).myName);
    }

    public String getURL() {
        String url = (String)myAttributes.get(SVNProperty.URL);
        if (url == null && myAdminArea != null && !myAdminArea.getThisDirName().equals(myName)) {
            SVNEntry2 rootEntry = null; 
            try {    
                rootEntry = myAdminArea.getEntry(myAdminArea.getThisDirName(), true); 
            } catch (SVNException svne) {
                return url;
            }
            url = rootEntry.getURL();
            url = SVNPathUtil.append(url, SVNEncodingUtil.uriEncode(myName));
        }
        return url;
    }
    
    public SVNURL getSVNURL() throws SVNException {
        String url = getURL();
        if (url != null) {
            return SVNURL.parseURIEncoded(url);
        }
        return null;
    }

    public String getName() {
        return myName;
    }

    public boolean isDirectory() {
        return SVNProperty.KIND_DIR.equals(myAttributes.get(SVNProperty.KIND));
    }

    public long getRevision() {
        String revStr = (String)myAttributes.get(SVNProperty.REVISION);
        if (revStr == null && myAdminArea != null && !myAdminArea.getThisDirName().equals(myName)) {
            SVNEntry2 rootEntry = null;
            try {
                rootEntry = myAdminArea.getEntry(myAdminArea.getThisDirName(), true);
            } catch (SVNException svne) {
                return -1;
            }
            return rootEntry.getRevision();
        }
        return revStr != null ? Long.parseLong(revStr) : -1;
    }

    public boolean isScheduledForAddition() {
        return SVNProperty.SCHEDULE_ADD.equals(myAttributes.get(SVNProperty.SCHEDULE));
    }

    public boolean isScheduledForDeletion() {
        return SVNProperty.SCHEDULE_DELETE.equals(myAttributes.get(SVNProperty.SCHEDULE));
    }

    public boolean isScheduledForReplacement() {
        return SVNProperty.SCHEDULE_REPLACE.equals(myAttributes.get(SVNProperty.SCHEDULE));
    }

    public boolean isHidden() {
        return (isDeleted() || isAbsent()) && !isScheduledForAddition()
                && !isScheduledForReplacement();
    }

    public boolean isFile() {
        return SVNProperty.KIND_FILE.equals(myAttributes.get(SVNProperty.KIND));
    }

    public String getLockToken() {
        return (String)myAttributes.get(SVNProperty.LOCK_TOKEN);
    }

    public boolean isDeleted() {
        return Boolean.TRUE.toString().equals(myAttributes.get(SVNProperty.DELETED));
    }

    public boolean isAbsent() {
        return Boolean.TRUE.toString().equals(myAttributes.get(SVNProperty.ABSENT));
    }

    public String toString() {
        return myName;
    }

    private boolean setAttributeValue(String name, String value) {
        if (SVNProperty.SCHEDULE.equals(name)) {
            if (SVNProperty.SCHEDULE_DELETE.equals(value)) {
                if (SVNProperty.SCHEDULE_ADD.equals(myAttributes.get(SVNProperty.SCHEDULE))) {
                    if (myAttributes.get(SVNProperty.DELETED) == null) {
                        try {
                            myAdminArea.deleteEntry(myName); 
                        } catch (SVNException svne) {
                            //
                        }
                    } else {
                        myAttributes.remove(SVNProperty.SCHEDULE);
                    }
                    return true;
                }
            }
        }

        if (value == null) {
            return myAttributes.remove(name) != null;
        }
        Object oldValue = myAttributes.put(name, value);
        return !value.equals(oldValue);            
    }
    
    public boolean setRevision(long revision) {
        return setAttributeValue(SVNProperty.REVISION, Long.toString(revision));
    }

    public boolean setURL(String url) {
        return setAttributeValue(SVNProperty.URL, url);
    }

    public void setIncomplete(boolean incomplete) {
        setAttributeValue(SVNProperty.INCOMPLETE,incomplete ? Boolean.TRUE.toString() : null);
    }

    public boolean isIncomplete() {
        return Boolean.TRUE.toString().equals(myAttributes.get(SVNProperty.INCOMPLETE));
    }

    public String getConflictOld() {
        return (String)myAttributes.get(SVNProperty.CONFLICT_OLD);
    }

    public void setConflictOld(String name) {
        setAttributeValue(SVNProperty.CONFLICT_OLD, name);
    }

    public String getConflictNew() {
        return (String)myAttributes.get(SVNProperty.CONFLICT_NEW);
    }

    public void setConflictNew(String name) {
        setAttributeValue(SVNProperty.CONFLICT_NEW, name);
    }

    public String getConflictWorking() {
        return (String)myAttributes.get(SVNProperty.CONFLICT_WRK);
    }

    public void setConflictWorking(String name) {
        setAttributeValue(SVNProperty.CONFLICT_WRK, name);
    }

    public String getPropRejectFile() {
        return (String)myAttributes.get(SVNProperty.PROP_REJECT_FILE);
    }

    public void setPropRejectFile(String name) {
        setAttributeValue(SVNProperty.PROP_REJECT_FILE, name);
    }

    public String getAuthor() {
        return (String)myAttributes.get(SVNProperty.LAST_AUTHOR);
    }

    public String getCommittedDate() {
        return (String)myAttributes.get(SVNProperty.COMMITTED_DATE);
    }

    public long getCommittedRevision() {
        String rev = (String)myAttributes.get(SVNProperty.COMMITTED_REVISION);
        if (rev == null) {
            return -1;
        }
        return Long.parseLong(rev);
    }

    public void setTextTime(String time) {
        setAttributeValue(SVNProperty.TEXT_TIME, time);
    }

    public void setKind(SVNNodeKind kind) {
        String kindStr = kind == SVNNodeKind.DIR ? SVNProperty.KIND_DIR : (kind == SVNNodeKind.FILE ? SVNProperty.KIND_FILE : null);
        setAttributeValue(SVNProperty.KIND, kindStr);
    }

    public void setAbsent(boolean absent) {
        setAttributeValue(SVNProperty.ABSENT, absent ? Boolean.TRUE.toString() : null);
    }

    public void setDeleted(boolean deleted) {
        setAttributeValue(SVNProperty.DELETED, deleted ? Boolean.TRUE.toString() : null);
    }

    public SVNNodeKind getKind() {
        String kind = (String)myAttributes.get(SVNProperty.KIND);
        if (SVNProperty.KIND_DIR.equals(kind)) {
            return SVNNodeKind.DIR;
        } else if (SVNProperty.KIND_FILE.equals(kind)) {
            return SVNNodeKind.FILE;
        }
        return SVNNodeKind.UNKNOWN;
    }
    
    public String getTextTime() {
        return (String)myAttributes.get(SVNProperty.TEXT_TIME);
    }

    public String getChecksum() {
        return (String)myAttributes.get(SVNProperty.CHECKSUM);
    }

    public void setLockComment(String comment) {
        myAttributes.put(SVNProperty.LOCK_COMMENT, comment);
    }

    public void setLockOwner(String owner) {
        setAttributeValue(SVNProperty.LOCK_OWNER, owner);
    }

    public void setLockCreationDate(String date) {
        setAttributeValue(SVNProperty.LOCK_CREATION_DATE, date);
    }

    public void setLockToken(String token) {
        setAttributeValue(SVNProperty.LOCK_TOKEN, token);
    }

    public void setUUID(String uuid) {
        setAttributeValue(SVNProperty.UUID, uuid);
    }

    public void unschedule() {
        setAttributeValue(SVNProperty.SCHEDULE, null);
    }

    public void scheduleForAddition() {
        setAttributeValue(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_ADD);
    }

    public void scheduleForDeletion() {
        setAttributeValue(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_DELETE);
    }

    public void scheduleForReplacement() {
        setAttributeValue(SVNProperty.SCHEDULE, SVNProperty.SCHEDULE_REPLACE);
    }

    public void setCopyFromRevision(long revision) {
        setAttributeValue(SVNProperty.COPYFROM_REVISION, revision >= 0 ? Long.toString(revision) : null);
    }

    public boolean setCopyFromURL(String url) {
        return setAttributeValue(SVNProperty.COPYFROM_URL, url);
    }

    public void setCopied(boolean copied) {
        setAttributeValue(SVNProperty.COPIED, copied ? Boolean.TRUE.toString() : null);
    }

    public String getCopyFromURL() {
        return (String)myAttributes.get(SVNProperty.COPYFROM_URL);
    }

    public SVNURL getCopyFromSVNURL() throws SVNException {
        String url = getCopyFromURL();
        if (url != null) {
            return SVNURL.parseURIEncoded(url);
        }
        return null;
    }

    public long getCopyFromRevision() {
        String rev = (String)myAttributes.get(SVNProperty.COPYFROM_REVISION);
        if (rev == null) {
            return -1;
        }
        return Long.parseLong(rev);
    }

    public String getPropTime() {
        return (String)myAttributes.get(SVNProperty.PROP_TIME);
    }

    public void setPropTime(String time) {
        setAttributeValue(SVNProperty.PROP_TIME, time);
    }

    public boolean isCopied() {
        return Boolean.TRUE.toString().equals(myAttributes.get(SVNProperty.COPIED));
    }

    public String getUUID() {
        return (String)myAttributes.get(SVNProperty.UUID);
    }

    public String getRepositoryRoot() {
        return (String)myAttributes.get(SVNProperty.REPOS);
    }

    public SVNURL getRepositoryRootURL() throws SVNException {
        String url = getRepositoryRoot();
        if (url != null) {
            return SVNURL.parseURIEncoded(url);
        }
        return null;
    }
    
    public boolean setRepositoryRoot(String url) {
        return setAttributeValue(SVNProperty.REPOS, url);
    }

    public boolean setRepositoryRootURL(SVNURL url) {
        return setRepositoryRoot(url == null ? null : url.toString());
    }

    public void loadProperties(Map entryProps) {
        if (entryProps == null) {
            return;
        }
        for (Iterator propNames = entryProps.keySet().iterator(); propNames.hasNext();) {
            String propName = (String) propNames.next();
            setAttributeValue(propName, (String) entryProps.get(propName));
        }
    }

    public String getLockOwner() {
        return (String)myAttributes.get(SVNProperty.LOCK_OWNER);
    }

    public String getLockComment() {
        return (String)myAttributes.get(SVNProperty.LOCK_COMMENT);
    }

    public String getLockCreationDate() {
        return (String)myAttributes.get(SVNProperty.LOCK_CREATION_DATE);
    }

    public String getSchedule() {
        return (String)myAttributes.get(SVNProperty.SCHEDULE);
    }

    public void setCachableProperties(String[] cachableProps) {
        if (cachableProps != null) {
            myAttributes.put(SVNProperty.CACHABLE_PROPS, cachableProps);
        } else {
            myAttributes.remove(SVNProperty.CACHABLE_PROPS);
        }
    }

    public String[] getCachableProperties() {
        return (String[])myAttributes.get(SVNProperty.CACHABLE_PROPS);
    }

    public void setPresentProperties(String[] presentProps) {
        if (presentProps != null) {
            myAttributes.put(SVNProperty.PRESENT_PROPS, presentProps);
        } else {
            myAttributes.remove(SVNProperty.PRESENT_PROPS);
        }
    }

    public String[] getPresentProperties() {
        return (String[])myAttributes.get(SVNProperty.PRESENT_PROPS);
    }
    
    public void setHasProperties(boolean hasProps) {
        setAttributeValue(SVNProperty.HAS_PROPS, SVNProperty.toString(hasProps));
    }
    
    public void setHasPropertyModifications(boolean hasPropModifications) {
        setAttributeValue(SVNProperty.HAS_PROP_MODS, SVNProperty.toString(hasPropModifications));
    }

    public Map asMap() {
        return myAttributes;
    }
    
    public SVNEntry2 copy() {
        return new SVNEntry2(myAttributes, null, myName);
    }
}
/*
 * ====================================================================
 * Copyright (c) 2004-2009 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.wc;

import java.io.File;

import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNMergeRange;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;

/**
 * The <b>SVNEvent</b> class is used to provide detailed information on 
 * an operation progress to the <b>ISVNEventHandler</b> (if any) registered 
 * for an <b>SVN</b>*<b>Client</b> object. Such events are generated by
 * an operation invoked by do*() method of an <b>SVN</b>*<b>Client</b> object
 * and passed to a developer's event handler for notification. Retrieving 
 * information out of an <b>SVNEvent</b> the developer can decide how it 
 * should be interpreted.
 * 
 * <p>
 * This is an example:<br />
 * implementing <b>ISVNEventHandler</b>
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNEventHandler;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.SVNCancelException;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNEvent;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNEventAction;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNStatusType;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.SVNNodeKind;
 * <span class="javakeyword">import</span> java.io.File;
 * ...
 * 
 * <span class="javakeyword">public class</span> MyCustomUpdateEventHandler <span class="javakeyword">implements</span> ISVNEventHandler {
 *     <span class="javakeyword">public void</span> handleEvent(SVNEvent event, <span class="javakeyword">double</span> progress) {
 *         <span class="javacomment">//get the action type</span>
 *         <span class="javakeyword">if</span>(event.getAction() == SVNEventAction.UPDATE_UPDATE){
 *             <span class="javacomment">//get the item's node kind</span>
 *             SVNNodeKind kind = even.getNodeKind();
 *             <span class="javacomment">//get the item's contents status</span>
 *             <span class="javakeyword">if</span>(event.getContentsStatus() == SVNStatusType.CHANGED &&
 *                kind == SVNNodeKind.FILE){
 *                 ...
 *             }
 *             ...
 *             <span class="javacomment">//get the item's properties status</span>
 *             <span class="javakeyword">if</span>(event.getPropertiesStatus() == SVNStatusType.MERGED){
 *                 ...
 *             }
 *             <span class="javacomment">//get the item's lock status</span>
 *             <span class="javakeyword">if</span>(event.getLockStatus() == SVNStatusType.LOCK_UNLOCKED){
 *                 ...
 *             }
 *             <span class="javacomment">//get the item's relative path</span>
 *             String path = event.getPath();
 *             <span class="javacomment">//or in a java.io.File representation</span>
 *             File fsEntry = event.getFile(); 
 *             
 *             <span class="javacomment">//get update revision</span>
 *             long revision = event.getRevision(); 
 *             ...
 *         }
 *         ...
 *     }
 *     
 *     <span class="javakeyword">public void</span> checkCancelled() <span class="javakeyword">throws</span> SVNCancelException{
 *         <span class="javakeyword">throw new</span> SVNCancelException(<span class="javastring">"cancelled!"</span>);
 *     }
 * }</pre><br />
 * then registering a handler:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNUpdateClient;
 * ...
 * 
 * SVNUpdateClient updateClient;
 * ...
 * updateClient.setEventHandler(<span class="javakeyword">new</span> MyCustomUpdateEventHandler());
 * ...</pre><br />
 * now when invoking an update operation:
 * <pre class="javacode">
 * updateClient.doUpdate(...);</pre><br />
 * the registered instance of the <b>ISVNEventHandler</b> implementation
 * will be dispatched progress events. 
 * </p>
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     ISVNEventHandler
 * @see     SVNStatusType
 * @see     SVNEventAction
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNEvent {

    private File myFile;
    private SVNNodeKind myNodeKind;
    private String myMimeType;
    
    private long myRevision;
    private long myPreviousRevision;
    private SVNURL myURL;
    private SVNURL myPreviousURL;

    private SVNStatusType myContentsStatus;
    private SVNStatusType myPropertiesStatus;
    private SVNStatusType myLockStatus;
    private SVNLock myLock;
    private SVNErrorMessage myErrorMessage;
    private SVNEventAction myAction;
    private SVNEventAction myExpectedAction;
    private String myChangelistName;
    private SVNMergeRange myRange;
    private Object info;

    /**
     * Constructs an <b>SVNEvent</b> object given
     * an error message for a filed operation. 
     * <p>
     * Used by SVNKit internals to construct and initialize an 
     * <b>SVNEvent</b> object. It's not intended for users (from an API point of view).
     * 
     * @param errorMessage the message describing the operation fault
     */
    public SVNEvent(SVNErrorMessage errorMessage) {
        myErrorMessage = errorMessage;
    }
    

    /**
     * Constructs an <b>SVNEvent</b> object.
     * <p>
     * Used by SVNKit internals to construct and initialize an 
     * <b>SVNEvent</b> object. It's not intended for users (from an API point of view).
     * 
     * @param file           local path
     * @param action         the type of action the item is exposed to
     * @param kind           the item's node kind
     * @param revision       a revision number
     * @param mimetype       the item's MIME type
     * @param cstatus        the item's contents status
     * @param pstatus        the item's properties status
     * @param lstatus        the item's lock status
     * @param lock           the item's lock
     * @param expected       the action type that was expected 
     * @param error          an error message
     * @param range          merge range
     * @param changelistName change list name
     */
    public SVNEvent(File file, SVNNodeKind kind, String mimetype, long revision, SVNStatusType cstatus, 
            SVNStatusType pstatus, SVNStatusType lstatus, SVNLock lock, SVNEventAction action, 
            SVNEventAction expected, SVNErrorMessage error, SVNMergeRange range, String changelistName) {
        myFile = file != null ? file.getAbsoluteFile() : null;
        myNodeKind = kind == null ? SVNNodeKind.UNKNOWN : kind;
        myMimeType = mimetype;
        myRevision = revision;
        myContentsStatus = cstatus == null ? SVNStatusType.INAPPLICABLE : cstatus;
        myPropertiesStatus = pstatus == null ? SVNStatusType.INAPPLICABLE : pstatus;
        myLockStatus = lstatus == null ? SVNStatusType.INAPPLICABLE : lstatus;
        myLock = lock;
        myAction = action;
        myExpectedAction = expected == null ? action : expected;
        myErrorMessage = error;
        myRange = range;
        myChangelistName = changelistName;
        myPreviousRevision = -1;
    }

    /**
     * Returns local path the event is fired for.
     * 
     * @return local path 
     */
    public File getFile() {
        return myFile;
    }
    
    /**
     * Gets the type of an action performed upon the item. An action is 
     * one of predefined <b>SVNEventAction</b> constants that are specific for
     * each kind of operation, such as update actions, commit actions, etc. 
     * 
     * @return the current action 
     */
    public SVNEventAction getAction() {
        return myAction;
    }
    
    /**
     * Returns the expected action. It is always the same as
     * the action returned by {@link #getAction()} except those cases 
     * when {@link #getAction()} returns {@link SVNEventAction#SKIP} (i.e. 
     * when the expected operation is skipped).
     *  
     * @return the expected action
     */
    public SVNEventAction getExpectedAction() {
        return myExpectedAction;
    }
    
    /**
     * Gets the status type of either file or directory contents.
     * Use predefined <b>SVNStatusType</b> constants to examine the
     * item's status. For a directory contents are its entries.
     * 
     * @return the item's status type
     */
    public SVNStatusType getContentsStatus() {
        return myContentsStatus;
    }
    
    /**
     * Gets the error message that (if it's an error situation and 
     * therefore the string is not <span class="javakeyword">null</span>) 
     * points to some fault.  
     * 
     * @return  an error message (in case of an error occured) or 
     *          <span class="javakeyword">null</span> if everything
     *          is OK
     */
    public SVNErrorMessage getErrorMessage() {
        return myErrorMessage;
    }
    
    /**
     * Gets the file item's lock information (if any) represented by an 
     * <b>SVNLock</b> object.
     * 
     * @return the file item's lock info if the file is locked; otherwise 
     *         <span class="javakeyword">null</span> 
     */
    public SVNLock getLock() {
        return myLock;
    }
    
    /**
     * Gets the file item's lock status. The value of 
     * <b>SVNStatusType.<i>LOCK_INAPPLICABLE</i></b> means
     * the lock status is irrelevant during the current event action.
     *  
     * @return the lock status of the file item
     */
    public SVNStatusType getLockStatus() {
        return myLockStatus;
    }

    /**
     * Gets the MIME type of the item relying upon the special 
     * SVN's <i>'svn:mime-type'</i> property.
     * 
     * <p>
     * You can use {@link org.tmatesoft.svn.core.SVNProperty}'s metods to 
     * find out whether it's a text MIME type or a binary:
     * <pre class="javacode">
     * <span class="javakeyword">import</span> org.tmatesoft.svn.core.SVNProperty;
     * ...
     * 
     * String mimeType = event.getMimeType();
     * <span class="javakeyword">if</span>(SVNProperty.isBinaryMimeType(mimeType)){
     *     <span class="javacomment">//your processing</span>
     * }</pre>
     * 
     * @return the item's MIME type as a string or 
     *         <span class="javakeyword">null</span> if the item has no
     *         <i>'svn:mime-type'</i> property set
     */
    public String getMimeType() {
        return myMimeType;
    }
    
    /**
     * Gets the node kind of the item characterizing it as an entry - 
     * whether it's a directory, file, etc. The value of 
     * <b>SVNNodeKind.<i>NONE</i></b> may mean the node kind is 
     * inapplicable diring the current event action. The value of 
     * <b>SVNNodeKind.<i>UNKNOWN</i></b> may mean deleted entries.
     *  
     * @return the item's node kind
     */
    public SVNNodeKind getNodeKind() {
        return myNodeKind;
    }
    
    /**
     * Gets the status type of the item's properties.
     * The value of <b>SVNStatusType.<i>INAPPLICABLE</i></b> may mean
     * the item has no versioned properties or that properties status is
     * irrelevant during the current event action.
     * 
     * @return the status type of the item's properties
     */
    public SVNStatusType getPropertiesStatus() {
        return myPropertiesStatus;
    }
    
    /**
     * Gets the revision number specific for the action context.
     * It may be whether an update revision or a committed one or
     * an inapplicable value when a revision number is irrelevant during
     * the event action.
     *  
     * @return a revision number
     */
    public long getRevision() {
        return myRevision;
    }

    /**
     * Returns the local revision before it will be changed by an update.
     * @return       revision prior to modification
     * @since        1.2.0, SVN 1.5.0
     */
    public long getPreviousRevision() {
        return myPreviousRevision;
    }

    /**
     * Returns the repository URL that this event is fired for.
     * @return repository url 
     */
	public SVNURL getURL() {
		return myURL;
	}

    /**
     * Returns the item's repository url before it will be changed by an update.
     * @return       repository url prior to modification
     * @since        1.2.0, SVN 1.5.0
     */
	public SVNURL getPreviousURL() {
		return myPreviousURL;
	}

	/**
     * Returns a changelist name. Relevant for changelist operations provided by 
     * {@link SVNChangelistClient}.
	 * 
	 * @return  changelist name
     * @since   1.2.0, SVN 1.5.0 
     * 
     */
    public String getChangelistName() {
        return myChangelistName;
    }
    
    /** 
     * Returns the merge range. 
     * 
     * <p/>
     * When {@link #getAction() action} is {@link SVNEventAction#MERGE_BEGIN}, and both the left and right sides 
     * of the merge are not from the same URL, the return value is <span class="javakeyword">null</span>.  
     * 
     * @return  merge range 
     * @since   1.2.0, New in SVN 1.5.0 
     */
    public SVNMergeRange getMergeRange() {
        return myRange;
    }    

    /**
     * Sets the item revision which will be changed by the operation after this event is handled.
     * 
     * <p/>
     * Note: this method is not intended for API users.
     * 
     * @param previousRevision previous revision
     * @since                  1.2.0, SVN 1.5.0
     */
    public void setPreviousRevision(long previousRevision) {
        myPreviousRevision = previousRevision;
    }

    /**
     * Sets the repository url.
     * 
     * <p/>
     * Note: this method is not intended for API users.
     * 
     * @param url repository url 
     */
    public void setURL(SVNURL url) {
        myURL = url;
    }

    /**
     * Sets the item url which will be changed by the operation after this event is handled.
     * 
     * <p/>
     * Note: this method is not intended for API users.
     * 
     * @param url previous url
     * @since     1.2.0, SVN 1.5.0
     */
	public void setPreviousURL(SVNURL url) {
	    myPreviousURL = url;
	}


    public String toString() {
        StringBuffer sb = new StringBuffer();
        if (getAction() != null) {
            sb.append(getAction().toString());
        }
        if (getFile() != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(getFile().toString());
        }
        if (getURL() != null) {
            if (sb.length() > 0) {
                sb.append(" ");
            }
            sb.append(getURL().toString());
        }
        return sb.toString();
    }


    
    public Object getInfo() {
        return info;
    }


    
    public void setInfo(Object info) {
        this.info = info;
    }
	
	
}

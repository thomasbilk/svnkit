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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.tmatesoft.svn.core.ISVNDirEntryHandler;
import org.tmatesoft.svn.core.ISVNLogEntryHandler;
import org.tmatesoft.svn.core.SVNAnnotationGenerator;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNDirEntry;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepository;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNURLUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminArea;
import org.tmatesoft.svn.core.internal.wc.admin.SVNEntry;
import org.tmatesoft.svn.core.internal.wc.admin.SVNWCAccess;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNLogType;
import org.tmatesoft.svn.core.internal.wc16.*;
import org.tmatesoft.svn.core.internal.wc17.*;

/**
 * The <b>SVNLogClient</b> class is intended for such purposes as getting
 * revisions history, browsing repository entries and annotating file contents.
 * 
 * <p>
 * Here's a list of the <b>SVNLogClient</b>'s methods 
 * matched against corresponing commands of the <b>SVN</b> command line 
 * client:
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="40%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>SVNKit</b></td>
 * <td><b>Subversion</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doLog()</td><td>'svn log'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doList()</td><td>'svn list'</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>doAnnotate()</td><td>'svn blame'</td>
 * </tr>
 * </table>
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 */
public class SVNLogClient extends SVNBasicClient {

    private SVNLogClient16 getSVNLogClient16() {
        return (SVNLogClient16) getDelegate16();
    }

    private SVNLogClient17 getSVNLogClient17() {
        return (SVNLogClient17) getDelegate17();
    }

    /**
     * Constructs and initializes an <b>SVNLogClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNLogClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p>
     * If <code>authManager</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNLogClient</b> will be using a default authentication
     * and network layers driver (see {@link SVNWCUtil#createDefaultAuthenticationManager()})
     * which uses server-side settings and auth storage from the 
     * default SVN's run-time configuration area (or system properties
     * if that area is not found).
     * 
     * @param authManager an authentication and network layers driver
     * @param options     a run-time configuration options driver     
     */
    public SVNLogClient(ISVNAuthenticationManager authManager, ISVNOptions options) {
        super(new SVNLogClient16(authManager, options), new SVNLogClient17(authManager, options));
        setDiffOptions(null);
    }

    /**
     * Constructs and initializes an <b>SVNLogClient</b> object
     * with the specified run-time configuration and authentication 
     * drivers.
     * 
     * <p>
     * If <code>options</code> is <span class="javakeyword">null</span>,
     * then this <b>SVNLogClient</b> will be using a default run-time
     * configuration driver  which takes client-side settings from the 
     * default SVN's run-time configuration area but is not able to
     * change those settings (read more on {@link ISVNOptions} and {@link SVNWCUtil}).  
     * 
     * <p/>
     * If <code>repositoryPool</code> is <span class="javakeyword">null</span>,
     * then {@link org.tmatesoft.svn.core.io.SVNRepositoryFactory} will be used to create {@link SVNRepository repository access objects}.
     *
     * @param repositoryPool   a repository pool object
     * @param options          a run-time configuration options driver
     */
    public SVNLogClient(ISVNRepositoryPool repositoryPool, ISVNOptions options) {
        super(new SVNLogClient16(repositoryPool, options), new SVNLogClient17(repositoryPool, options));
        setDiffOptions(null);
    }
    
    /**
     * Sets diff options for this client to use in annotate operations.
     * 
     * @param diffOptions diff options object
     */
    public void setDiffOptions(SVNDiffOptions diffOptions) {
        if (diffOptions == null) {
            diffOptions = new SVNDiffOptions();
        }
        getSVNLogClient16().setDiffOptions(diffOptions);
        getSVNLogClient17().setDiffOptions(diffOptions);
    }

    /**
     * Gets the diff options that are used in annotate operations 
     * by this client. Creates a new one if none was used before.
     * 
     * @return diff options
     */
    public SVNDiffOptions getDiffOptions() {
        return getSVNLogClient16().getDiffOptions();
    }

    
    /**
     * Obtains annotation information for each file text line from a repository
     * (using a Working Copy path to get a corresponding URL) and passes it to a 
     * provided annotation handler. 
     * 
     * <p/>
     * This method is equivalent to a call to 
     * <code>doAnnotate(path, pegRevision, startRevision, endRevision, false, false, handler, null)</code>.
     * 
     * @param  path           a WC file item to be annotated
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  handler        a caller's handler to process annotation information
     * @throws SVNException   if <code>startRevision > endRevision</code>
     * @see                   #doAnnotate(File, SVNRevision, SVNRevision, SVNRevision, boolean, boolean, ISVNAnnotateHandler, String)
     */
    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doAnnotate(path, pegRevision, startRevision, endRevision, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doAnnotate(path, pegRevision, startRevision, endRevision, handler);
            } else {
                throw e;
            }
        }
    }

    /**
     * Obtains annotation information for each file text line from a repository
     * (using a Working Copy path to get a corresponding URL) and passes it to a 
     * provided annotation handler. 
     * 
     * <p/>
     * This method is equivalent to a call to 
     * <code>doAnnotate(path, pegRevision, startRevision, endRevision, ignoreMimeType, false, handler, null)</code>.
     * 
     * @param  path            a WC file item to be annotated
     * @param  pegRevision     a revision in which <code>path</code> is first looked up
     *                         in the repository
     * @param  startRevision   a revision for an operation to start from
     * @param  endRevision     a revision for an operation to stop at
     * @param  ignoreMimeType  forces operation to run (all files to be treated as 
     *                         text, no matter what SVNKit has inferred from the mime-type 
     *                         property) 
     * @param  handler         a caller's handler to process annotation information
     * @throws SVNException
     * @see                    #doAnnotate(File, SVNRevision, SVNRevision, SVNRevision, boolean, boolean, ISVNAnnotateHandler, String)
     * @since                  1.1
     */
    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, boolean ignoreMimeType, 
            ISVNAnnotateHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doAnnotate(path, pegRevision, startRevision, endRevision, ignoreMimeType, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doAnnotate(path, pegRevision, startRevision, endRevision, ignoreMimeType, handler);
            } else {
                throw e;
            }
        }
    }

    /**
     * Invokes <code>handler</code> on each line-blame item associated with revision <code>endRevision</code> of 
     * <code>path</code>, using <code>startRevision</code> as the default source of all blame. 
     * 
     * <p/>
     * <code>pegRevision</code> indicates in which revision <code>path</code> is valid.  If <code>pegRevision</code>
     * is {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#WORKING}.
     * 
     * <p/>
     * If <code>startRevision</code> is <span class="javakeyword">null</span> or {@link SVNRevision#isValid() invalid},
     * then it defaults to revision 1. If <code>endRevision</code> is <span class="javakeyword">null</span> or 
     * {@link SVNRevision#isValid() invalid}, then in defaults to {@link SVNRevision#HEAD}.
     * 
     *  <p/>
     *  Note: this routine requires repository access.
     *  
     * @param  path                        a WC file item to be annotated              
     * @param  pegRevision                 a revision in which <code>path</code> is first looked up
     *                                     in the repository
     * @param  startRevision               a revision for an operation to start from
     * @param  endRevision                 a revision for an operation to stop at
     * @param  ignoreMimeType              forces operation to run (all files to be treated as 
     *                                     text, no matter what SVNKit has inferred from the mime-type 
     *                                     property) 
     * @param  includeMergedRevisions      if <span class="javakeyword">true</span>, then also returns data based upon revisions which have 
     *                                     been merged to <code>path</code>
     * @param  handler                     a caller's handler to process annotation information
     * @param  inputEncoding               character set to decode input bytes with
     * @throws SVNException                in the following cases:
     *                                     <ul>
     *                                     <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - if both 
     *                                     <code>startRevision</code> and <code>endRevision</code> are either <span class="javakeyword">null</span> 
     *                                     or {@link SVNRevision#isValid() invalid} 
     *                                     <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - if either of 
     *                                     <code>startRevision</code> or <code>endRevision</code> is {@link SVNRevision#WORKING} 
     *                                     <li/>exception with {@link SVNErrorCode#CLIENT_IS_BINARY_FILE} error code - if any of the 
     *                                     revisions of <code>path</code> have a binary mime-type, unless <code>ignoreMimeType</code> is 
     *                                     <span class="javakeyword">true</span>, in which case blame information will be generated regardless 
     *                                     of the MIME types of the revisions   
     *                                     </ul>
     * @since  1.2, SVN 1.5
     */
    public void doAnnotate(File path, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, boolean ignoreMimeType, 
            boolean includeMergedRevisions, ISVNAnnotateHandler handler, String inputEncoding) throws SVNException {
        try {
            getSVNLogClient17().doAnnotate(path, pegRevision, startRevision, endRevision, ignoreMimeType, includeMergedRevisions, handler, inputEncoding);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doAnnotate(path, pegRevision, startRevision, endRevision, ignoreMimeType, includeMergedRevisions, handler, inputEncoding);
            } else {
                throw e;
            }
        }
    }

    /**
     * Obtains annotation information for each file text line from a repository
     * and passes it to a provided annotation handler. 
     * 
     * <p>
     * This method is equivalent to a call to <code>doAnnotate(url, pegRevision, startRevision, endRevision, false, false, handler, null)</code>.
     * 
     * @param  url            a URL of a text file that is to be annotated 
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  handler        a caller's handler to process annotation information
     * @throws SVNException   if <code>startRevision > endRevision</code>
     * @see                   #doAnnotate(SVNURL, SVNRevision, SVNRevision, SVNRevision, boolean, boolean, ISVNAnnotateHandler, String)
     */
    public void doAnnotate(SVNURL url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doAnnotate(url, pegRevision, startRevision, endRevision, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doAnnotate(url, pegRevision, startRevision, endRevision, handler);
            } else {
                throw e;
            }
        }
    }

    /**
     * Obtains annotation information for each file text line from a repository
     * and passes it to a provided annotation handler. 
     * 
     * <p>
     * This method is equivalent to a call to <code>doAnnotate(url, pegRevision, startRevision, endRevision, false, false, handler, inputEncoding)</code>.
     * 
     * @param  url            a URL of a text file that is to be annotated 
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  handler        a caller's handler to process annotation information
     * @param  inputEncoding  a desired character set (encoding) of text lines
     * @throws SVNException
     * @see                   #doAnnotate(SVNURL, SVNRevision, SVNRevision, SVNRevision, boolean, boolean, ISVNAnnotateHandler, String)
     */
    public void doAnnotate(SVNURL url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, ISVNAnnotateHandler handler, String inputEncoding) throws SVNException {
        try {
            getSVNLogClient17().doAnnotate(url, pegRevision, startRevision, endRevision, handler, inputEncoding);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doAnnotate(url, pegRevision, startRevision, endRevision, handler, inputEncoding);
            } else {
                throw e;
            }
        }
    }

    /**
     * Obtains annotation information for each file text line from a repository and passes it to a provided annotation handler. 
     * 
     * <p>
     * This method is equivalent to a call to <code>doAnnotate(url, pegRevision, startRevision, endRevision, ignoreMimeType, false, handler, inputEncoding)</code>.
     *  
     * @param  url            a URL of a text file that is to be annotated 
     * @param  pegRevision    a revision in which <code>path</code> is first looked up
     *                        in the repository
     * @param  startRevision  a revision for an operation to start from
     * @param  endRevision    a revision for an operation to stop at
     * @param  ignoreMimeType forces operation to run (all files to be treated as 
     *                        text, no matter what SVNKit has inferred from the mime-type 
     *                        property) 
     * @param  handler        a caller's handler to process annotation information
     * @param  inputEncoding  a desired character set (encoding) of text lines
     * @throws SVNException
     * @see                   #doAnnotate(SVNURL, SVNRevision, SVNRevision, SVNRevision, boolean, boolean, ISVNAnnotateHandler, String)
     * @since                 1.1
     */
	public void doAnnotate(SVNURL url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, boolean ignoreMimeType, 
	        ISVNAnnotateHandler handler, String inputEncoding) throws SVNException {
	    try {
            getSVNLogClient17().doAnnotate(url, pegRevision, startRevision, endRevision, ignoreMimeType, handler, inputEncoding);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doAnnotate(url, pegRevision, startRevision, endRevision, ignoreMimeType, handler, inputEncoding);
            } else {
                throw e;
            }
        }
	}

    /**
     * Invokes <code>handler</code> on each line-blame item associated with revision <code>endRevision</code> of 
     * <code>url</code>, using <code>startRevision</code> as the default source of all blame. 
     * 
     * <p/>
     * <code>pegRevision</code> indicates in which revision <code>url</code> is valid. If <code>pegRevision</code>
     * is {@link SVNRevision#UNDEFINED}, then it defaults to {@link SVNRevision#HEAD}.
     * 
     * <p/>
     * If <code>startRevision</code> is <span class="javakeyword">null</span> or {@link SVNRevision#isValid() invalid},
     * then it defaults to revision 1. If <code>endRevision</code> is <span class="javakeyword">null</span> or 
     * {@link SVNRevision#isValid() invalid}, then in defaults to {@link SVNRevision#HEAD}.
     * 
     *  <p/>
     *  Note: this routine requires repository access
     *  
     * @param  url                         a URL of a text file that is to be annotated 
     * @param  pegRevision                 a revision in which <code>url</code> is first looked up
     *                                     in the repository
     * @param  startRevision               a revision for an operation to start from
     * @param  endRevision                 a revision for an operation to stop at
     * @param  ignoreMimeType              forces operation to run (all files to be treated as 
     *                                     text, no matter what SVNKit has inferred from the mime-type 
     *                                     property) 
     * @param  includeMergedRevisions      if <span class="javakeyword">true</span>, then also returns data based upon revisions which have 
     *                                     been merged to <code>url</code>
     * @param  handler                     a caller's handler to process annotation information
     * @param  inputEncoding               character set to decode input bytes with
     * @throws SVNException                in the following cases:
     *                                     <ul>
     *                                     <li/>exception with {@link SVNErrorCode#CLIENT_BAD_REVISION} error code - if both 
     *                                     <code>startRevision</code> and <code>endRevision</code> are either <span class="javakeyword">null</span> 
     *                                     or {@link SVNRevision#isValid() invalid} 
     *                                     <li/>exception with {@link SVNErrorCode#UNSUPPORTED_FEATURE} error code - if either of 
     *                                     <code>startRevision</code> or <code>endRevision</code> is {@link SVNRevision#WORKING} 
     *                                     <li/>exception with {@link SVNErrorCode#CLIENT_IS_BINARY_FILE} error code - if any of the 
     *                                     revisions of <code>url</code> have a binary mime-type, unless <code>ignoreMimeType</code> is 
     *                                     <span class="javakeyword">true</span>, in which case blame information will be generated regardless 
     *                                     of the MIME types of the revisions   
     *                                     </ul>
     * @since  1.2, SVN 1.5
     */
    public void doAnnotate(SVNURL url, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, boolean ignoreMimeType, 
            boolean includeMergedRevisions, ISVNAnnotateHandler handler, String inputEncoding) throws SVNException {
        try {
            getSVNLogClient17().doAnnotate(url, pegRevision, startRevision, endRevision, ignoreMimeType, includeMergedRevisions, handler, inputEncoding);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doAnnotate(url, pegRevision, startRevision, endRevision, ignoreMimeType, includeMergedRevisions, handler, inputEncoding);
            } else {
                throw e;
            }
        }
    
    }
    
    /**
     * Gets commit log messages with other revision specific information from a repository 
     * (using Working Copy paths to get corresponding URLs) and passes them to a log entry handler for
     * processing. Useful for observing the history of affected paths, author, date and log comments 
     * information per revision.
     * 
     * <p>
     * Calling this method is equivalent to 
     * <code>doLog(paths, startRevision, endRevision, SVNRevision.UNDEFINED, stopOnCopy, discoverChangedPaths, false, limit, null, handler)</code>.
     * 
     * @param  paths                  an array of Working Copy paths,
     *                                should not be <span class="javakeyword">null</span>
     * @param  startRevision          a revision for an operation to start from (including
     *                                this revision)    
     * @param  endRevision            a revision for an operation to stop at (including
     *                                this revision)
     * @param  stopOnCopy             <span class="javakeyword">true</span> not to cross
     *                                copies while traversing history, otherwise copies history
     *                                will be also included into processing
     * @param  discoverChangedPaths   <span class="javakeyword">true</span> to report
     *                                of all changed paths for every revision being processed 
     *                                (those paths will be available by calling 
     *                                {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  limit                  a maximum number of log entries to be processed 
     * @param  handler                a caller's log entry handler
     * @throws SVNException           if one of the following is true:
     *                                <ul>
     *                                <li>a path is not under version control
     *                                <li>can not obtain a URL of a WC path - there's no such
     *                                entry in the Working Copy
     *                                <li><code>paths</code> contain entries that belong to
     *                                different repositories
     *                                </ul>
     * @see                           #doLog(File[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)                                                    
     */
    public void doLog(File[] paths, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean discoverChangedPaths, long limit, final ISVNLogEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doLog(paths, startRevision, endRevision, stopOnCopy, discoverChangedPaths, limit, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doLog(paths, startRevision, endRevision, stopOnCopy, discoverChangedPaths, limit, handler);
            } else {
                throw e;
            }
        }
    }

    /**
     * Invokes <code>handler</code> on each log message from <code>startRevision</code> to <code>endRevision</code> in turn, inclusive 
     * (but never invokes <code>handler</code> on a given log message more than once).
     * 
     * <p/>
     * <code>handler</code> is invoked only on messages whose revisions involved a change to some path in <code>paths</code>. 
     * <code>pegRevision</code> indicates in which revision <code>paths</code> are valid. If <code>pegRevision</code> is
     * {@link SVNRevision#isValid() invalid}, it defaults to {@link SVNRevision#WORKING}.
     * 
     * <p/>
     * If <code>limit</code> is non-zero, only invokes <code>handler</code> on the first <code>limit</code> logs.
     *
     * <p/>
     * If <code>discoverChangedPaths</code> is set, then the changed paths <code>Map</code> argument
     * will be passed to a constructor of {@link SVNLogEntry} on each invocation of <code>handler</code>.
     *
     * <p/>
     * If <code>stopOnCopy</code> is set, copy history (if any exists) will not be traversed while harvesting 
     * revision logs for each target.
     * 
     * <p/>
     * If <code>includeMergedRevisions</code> is set, log information for revisions which have been merged to 
     * <code>paths</code> will also be returned.
     * 
     * <p/> 
     * Refer to {@link org.tmatesoft.svn.core.SVNLogEntry#hasChildren()} for additional information on how 
     * to handle mergeinfo information during a log operation.
     * 
     * <p/>
     * If <code>revisionProperties is <span class="javakeyword">null</span>, retrieves all revision properties; 
     * else, retrieves only the revprops named in the array (i.e. retrieves none if the array is empty).
     * 
     * <p/>
     * If <code>startRevision</code> is {@link SVNRevision#isValid() valid} but <code>endRevision</code> 
     * is not, then <code>endRevision</code> defaults to <code>startRevision</code>. If both 
     * <code>startRevision</code> and <code>endRevision</code> are invalid, then <code>endRevision</code> 
     * defaults to revision <code>0</code>, and <code>startRevision</code> defaults either to 
     * <code>pegRevision</code> in case the latter one is valid, or to {@link SVNRevision#BASE}, if it is not.
     * 
     * <p/>
     * Important: to avoid an exception with the {@link SVNErrorCode#FS_NO_SUCH_REVISION} error code  
     * when invoked against an empty repository (i.e. one not containing a revision 1), callers should specify 
     * the range {@link SVNRevision#HEAD}:<code>0</code>. 
     *
     * <p/>
     * If the caller has provided a non-<span class="javakeyword">null</span> {@link ISVNEventHandler},
     * it will be called with the {@link SVNEventAction#SKIP} event action on any unversioned paths. 
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  paths                  an array of Working Copy paths, for which log messages are desired
     * @param  startRevision          a revision for an operation to start from (including
     *                                this revision)    
     * @param  endRevision            a revision for an operation to stop at (including
     *                                this revision)
     * @param  pegRevision            a revision in which <code>paths</code> are first looked up
     *                                in the repository
     * @param  stopOnCopy             <span class="javakeyword">true</span> not to cross
     *                                copies while traversing history, otherwise copies history
     *                                will be also included into processing
     * @param  discoverChangedPaths   <span class="javakeyword">true</span> to report
     *                                of all changed paths for every revision being processed 
     *                                (those paths will be available by calling 
     *                                {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  includeMergedRevisions if <span class="javakeyword">true</span>, merged revisions will be also 
     *                                reported
     * @param  limit                  a maximum number of log entries to be processed 
     * @param  revisionProperties     names of revision properties to retrieve     
     * @param  handler                a caller's log entry handler
     * @throws SVNException           if one of the following is true:
     *                                <ul>
     *                                <li>can not obtain a URL of a WC path - there's no such
     *                                entry in the Working Copy
     *                                <li><code>paths</code> contain entries that belong to
     *                                different repositories
     *                                </ul>
     * @since                         1.2, SVN 1.5 
     */
    public void doLog(File[] paths, SVNRevision startRevision, SVNRevision endRevision, SVNRevision pegRevision, boolean stopOnCopy, 
            boolean discoverChangedPaths, boolean includeMergedRevisions, long limit, String[] revisionProperties, final ISVNLogEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doLog(paths, startRevision, endRevision, pegRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit, revisionProperties, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doLog(paths, startRevision, endRevision, pegRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit, revisionProperties, handler);
            } else {
                throw e;
            }
        }
    }

    /**
     * Invokes <code>handler</code> on each log message from the given <code>revisionRanges</code> in turn, inclusive 
     * (but never invokes <code>handler</code> on a given log message more than once).
     * 
     * <p/>
     * <code>handler</code> is invoked only on messages whose revisions involved a change to some path in <code>paths</code>. 
     * <code>pegRevision</code> indicates in which revision <code>paths</code> are valid. If <code>pegRevision</code> is
     * {@link SVNRevision#isValid() invalid}, it defaults to {@link SVNRevision#WORKING}.
     * 
     * <p/>
     * If <code>limit</code> is non-zero, only invokes <code>handler</code> on the first <code>limit</code> logs.
     *
     * <p/>
     * If <code>discoverChangedPaths</code> is set, then the changed paths <code>Map</code> argument
     * will be passed to a constructor of {@link SVNLogEntry} on each invocation of <code>handler</code>.
     *
     * <p/>
     * If <code>stopOnCopy</code> is set, copy history (if any exists) will not be traversed while harvesting 
     * revision logs for each target.
     * 
     * <p/>
     * If <code>includeMergedRevisions</code> is set, log information for revisions which have been merged to 
     * <code>paths</code> will also be returned.
     * 
     * <p/> 
     * Refer to {@link org.tmatesoft.svn.core.SVNLogEntry#hasChildren()} for additional information on how 
     * to handle mergeinfo information during a log operation.
     * 
     * <p/>
     * If <code>revisionProperties is <span class="javakeyword">null</span>, retrieves all revision properties; 
     * else, retrieves only the revprops named in the array (i.e. retrieves none if the array is empty).
     * 
     * <p/>
     * For every {@link SVNRevisionRange} in <code>revisionRanges</code>:
     * <b/>
     * If <code>startRevision</code> is {@link SVNRevision#isValid() valid} but <code>endRevision</code> 
     * is not, then <code>endRevision</code> defaults to <code>startRevision</code>. If both 
     * <code>startRevision</code> and <code>endRevision</code> are invalid, then <code>endRevision</code> 
     * defaults to revision <code>0</code>, and <code>startRevision</code> defaults either to 
     * <code>pegRevision</code> in case the latter one is valid, or to {@link SVNRevision#BASE}, if it is not.
     * 
     * <p/>
     * Important: to avoid an exception with the {@link SVNErrorCode#FS_NO_SUCH_REVISION} error code  
     * when invoked against an empty repository (i.e. one not containing a revision 1), callers should specify 
     * the range {@link SVNRevision#HEAD}:<code>0</code>. 
     *
     * <p/>
     * If the caller has provided a non-<span class="javakeyword">null</span> {@link ISVNEventHandler},
     * it will be called with the {@link SVNEventAction#SKIP} event action on any unversioned paths. 
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  paths                  an array of Working Copy paths, for which log messages are desired
     * @param  revisionRanges         collection of {@link SVNRevisionRange} objects 
     * @param  pegRevision            a revision in which <code>paths</code> are first looked up
     *                                in the repository
     * @param  stopOnCopy             <span class="javakeyword">true</span> not to cross
     *                                copies while traversing history, otherwise copies history
     *                                will be also included into processing
     * @param  discoverChangedPaths   <span class="javakeyword">true</span> to report
     *                                of all changed paths for every revision being processed 
     *                                (those paths will be available by calling 
     *                                {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  includeMergedRevisions if <span class="javakeyword">true</span>, merged revisions will be also 
     *                                reported
     * @param  limit                  a maximum number of log entries to be processed 
     * @param  revisionProperties     names of revision properties to retrieve     
     * @param  handler                a caller's log entry handler
     * @throws SVNException           if one of the following is true:
     *                                <ul>
     *                                <li>can not obtain a URL of a WC path - there's no such
     *                                entry in the Working Copy
     *                                <li><code>paths</code> contain entries that belong to
     *                                different repositories
     *                                </ul>
     * @since                         1.3, SVN 1.6 
     */
    public void doLog(File[] paths, Collection revisionRanges, SVNRevision pegRevision, boolean stopOnCopy, 
            boolean discoverChangedPaths, boolean includeMergedRevisions, long limit, String[] revisionProperties, 
            final ISVNLogEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doLog(paths, revisionRanges, pegRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit, revisionProperties, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doLog(paths, revisionRanges, pegRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit, revisionProperties, handler);
            } else {
                throw e;
            }
        }
        
    }
    
    /**
     * Gets commit log messages with other revision specific information from a repository (using Working Copy 
     * paths to get corresponding URLs) and passes them to a log entry handler for processing. Useful for 
     * observing the history of affected paths, author, date and log comments information per revision.
     * 
     * <p>
     * Calling this method is equivalent to 
     * <code>doLog(paths, startRevision, endRevision, pegRevision, stopOnCopy, discoverChangedPaths, false, limit, null, handler)</code>.
     * 
     * @param  paths                 an array of Working Copy paths,
     *                               should not be <span class="javakeyword">null</span>
     * @param  pegRevision           a revision in which <code>path</code> is first looked up
     *                               in the repository
     * @param  startRevision         a revision for an operation to start from (including
     *                               this revision)    
     * @param  endRevision           a revision for an operation to stop at (including
     *                               this revision)
     * @param  stopOnCopy            <span class="javakeyword">true</span> not to cross
     *                               copies while traversing history, otherwise copies history
     *                               will be also included into processing
     * @param  discoverChangedPaths  <span class="javakeyword">true</span> to report
     *                               of all changed paths for every revision being processed 
     *                               (those paths will be available by calling 
     *                               {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  limit                 a maximum number of log entries to be processed 
     * @param  handler               a caller's log entry handler
     * @throws SVNException          if one of the following is true:
     *                               <ul>
     *                               <li>a path is not under version control
     *                               <li>can not obtain a URL of a WC path - there's no such
     *                               entry in the Working Copy
     *                               <li><code>paths</code> contain entries that belong to
     *                               different repositories
     *                               </ul>
     * @see                          #doLog(File[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)
     */
    public void doLog(File[] paths, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, 
            boolean stopOnCopy, boolean discoverChangedPaths, long limit, final ISVNLogEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doLog(paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, limit, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doLog(paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, limit, handler);
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Gets commit log messages with other revision specific information from a repository and passes them to 
     * a log entry handler for processing. Useful for observing the history of affected paths, author, date and 
     * log comments information per revision.
     * 
     * <p>
     * Calling this method is equivalent to 
     * <code> doLog(url, paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, false, limit, null, handler)</code>.
     *  
     * @param  url                   a target URL            
     * @param  paths                 an array of paths relative to the target 
     *                               <code>url</code>
     * @param  pegRevision           a revision in which <code>url</code> is first looked up
     * @param  startRevision         a revision for an operation to start from (including
     *                               this revision)    
     * @param  endRevision           a revision for an operation to stop at (including
     *                               this revision)
     * @param  stopOnCopy            <span class="javakeyword">true</span> not to cross
     *                               copies while traversing history, otherwise copies history
     *                               will be also included into processing
     * @param  discoverChangedPaths  <span class="javakeyword">true</span> to report
     *                               of all changed paths for every revision being processed 
     *                               (those paths will be available by calling 
     *                               {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  limit                 a maximum number of log entries to be processed 
     * @param  handler               a caller's log entry handler
     * @throws SVNException
     * @see                          #doLog(SVNURL, String[], SVNRevision, SVNRevision, SVNRevision, boolean, boolean, boolean, long, String[], ISVNLogEntryHandler)
     * @since                        1.1, new in Subversion 1.4
     */
    public void doLog(SVNURL url, String[] paths, SVNRevision pegRevision, SVNRevision startRevision, SVNRevision endRevision, boolean stopOnCopy, boolean discoverChangedPaths, long limit, final ISVNLogEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doLog(url, paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, limit, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doLog(url, paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, limit, handler);
            } else {
                throw e;
            }
        }
    }

    /**
     * Invokes <code>handler</code> on each log message from <code>startRevision</code> to <code>endRevision</code> in turn, inclusive 
     * (but never invokes <code>handler</code> on a given log message more than once).
     * 
     * <p/>
     * <code>handler</code> is invoked only on messages whose revisions involved a change to some path in <code>paths</code>. 
     * <code>pegRevision</code> indicates in which revision <code>paths</code> are valid. If <code>pegRevision</code> is
     * {@link SVNRevision#isValid() invalid}, it defaults to {@link SVNRevision#HEAD}.
     * 
     * <p/>
     * If <code>limit</code> is non-zero, only invokes <code>handler</code> on the first <code>limit</code> logs.
     *
     * <p/>
     * If <code>discoverChangedPaths</code> is set, then the changed paths <code>Map</code> argument
     * will be passed to a constructor of {@link SVNLogEntry} on each invocation of <code>handler</code>.
     *
     * <p/>
     * If <code>stopOnCopy</code> is set, copy history (if any exists) will not be traversed while harvesting 
     * revision logs for each target.
     * 
     * <p/>
     * If <code>includeMergedRevisions</code> is set, log information for revisions which have been merged to 
     * <code>paths</code> will also be returned.
     * 
     * <p/>
     * If <code>revisionProperties is <span class="javakeyword">null</span>, retrieves all revision properties; 
     * else, retrieves only the revision properties named in the array (i.e. retrieves none if the array is empty).
     * 
     * <p/>
     * Important: to avoid an exception with the {@link SVNErrorCode#FS_NO_SUCH_REVISION} error code  
     * when invoked against an empty repository (i.e. one not containing a revision 1), callers should specify 
     * the range {@link SVNRevision#HEAD}:<code>0</code>. 
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  url                     repository URL            
     * @param  paths                   an array of paths relative to <code>url</code>
     * @param  pegRevision             a revision in which <code>paths</code> are first looked up
     *                                 in the repository
     * @param  startRevision           a revision for an operation to start from (including
     *                                 this revision)    
     * @param  endRevision             a revision for an operation to stop at (including
     *                                 this revision)
     * @param  stopOnCopy              <span class="javakeyword">true</span> not to cross
     *                                 copies while traversing history, otherwise copies history
     *                                 will be also included into processing
     * @param  discoverChangedPaths    <span class="javakeyword">true</span> to report
     *                                 of all changed paths for every revision being processed 
     *                                 (those paths will be available by calling 
     *                                 {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  includeMergedRevisions  if <span class="javakeyword">true</span>, merged revisions will be also 
     *                                 reported
     * @param  limit                   a maximum number of log entries to be processed
     * @param  revisionProperties      names of revision properties to retrieve     
     * @param  handler                 a caller's log entry handler
     * @throws SVNException 
     * @since                          1.2, SVN 1.5 
     */
    public void doLog(SVNURL url, String[] paths, SVNRevision pegRevision, SVNRevision startRevision, 
            SVNRevision endRevision, boolean stopOnCopy, boolean discoverChangedPaths, boolean includeMergedRevisions, 
            long limit, String[] revisionProperties, final ISVNLogEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doLog(url, paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit, revisionProperties, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doLog(url, paths, pegRevision, startRevision, endRevision, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit, revisionProperties, handler);
            } else {
                throw e;
            }
        }
    }

    /**
     * Invokes <code>handler</code> on each log message from the given <code></code> in turn, inclusive 
     * (but never invokes <code>handler</code> on a given log message more than once).
     * 
     * <p/>
     * <code>handler</code> is invoked only on messages whose revisions involved a change to some path in <code>paths</code>. 
     * <code>pegRevision</code> indicates in which revision <code>paths</code> are valid. If <code>pegRevision</code> is
     * {@link SVNRevision#isValid() invalid}, it defaults to {@link SVNRevision#HEAD}.
     * 
     * <p/>
     * If <code>limit</code> is non-zero, only invokes <code>handler</code> on the first <code>limit</code> logs.
     *
     * <p/>
     * If <code>discoverChangedPaths</code> is set, then the changed paths <code>Map</code> argument
     * will be passed to a constructor of {@link SVNLogEntry} on each invocation of <code>handler</code>.
     *
     * <p/>
     * If <code>stopOnCopy</code> is set, copy history (if any exists) will not be traversed while harvesting 
     * revision logs for each target.
     * 
     * <p/>
     * If <code>includeMergedRevisions</code> is set, log information for revisions which have been merged to 
     * <code>paths</code> will also be returned.
     * 
     * <p/>
     * If <code>revisionProperties is <span class="javakeyword">null</span>, retrieves all revision properties; 
     * else, retrieves only the revision properties named in the array (i.e. retrieves none if the array is empty).
     * 
     * <p/>
     * Important: to avoid an exception with the {@link SVNErrorCode#FS_NO_SUCH_REVISION} error code  
     * when invoked against an empty repository (i.e. one not containing a revision 1), callers should specify 
     * the range {@link SVNRevision#HEAD}:<code>0</code>. 
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  url                     repository URL            
     * @param  paths                   an array of paths relative to <code>url</code>
     * @param  pegRevision             a revision in which <code>paths</code> are first looked up
     *                                 in the repository
     * @param  revisionRanges          collection of {@link SVNRevisionRange} objects
     * @param  stopOnCopy              <span class="javakeyword">true</span> not to cross
     *                                 copies while traversing history, otherwise copies history
     *                                 will be also included into processing
     * @param  discoverChangedPaths    <span class="javakeyword">true</span> to report
     *                                 of all changed paths for every revision being processed 
     *                                 (those paths will be available by calling 
     *                                 {@link org.tmatesoft.svn.core.SVNLogEntry#getChangedPaths()})
     * @param  includeMergedRevisions  if <span class="javakeyword">true</span>, merged revisions will be also 
     *                                 reported
     * @param  limit                   a maximum number of log entries to be processed
     * @param  revisionProperties      names of revision properties to retrieve     
     * @param  handler                 a caller's log entry handler
     * @throws SVNException 
     * @since                          1.3, SVN 1.6 
     */
    public void doLog(SVNURL url, String[] paths, SVNRevision pegRevision, Collection revisionRanges, boolean stopOnCopy, 
            boolean discoverChangedPaths, boolean includeMergedRevisions, long limit, String[] revisionProperties, 
            final ISVNLogEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doLog(url, paths, pegRevision, revisionRanges, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit, revisionProperties, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doLog(url, paths, pegRevision, revisionRanges, stopOnCopy, discoverChangedPaths, includeMergedRevisions, limit, revisionProperties, handler);
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Browses directory entries from a repository (using Working 
     * Copy paths to get corresponding URLs) and uses the provided dir 
     * entry handler to process them.
     * 
     * <p>
     * On every entry that this method stops it gets some useful entry 
     * information which is packed into an {@link org.tmatesoft.svn.core.SVNDirEntry}
     * object and passed to the <code>handler</code>'s 
     * {@link org.tmatesoft.svn.core.ISVNDirEntryHandler#handleDirEntry(SVNDirEntry) handleDirEntry()} method.
     *  
     * @param  path           a WC item to get its repository location            
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  fetchLocks     <span class="javakeyword">true</span> to fetch locks 
     *                        information from a repository
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively (relevant for directories)    
     * @param  handler        a caller's directory entry handler (to process
     *                        info on an entry)
     * @throws SVNException 
     * 
     * @deprecated            use {@link #doList(File, SVNRevision, SVNRevision, boolean, SVNDepth, int, ISVNDirEntryHandler)} 
     *                        instead  
     */
    public void doList(File path, SVNRevision pegRevision, SVNRevision revision, boolean fetchLocks, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doList(path, pegRevision, revision, fetchLocks, recursive, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doList(path, pegRevision, revision, fetchLocks, recursive, handler);
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Reports the directory entry, and possibly children, for <code>path</code> at <code>revision</code>. 
     * The actual node revision selected is determined by the path as it exists in <code>pegRevision</code>. 
     * If <code>pegRevision</code> is {@link SVNRevision#isValid() invalid}, then it defaults to 
     * {@link SVNRevision#WORKING}.
     * 
     * <p/>
     * Reports directory entries by invoking <code>handler</code>, 
     * {@link SVNDirEntry#setRelativePath(String) setting} a relative to <code>path</code> path to the 
     * {@link SVNDirEntry} object. The directory entry for <code>path</code> is reported using an empty path. 
     * If <code>path</code> is a directory, also reports its children.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, lists just <code>path</code> itself.
     * If <code>depth</code> is {@link SVNDepth#FILES}, lists <code>path</code> and its file
     * entries. If {@link SVNDepth#IMMEDIATES}, lists its immediate file and directory entries. 
     * If {@link SVNDepth#INFINITY}, lists file entries and recurses (with {@link SVNDepth#INFINITY}) on 
     * directory entries.
     * 
     * <p/>
     * <code>entryFields</code> controls which fields in the {@link SVNDirEntry} are filled in. 
     * To have them totally filled in use {@link SVNDirEntry#DIRENT_ALL}, otherwise simply bitwise OR 
     * together the combination of fields you care about.
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  path           a WC item to get its repository location            
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  fetchLocks     <span class="javakeyword">true</span>, includes locks when 
     *                        reporting directory entries
     * @param  depth          tree depth to process 
     * @param  entryFields    entry fields to fill
     * @param  handler        a caller's directory entry handler (to process info on an entry)
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#FS_NOT_FOUND} error code - 
     *                        if <code>path</code> is non-existent in the repository 
     *                        <ul/>
     * @since                 1.2, SVN 1.5
     */
    public void doList(File path, SVNRevision pegRevision, SVNRevision revision, boolean fetchLocks, 
            SVNDepth depth, int entryFields, ISVNDirEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doList(path, pegRevision, revision, fetchLocks, depth, entryFields, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doList(path, pegRevision, revision, fetchLocks, depth, entryFields, handler);
            } else {
                throw e;
            }
        }
    }

    /**
     * Browses directory entries from a repository (using Working 
     * Copy paths to get corresponding URLs) and uses the provided dir 
     * entry handler to process them.
     * 
     * <p>
     * On every entry that this method stops it gets some useful entry 
     * information which is packed into an {@link org.tmatesoft.svn.core.SVNDirEntry}
     * object and passed to the <code>handler</code>'s 
     * {@link org.tmatesoft.svn.core.ISVNDirEntryHandler#handleDirEntry(SVNDirEntry) handleDirEntry()} method.
     *  
     * @param  path           a WC item to get its repository location            
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively (relevant for directories)    
     * @param  handler        a caller's directory entry handler (to process
     *                        info on an entry)
     * @throws SVNException
     * @deprecated            use {@link #doList(File, SVNRevision, SVNRevision, boolean, SVNDepth, int, ISVNDirEntryHandler)}
     *                        instead 
     */
    public void doList(File path, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doList(path, pegRevision, revision, recursive, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doList(path, pegRevision, revision, recursive, handler);
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Browses directory entries from a repository and uses the provided 
     * dir entry handler to process them. This method is 
     * especially useful when having no Working Copy. 
     * 
     * <p>
     * On every entry that this method stops it gets some useful entry 
     * information which is packed into an {@link org.tmatesoft.svn.core.SVNDirEntry}
     * object and passed to the <code>handler</code>'s 
     * {@link org.tmatesoft.svn.core.ISVNDirEntryHandler#handleDirEntry(SVNDirEntry) handleDirEntry()} method.
     * 
     * @param  url            a repository location to be "listed"
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  fetchLocks     <span class="javakeyword">true</span> to 
     *                        fetch locks information from repository
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively (relevant for directories)    
     * @param  handler        a caller's directory entry handler (to process
     *                        info on an entry)
     * @throws SVNException
     * @see                   #doList(File, SVNRevision, SVNRevision, boolean, ISVNDirEntryHandler)   
     */
    public void doList(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean fetchLocks, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doList(url, pegRevision, revision, fetchLocks, recursive, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doList(url, pegRevision, revision, fetchLocks, recursive, handler);
            } else {
                throw e;
            }
        }
    }
    
    /**
     * Reports the directory entry, and possibly children, for <code>url</code> at <code>revision</code>. 
     * The actual node revision selected is determined by the path as it exists in <code>pegRevision</code>. 
     * If <code>pegRevision</code> is {@link SVNRevision#isValid() invalid}, then it defaults to 
     * {@link SVNRevision#HEAD}.
     * 
     * <p/>
     * Reports directory entries by invoking <code>handler</code>, 
     * {@link SVNDirEntry#setRelativePath(String) setting} a relative to <code>url</code> path to the 
     * {@link SVNDirEntry} object. The directory entry for <code>url</code> is reported using an empty path. 
     * If <code>url</code> is a directory, also reports its children.
     * 
     * <p/>
     * If <code>depth</code> is {@link SVNDepth#EMPTY}, lists just <code>url</code> itself.
     * If <code>depth</code> is {@link SVNDepth#FILES}, lists <code>url</code> and its file
     * entries. If {@link SVNDepth#IMMEDIATES}, lists its immediate file and directory entries. 
     * If {@link SVNDepth#INFINITY}, lists file entries and recurses (with {@link SVNDepth#INFINITY}) on 
     * directory entries.
     * 
     * <p/>
     * <code>entryFields</code> controls which fields in the {@link SVNDirEntry} are filled in. 
     * To have them totally filled in use {@link SVNDirEntry#DIRENT_ALL}, otherwise simply bitwise OR 
     * together the combination of fields you care about.
     *
     * <p/>
     * Note: this routine requires repository access.
     * 
     * @param  url            a repository url to be "listed"
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  fetchLocks     <span class="javakeyword">true</span>, includes locks when 
     *                        reporting directory entries
     * @param  depth          tree depth to process 
     * @param  entryFields    entry fields to fill
     * @param  handler        a caller's directory entry handler (to process info on an entry)
     * @throws SVNException   in the following cases:
     *                        <ul>
     *                        <li/>exception with {@link SVNErrorCode#FS_NOT_FOUND} error code - 
     *                        if <code>url</code> is non-existent in the repository 
     *                        <ul/>
     * @since                 1.2, SVN 1.5
     */
    public void doList(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean fetchLocks, 
            SVNDepth depth, int entryFields, ISVNDirEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doList(url, pegRevision, revision, fetchLocks, depth, entryFields, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doList(url, pegRevision, revision, fetchLocks, depth, entryFields, handler);
            } else {
                throw e;
            }
        }
    }

    /**
     * Browses directory entries from a repository and uses the provided 
     * dir entry handler to process them. This method is 
     * especially useful when having no Working Copy. 
     * 
     * <p>
     * On every entry that this method stops it gets some useful entry 
     * information which is packed into an {@link org.tmatesoft.svn.core.SVNDirEntry}
     * object and passed to the <code>handler</code>'s 
     * {@link org.tmatesoft.svn.core.ISVNDirEntryHandler#handleDirEntry(SVNDirEntry) handleDirEntry()} method.
     * 
     * @param  url            a repository location to be "listed"
     * @param  pegRevision    a revision in which the item's URL is first looked up
     * @param  revision       a target revision
     * @param  recursive      <span class="javakeyword">true</span> to
     *                        descend recursively (relevant for directories)    
     * @param  handler        a caller's directory entry handler (to process
     *                        info on an entry)
     * @throws SVNException
     * @deprecated            use {@link #doList(SVNURL, SVNRevision, SVNRevision, boolean, SVNDepth, int, ISVNDirEntryHandler)} 
     *                        instead
     */
    public void doList(SVNURL url, SVNRevision pegRevision, SVNRevision revision, boolean recursive, ISVNDirEntryHandler handler) throws SVNException {
        try {
            getSVNLogClient17().doList(url, pegRevision, revision, recursive, handler);
        } catch (SVNException e) {
            if (e.getErrorMessage().getErrorCode() == SVNErrorCode.VERSION_MISMATCH) {
                getSVNLogClient16().doList(url, pegRevision, revision, recursive, handler);
            } else {
                throw e;
            }
        }
    }
    
}
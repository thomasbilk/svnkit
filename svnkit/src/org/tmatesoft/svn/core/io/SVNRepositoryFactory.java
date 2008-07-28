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

package org.tmatesoft.svn.core.io;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSFS;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.util.SVNUUIDGenerator;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileListUtil;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.SVNWCProperties;
import org.tmatesoft.svn.core.internal.wc.admin.SVNTranslator;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * <b>SVNRepositoryFactory</b> is an abstract factory that is responsible
 * for creating an appropriate <b>SVNRepository</b> driver specific for the 
 * protocol to use.
 * 
 * <p>
 * Depending on what protocol a user exactly would like to use
 * to access the repository he should first of all set up an 
 * appropriate extension of this factory. So, if the user is going to
 * work with the repository via the custom <i>svn</i>-protocol (or 
 * <i>svn+xxx</i>) he initially calls:
 * <pre class="javacode">
 * ...
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
 * ...      
 *     <span class="javacomment">//do it once in your application prior to using the library</span>
 *     <span class="javacomment">//enables working with a repository via the svn-protocol (over svn and svn+ssh)</span>
 *     SVNRepositoryFactoryImpl.setup();
 * ...</pre><br />
 * From this point the <b>SVNRepositoryFactory</b> knows how to create
 * <b>SVNRepository</b> instances specific for the <i>svn</i>-protocol.
 * And further on the user can create an <b>SVNRepository</b> instance:
 * <pre class="javacode">
 *     ...
 *     <span class="javacomment">//creating a new SVNRepository instance</span>
 *     String url = <span class="javastring">"svn://host/path"</span>;
 *     SVNRepository repository = SVNRepositoryFactory.create(SVNURL.parseURIDecoded(url));
 *     ...</pre><br />
 * 
 * <table cellpadding="3" cellspacing="1" border="0" width="70%" bgcolor="#999933">
 * <tr bgcolor="#ADB8D9" align="left">
 * <td><b>Supported Protocols</b></td>
 * <td><b>Factory to setup</b></td>
 * </tr>   
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>svn://, svn+xxx://</td><td>SVNRepositoryFactoryImpl (<b>org.tmatesoft.svn.core.internal.io.svn</b>)</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>http://, https://</td><td>DAVRepositoryFactory (<b>org.tmatesoft.svn.core.internal.io.dav</b>)</td>
 * </tr>
 * <tr bgcolor="#EAEAEA" align="left">
 * <td>file:/// (FSFS only)</td><td>FSRepositoryFactory (<b>org.tmatesoft.svn.core.internal.io.fs</b>)</td>
 * </tr>
 * </table>
 * 
 * <p>
 * Also <b>SVNRepositoryFactory</b> may be used to create local 
 * FSFS-type repositories.
 * 
 * @version 1.1.1
 * @author  TMate Software Ltd.
 * @see     SVNRepository
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public abstract class SVNRepositoryFactory {
    
    private static final Map myFactoriesMap = new SVNHashMap();
    private static final String REPOSITORY_TEMPLATE_PATH = "/org/tmatesoft/svn/core/io/repository/template.jar";
    
    protected static void registerRepositoryFactory(String protocol, SVNRepositoryFactory factory) {
        if (protocol != null && factory != null) {
            synchronized (myFactoriesMap) {
                myFactoriesMap.put(protocol, factory);
            }
        }
    }
    
    protected static boolean hasRepositoryFactory(String protocol) {
        if (protocol != null) {
            synchronized (myFactoriesMap) {
                return myFactoriesMap.get(protocol) != null;
            }
        }
        return false;
    }
    
    /**
     * Creates an <b>SVNRepository</b> driver according to the protocol that is to be 
     * used to access a repository.
     * 
     * <p>
     * The protocol is defined as the beginning part of the URL schema. Currently
     * SVNKit supports only <i>svn://</i> (<i>svn+ssh://</i>) and <i>http://</i> (<i>https://</i>)
     * schemas.
     * 
     * <p>
     * The created <b>SVNRepository</b> driver can later be <i>"reused"</i> for another
     * location - that is you can switch it to another repository url not to
     * create yet one more <b>SVNRepository</b> object. Use the {@link SVNRepository#setLocation(SVNURL, boolean) SVNRepository.setLocation()} 
     * method for this purpose.
     * 
     * <p>
     * An <b>SVNRepository</b> driver created by this method uses a default
     * session options driver ({@link ISVNSession#DEFAULT}) which does not 
     * allow to keep a single socket connection opened and commit log messages
     * caching.     
     * 
     * @param  url              a repository location URL  
     * @return                  a protocol specific <b>SVNRepository</b> driver
     * @throws SVNException     if there's no implementation for the specified protocol
     *                          (the user may have forgotten to register a specific 
     *                          factory that creates <b>SVNRepository</b>
     *                          instances for that protocol or the SVNKit 
     *                          library does not support that protocol at all)
     * @see                     #create(SVNURL, ISVNSession)
     * @see                     SVNRepository
     * 
     */
    public static SVNRepository create(SVNURL url) throws SVNException {
        return create(url, null);
        
    }
    
    /**
     * Creates an <b>SVNRepository</b> driver according to the protocol that is to be 
     * used to access a repository.
     * 
     * <p>
     * The protocol is defined as the beginning part of the URL schema. Currently
     * SVNKit supports only <i>svn://</i> (<i>svn+ssh://</i>) and <i>http://</i> (<i>https://</i>)
     * schemas.
     * 
     * <p>
     * The created <b>SVNRepository</b> driver can later be <i>"reused"</i> for another
     * location - that is you can switch it to another repository url not to
     * create yet one more <b>SVNRepository</b> object. Use the {@link SVNRepository#setLocation(SVNURL, boolean) SVNRepository.setLocation()} 
     * method for this purpose.
     * 
     * <p>
     * This method allows to customize a session options driver for an <b>SVNRepository</b> driver.
     * A session options driver must implement the <b>ISVNSession</b> interface. It manages socket
     * connections - says whether an <b>SVNRepository</b> driver may use a single socket connection
     * during the runtime, or it should open a new connection per each repository access operation.
     * And also a session options driver may cache and provide commit log messages during the
     * runtime. 
     * 
     * @param  url              a repository location URL  
     * @param  options          a session options driver
     * @return                  a protocol specific <b>SVNRepository</b> driver
     * @throws SVNException     if there's no implementation for the specified protocol
     *                          (the user may have forgotten to register a specific 
     *                          factory that creates <b>SVNRepository</b>
     *                          instances for that protocol or the SVNKit 
     *                          library does not support that protocol at all)
     * @see                     #create(SVNURL)
     * @see                     SVNRepository
     */
    public static SVNRepository create(SVNURL url, ISVNSession options) throws SVNException {
        String urlString = url.toString();
        synchronized (myFactoriesMap) {
            for(Iterator keys = myFactoriesMap.keySet().iterator(); keys.hasNext();) {
                String key = (String) keys.next();
                if (Pattern.matches(key, urlString)) {
                    return ((SVNRepositoryFactory) myFactoriesMap.get(key)).createRepositoryImpl(url, options);
                }
            }
        }
        if ("file".equalsIgnoreCase(url.getProtocol())) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.RA_LOCAL_REPOS_OPEN_FAILED, "Unable to open an ra_local session to URL");
            SVNErrorManager.error(err, SVNLogType.NETWORK);
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Unable to create SVNRepository object for ''{0}''", url);
        SVNErrorManager.error(err, SVNLogType.NETWORK);
        return null;
    }

    /**
     * Creates a local blank FSFS-type repository.
     * A call to this routine is equivalent to 
     * <code>createLocalRepository(path, null, enableRevisionProperties, force)</code>.
     * 
     * @param  path                          a repository root location
     * @param  enableRevisionProperties      enables or not revision property 
     *                                       modifications
     * @param  force                         forces operation to run               
     * @return                               a local URL (file:///) of a newly
     *                                       created repository
     * @throws SVNException
     * @see                                  #createLocalRepository(File, String, boolean, boolean)
     * @since                                1.1
     */
    public static SVNURL createLocalRepository(File path, boolean enableRevisionProperties, boolean force) throws SVNException {
        return createLocalRepository(path, null, enableRevisionProperties, force);
    }
    
    /**
     * Creates a local blank FSFS-type repository. This is just similar to 
     * the Subversion's command: <code>svnadmin create --fs-type=fsfs REPOS_PATH</code>.
     * The resultant repository is absolutely format-compatible with Subversion.
     * 
     * <p>
     * If <code>uuid</code> is <span class="javakeyword">null</span> or not 36 chars 
     * wide, the method generates a new UUID for the repository. This UUID would have 
     * the same format as if it's generated by Subversion itself.
     * 
     * <p>
     * If <code>enableRevisionProperties</code> is <span class="javakeyword">true</span>
     * then the method creates a <code>pre-revprop-change</code> executable file inside 
     * the <code>"hooks"</code> subdir of the repository tree. This executable file 
     * simply returns 0 thus allowing revision property modifications, which are not 
     * permitted, unless one puts such a hook into that very directory.   
     *
     * <p>
     * If <code>force</code> is <span class="javakeyword">true</span> and <code>path</code> already 
     * exists, deletes that path and creates a repository in its place. 
     * 
     * <p>
     * A call to this routine is equivalent to 
     * <code>createLocalRepository(path, uuid, enableRevisionProperties, force, false)</code>.
     * 
     * @param  path                          a repository root location
     * @param  uuid                          a repository's uuid
     * @param  enableRevisionProperties      enables or not revision property 
     *                                       modifications
     * @param  force                         forces operation to run               
     * @return                               a local URL (file:///) of a newly
     *                                       created repository
     * @throws SVNException
     * @see                                  #createLocalRepository(File, String, boolean, boolean, boolean)
     * @since                                1.1
     */
    public static SVNURL createLocalRepository(File path, String uuid, boolean enableRevisionProperties, boolean force) throws SVNException {
        return createLocalRepository(path, uuid, enableRevisionProperties, force, false);
    }

    /**
     * Creates a local blank FSFS-type repository. This is just similar to 
     * the Subversion's command: <code>svnadmin create --fs-type=fsfs REPOS_PATH</code>.
     * The resultant repository is absolutely format-compatible with Subversion.
     * 
     * <p>
     * If <code>uuid</code> is <span class="javakeyword">null</span> or not 36 chars 
     * wide, the method generates a new UUID for the repository. This UUID would have 
     * the same format as if it's generated by Subversion itself.
     * 
     * <p>
     * If <code>enableRevisionProperties</code> is <span class="javakeyword">true</span>
     * then the method creates a <code>pre-revprop-change</code> executable file inside 
     * the <code>"hooks"</code> subdir of the repository tree. This executable file 
     * simply returns 0 thus allowing revision property modifications, which are not 
     * permitted, unless one puts such a hook into that very directory.   
     *
     * <p>
     * If <code>force</code> is <span class="javakeyword">true</span> and <code>path</code> already 
     * exists, deletes that path and creates a repository in its place. 
     * 
     * <p>
     * Set <code>pre14Compatible</code> to <span class="javakeyword">true</span> if you want a new repository 
     * to be compatible with pre-1.4 servers.
     *  
     * @param  path                          a repository root location
     * @param  uuid                          a repository's uuid
     * @param  enableRevisionProperties      enables or not revision property 
     *                                       modifications
     * @param  force                         forces operation to run               
     * @param  pre14Compatible               <span class="javakeyword">true</span> to 
     *                                       create a repository with pre-1.4 format
     * @return                               a local URL (file:///) of a newly
     *                                       created repository
     * @throws SVNException
     * @since                               1.1.1
     */
    public static SVNURL createLocalRepository(File path, String uuid, boolean enableRevisionProperties, boolean force, boolean pre14Compatible) throws SVNException {
        return createLocalRepository(path, uuid, enableRevisionProperties, force, pre14Compatible, false);
    }
    
    public static SVNURL createLocalRepository(File path, String uuid, boolean enableRevisionProperties, boolean force, boolean pre14Compatible, boolean pre15Compatible) throws SVNException {
        SVNFileType fType = SVNFileType.getType(path);
        if (fType != SVNFileType.NONE) {
            if (fType == SVNFileType.DIRECTORY) {
                File[] children = SVNFileListUtil.listFiles(path);
                if ( children != null && children.length != 0) {
                    if (!force) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "''{0}'' already exists; use ''force'' to overwrite existing files", path);
                        SVNErrorManager.error(err, SVNLogType.NETWORK);
                    } else {
                        SVNFileUtil.deleteAll(path, true);
                    }
                }
            } else {
                if (!force) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "''{0}'' already exists; use ''force'' to overwrite existing files", path);
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                } else {
                    SVNFileUtil.deleteAll(path, true);
                }
            }
        }
        //SVNFileUtil.deleteAll(path, true);
        if (!path.mkdirs() && !path.exists()) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can not create directory ''{0}''", path);
            SVNErrorManager.error(err, SVNLogType.NETWORK);
        }
        InputStream is = SVNRepositoryFactory.class.getResourceAsStream(REPOSITORY_TEMPLATE_PATH);
        if (is == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "No repository template found; should be part of SVNKit library jar");
            SVNErrorManager.error(err, SVNLogType.NETWORK);
        }
        File jarFile = SVNFileUtil.createUniqueFile(path, ".template", ".jar", true);
        OutputStream uuidOS = null; 
        OutputStream reposFormatOS = null;
        OutputStream fsFormatOS = null;
        OutputStream txnCurrentOS = null;
        OutputStream currentOS = null;
        try {
            copyToFile(is, jarFile);
            extract(jarFile, path);
            SVNFileUtil.deleteFile(jarFile);
            // translate eols.
            if (!SVNFileUtil.isWindows) {
                translateFiles(path);
                translateFiles(new File(path, "conf"));
                translateFiles(new File(path, "hooks"));
                translateFiles(new File(path, "locks"));
            }
            // create pre-rev-prop.
            if (enableRevisionProperties) {
                if (SVNFileUtil.isWindows) {
                    File hookFile = new File(path, "hooks/pre-revprop-change.bat");
                    OutputStream os = SVNFileUtil.openFileForWriting(hookFile);
                    try {
                        os.write("@echo off".getBytes());
                    } catch (IOException e) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create pre-rev-prop-change hook file at ''{0}'': {1}", 
                                new Object[] {hookFile, e.getLocalizedMessage()});
                        SVNErrorManager.error(err, SVNLogType.NETWORK);
                    } finally {
                        SVNFileUtil.closeFile(os);
                    }
                } else {
                    File hookFile = new File(path, "hooks/pre-revprop-change");
                    OutputStream os = null;
                    try {
                        os = SVNFileUtil.openFileForWriting(hookFile);
                        os.write("#!/bin/sh\nexit 0".getBytes("US-ASCII"));                        
                    } catch (IOException e) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Cannot create pre-rev-prop-change hook file at ''{0}'': {1}", 
                                new Object[] {hookFile, e.getLocalizedMessage()});
                        SVNErrorManager.error(err, SVNLogType.NETWORK);
                    } finally {
                        SVNFileUtil.closeFile(os);
                    }
                    SVNFileUtil.setExecutable(hookFile, true);
                }
            }
            // generate and write UUID.
            File uuidFile = new File(path, "db/uuid");
            if (uuid == null || uuid.length() != 36) {
                byte[] uuidBytes = SVNUUIDGenerator.generateUUID();
                uuid = SVNUUIDGenerator.formatUUID(uuidBytes);
            } 
            uuid += '\n'; 
            try {
                uuidOS = SVNFileUtil.openFileForWriting(uuidFile);
                uuidOS.write(uuid.getBytes("US-ASCII"));
            } catch (IOException e) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Error writing repository UUID to ''{0}''", uuidFile);
                err.setChildErrorMessage(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage()));
                SVNErrorManager.error(err, SVNLogType.NETWORK);
            }
            
            int fsFormat = FSFS.DB_FORMAT;
            if (pre14Compatible) {
                File reposFormatFile = new File(path, "format");
                try {
                    reposFormatOS = SVNFileUtil.openFileForWriting(reposFormatFile);
                    String format = String.valueOf(FSFS.REPOSITORY_FORMAT_LEGACY);
                    format += '\n';
                    reposFormatOS.write(format.getBytes("US-ASCII"));
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Error writing repository format to ''{0}''", reposFormatFile);
                    err.setChildErrorMessage(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage()));
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                }
            }
                
            if (pre14Compatible || pre15Compatible) {
                fsFormat = pre14Compatible ? 1 : 2; 
                File fsFormatFile = new File(path, "db/format");
                try {
                    fsFormatOS = SVNFileUtil.openFileForWriting(fsFormatFile);
                    String format = String.valueOf(fsFormat);
                    format += '\n';
                    fsFormatOS.write(format.getBytes("US-ASCII"));
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                            "Error writing fs format to ''{0}''", fsFormatFile);
                    err.setChildErrorMessage(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage()));
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                }
            }
            
            long maxFilesPerDir = 0;
            if (fsFormat >= FSFS.LAYOUT_FORMAT_OPTION_MINIMAL_FORMAT) {
                maxFilesPerDir = FSFS.getDefaultMaxFilesPerDirectory();
                File fsFormatFile = new File(path, "db/format");
                try {
                    fsFormatOS = SVNFileUtil.openFileForWriting(fsFormatFile);
                    String format = String.valueOf(fsFormat) + "\n";
                    if (maxFilesPerDir > 0) {
                        File revFileBefore = new File(path, "db/revs/0");
                        File tmpFile = SVNFileUtil.createUniqueFile(new File(path, "db/revs"), "0", "tmp", true);
                        SVNFileUtil.rename(revFileBefore, tmpFile);
                        File shardRevDir = new File(path, "db/revs/0");
                        shardRevDir.mkdirs();
                        File revFileAfter = new File(shardRevDir, "0");
                        SVNFileUtil.rename(tmpFile, revFileAfter);

                        File revPropFileBefore = new File(path, "db/revprops/0");
                        tmpFile = SVNFileUtil.createUniqueFile(new File(path, "db/revprops"), "0", "tmp", true);
                        SVNFileUtil.rename(revPropFileBefore, tmpFile);
                        File shardRevPropDir = new File(path, "db/revprops/0");
                        shardRevPropDir.mkdirs();
                        File revPropFileAfter = new File(shardRevPropDir, "0");
                        SVNFileUtil.rename(tmpFile, revPropFileAfter);

                        format += "layout sharded " + String.valueOf(maxFilesPerDir) + "\n";
                    } else {
                        format += "layout linear\n";
                    }
                    fsFormatOS.write(format.getBytes("US-ASCII"));
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Error writing fs format to ''{0}''", fsFormatFile);
                    err.setChildErrorMessage(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage()));
                    SVNErrorManager.error(err, SVNLogType.NETWORK);
                }
            }
            
            if (fsFormat >= FSFS.MIN_NO_GLOBAL_IDS_FORMAT) {
                File currentFile = new File(path, "db/" + FSFS.CURRENT_FILE);
                currentOS = SVNFileUtil.openFileForWriting(currentFile);
                try {
                    currentOS.write("0\n".getBytes("US-ASCII"));
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                            "Can not write to ''{0}'' file: {1}", 
                            new Object[] { currentFile.getName(), e.getLocalizedMessage() });
                    SVNErrorManager.error(err, e, SVNLogType.NETWORK);
                }
            }
            
            if (fsFormat >= FSFS.MIN_CURRENT_TXN_FORMAT) {
                File txnCurrentFile = new File(path, "db/" + FSFS.TXN_CURRENT_FILE);
                SVNFileUtil.createEmptyFile(txnCurrentFile);
                txnCurrentOS = SVNFileUtil.openFileForWriting(txnCurrentFile);
                try {
                    txnCurrentOS.write("0\n".getBytes("US-ASCII"));
                } catch (IOException e) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, 
                            "Can not write to ''{0}'' file: {1}", 
                            new Object[] { txnCurrentFile.getName(), e.getLocalizedMessage() });
                    SVNErrorManager.error(err, e, SVNLogType.NETWORK);
                }
            }
            
            if (fsFormat >= FSFS.MIN_PROTOREVS_DIR_FORMAT) {
                File protoRevsDir = new File(path, "db/txn-protorevs");
                protoRevsDir.mkdirs();
            }

            // set creation date.
            File rev0File = new File(path, maxFilesPerDir > 0 ? "db/revprops/0/0" : "db/revprops/0");
            SVNWCProperties props = new SVNWCProperties(rev0File, null);
            String date = SVNDate.formatDate(new Date(System.currentTimeMillis()), true);
            props.setPropertyValue(SVNRevisionProperty.DATE, date);
        
            setSGID(new File(path, FSFS.DB_DIR));
        } finally {
            SVNFileUtil.closeFile(uuidOS);
            SVNFileUtil.closeFile(reposFormatOS);
            SVNFileUtil.closeFile(fsFormatOS);
            SVNFileUtil.closeFile(txnCurrentOS);
            SVNFileUtil.closeFile(currentOS);
            SVNFileUtil.deleteFile(jarFile);
        }
        return SVNURL.fromFile(path);
    }

    protected abstract SVNRepository createRepositoryImpl(SVNURL url, ISVNSession session);
    
    private static void copyToFile(InputStream is, File dstFile) throws SVNException {
        OutputStream os = null; 
        byte[] buffer = new byte[16*1024];
        try {
            os = SVNFileUtil.openFileForWriting(dstFile);
            while(true) {
                int r = is.read(buffer);
                if (r <= 0) {
                    break;
                }
                os.write(buffer, 0, r);
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can not copy repository template file to ''{0}''", dstFile);
            err.setChildErrorMessage(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage()));
            SVNErrorManager.error(err, SVNLogType.NETWORK);
        } finally {
            SVNFileUtil.closeFile(os);
            SVNFileUtil.closeFile(is);
        }
    }

    private static void extract(File srcFile, File dst) throws SVNException {
        JarInputStream jis = null;
        InputStream is = SVNFileUtil.openFileForReading(srcFile, SVNLogType.NETWORK);
        byte[] buffer = new byte[16*1024];
        
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(srcFile);
            jis = new JarInputStream(is);
            while(true) {
                JarEntry entry = jis.getNextJarEntry();
                if (entry == null) {
                    break;
                }
                String name = entry.getName();
                File entryFile = new File(dst, name); 
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    InputStream fis = null;
                    OutputStream fos = null;
                    try {
                        fis = new BufferedInputStream(jarFile.getInputStream(entry));
                        fos = SVNFileUtil.openFileForWriting(entryFile);
                        while(true) {
                            int r = fis.read(buffer);
                            if (r <= 0) {
                                break;
                            }
                            fos.write(buffer, 0, r);
                        }
                    } finally {
                        SVNFileUtil.closeFile(fos);
                        SVNFileUtil.closeFile(fis);
                    }
                }
                jis.closeEntry();
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can not extract repository files from ''{0}'' to ''{1}''", 
                    new Object[] {srcFile, dst});
            err.setChildErrorMessage(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage()));
            SVNErrorManager.error(err, SVNLogType.NETWORK);
        } finally {
            SVNFileUtil.closeFile(jis);
            SVNFileUtil.closeFile(is);
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                }
            }
        }
    }
    
    private static void translateFiles(File directory) throws SVNException {
        File[] children = SVNFileListUtil.listFiles(directory);
        byte[] eol = new byte[] {'\n'};
        for (int i = 0; children != null && i < children.length; i++) {
            File child = children[i];
            File tmpChild = null;
            if (child.isFile()) {
                try {
                    tmpChild = SVNFileUtil.createUniqueFile(directory, ".repos", ".tmp", true);
                    SVNTranslator.translate(child, tmpChild, null, eol, null, false, true);
                    SVNFileUtil.deleteFile(child);
                    SVNFileUtil.rename(tmpChild, child);
                } finally {
                    SVNFileUtil.deleteFile(tmpChild);
                }
            }
        }
    }
    
    private static void setSGID(File dbDir) {
        SVNFileUtil.setSGID(dbDir);
        File[] dirContents = dbDir.listFiles();
        for(int i = 0; i < dirContents.length; i++) {
            File child = dirContents[i];
            if (child.isDirectory()) {
                setSGID(child);
            }
        }
    }
}

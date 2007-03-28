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
package org.tmatesoft.svn.core.internal.wc.admin;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.internal.util.SVNEncodingUtil;
import org.tmatesoft.svn.core.internal.util.SVNFormatUtil;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.util.SVNTimeUtil;
import org.tmatesoft.svn.core.internal.wc.IOExceptionWrapper;
import org.tmatesoft.svn.core.internal.wc.SVNAdminUtil;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.wc.ISVNOptions;


/**
 * @version 1.1.1
 * @author  TMate Software Ltd.
 */
public class SVNTranslator {
    public static final byte[] CRLF = new byte[] { '\r', '\n' };

    public static final byte[] LF = new byte[] { '\n' };

    public static final byte[] CR = new byte[] { '\r' };

    public static final byte[] NATIVE = System.getProperty("line.separator")
            .getBytes();

    private static final String NATIVE_STYLE = "native";
    private static final String CRLF_STYLE = "CRLF";
    private static final String CR_STYLE = "CR";
    private static final String LF_STYLE = "LF";
    
    public static void translate(SVNAdminArea adminArea, String name, String srcPath,
            String dstPath, boolean expand) throws SVNException {
        translate(adminArea, name, srcPath, dstPath, null, expand);
    }

    public static void translate(SVNAdminArea adminArea, String name, String srcPath,
            String dstPath, String customEOLStyle, boolean expand) throws SVNException {
        translate(adminArea, name, adminArea.getFile(srcPath), adminArea.getFile(dstPath), customEOLStyle, expand);
    }
    
    public static void translate(SVNAdminArea adminArea, String name, File src,
            File dst, boolean expand) throws SVNException {
        translate(adminArea, name, src, dst, null, expand);
    }
    
    public static void translate(SVNAdminArea adminArea, String name, File src,
            File dst, String customEOLStyle, boolean expand) throws SVNException {
        File dst2 = dst;
        
        SVNVersionedProperties props = adminArea.getProperties(name);
        String keywords = props.getPropertyValue(SVNProperty.KEYWORDS);
        String eolStyle = null;
        if (customEOLStyle != null) {
            eolStyle = customEOLStyle;
        } else {
            eolStyle = props.getPropertyValue(SVNProperty.EOL_STYLE);
        }
        boolean special = props.getPropertyValue(SVNProperty.SPECIAL) != null;
        Map keywordsMap = null;
        byte[] eols;
        if (keywords != null) {            
            if (expand) {
                SVNEntry entry = adminArea.getEntry(name, true);
                ISVNOptions options = adminArea.getWCAccess().getOptions();
                String url = entry.getURL();
                String author = entry.getAuthor();
                String date = entry.getCommittedDate();
                String rev = Long.toString(entry.getCommittedRevision());
                keywordsMap = computeKeywords(keywords, url, author, date, rev, options);
            } else {
                keywordsMap = computeKeywords(keywords, null, null, null, null, null);
            }
        }
        if (!expand) {
            eols = getBaseEOL(eolStyle);
        } else {
            eols = getWorkingEOL(eolStyle);
        }
        
        
        translate(src, dst2, eols, keywordsMap, special, expand);
    }

    public static void translate(File src, File dst, byte[] eol, Map keywords, boolean special, boolean expand) throws SVNException {
        if (src == null || dst == null) {
            SVNErrorManager.error(SVNErrorMessage.create(SVNErrorCode.INCORRECT_PARAMS));
            return;
        }
        if (src.equals(dst)) {
            return;
        }
        if (special) {
            if (dst.exists()) {
                dst.delete();
            }
            if (SVNFileUtil.isWindows) {
                SVNFileUtil.copyFile(src, dst, true);
            } else if (expand) {
                // create symlink to target, and create it at dst
                SVNFileUtil.createSymlink(dst, src);
            } else {
                SVNFileUtil.detranslateSymlink(src, dst);
            }
            return;

        }        
        if (eol == null && (keywords == null || keywords.isEmpty())) {
            // no expansion, fast copy.
            SVNFileUtil.copyFile(src, dst, false);
            return;
        }
        OutputStream os = SVNFileUtil.openFileForWriting(dst);
        InputStream is = SVNFileUtil.openFileForReading(src);
        try {
            copy(is, os, eol, keywords);
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            SVNFileUtil.closeFile(os);
            SVNFileUtil.closeFile(is);
        }
    }

    public static File getTranslatedFile(SVNAdminArea dir, String name, File src, boolean forceEOLRepair, boolean useGlobalTmp, boolean forceCopy, boolean toNormalFormat) throws SVNException {
        String eolStyle = dir.getProperties(name).getPropertyValue(SVNProperty.EOL_STYLE);
        String keywords = dir.getProperties(name).getPropertyValue(SVNProperty.KEYWORDS);
        boolean special = dir.getProperties(name).getPropertyValue(SVNProperty.SPECIAL) != null;
        boolean needsTranslation = eolStyle != null || keywords != null || special;
        File result = null;
        if (!needsTranslation && !forceCopy) {
            result = src;
        } else {
            if (useGlobalTmp) {
                result = SVNFileUtil.createTempFile("svndiff", ".tmp");
            } else {
                result = SVNAdminUtil.createTmpFile(dir, name, ".tmp", true);
            }
            if (toNormalFormat) {
                translateToNormalForm(src, result, eolStyle, forceEOLRepair, keywords, special);
            } else {
                SVNEntry entry = dir.getEntry(name, true);
                ISVNOptions options = dir.getWCAccess().getOptions();
                String url = entry.getURL();
                String author = entry.getAuthor();
                String date = entry.getCommittedDate();
                String rev = Long.toString(entry.getCommittedRevision());
                Map keywordsMap = computeKeywords(keywords, url, author, date, rev, options);
                copyAndTranslate(src, result, getEOL(eolStyle), keywordsMap, special, true, true);
            }
        }
        return result;
    }
    
    public static File maybeUpdateTargetEOLs(SVNAdminArea dir, File target, Map propDiff) throws SVNException {
        String eolStyle = null;
        if (propDiff != null && propDiff.containsKey(SVNProperty.EOL_STYLE) && propDiff.get(SVNProperty.EOL_STYLE) != null) {
            eolStyle = (String) propDiff.get(SVNProperty.EOL_STYLE);
            byte[] eol = getEOL(eolStyle);
            File tmpFile = SVNAdminUtil.createTmpFile(dir);
            copyAndTranslate(target, tmpFile, eol, null, false, false, eol == null);
            return tmpFile;
        } 
        return target;
    }
    
    public static File detranslateWorkingCopy(SVNAdminArea dir, String name, Map propDiff, boolean force) throws SVNException {
        SVNVersionedProperties props = dir.getProperties(name);
        boolean isLocalBinary = SVNProperty.isBinaryMimeType(props.getPropertyValue(SVNProperty.MIME_TYPE));
        
        String eolStyle = null;
        String keywords = null;
        boolean isSpecial = false;
        boolean isRemoteHasBinary = propDiff != null && propDiff.containsKey(SVNProperty.MIME_TYPE); 
        boolean isRemoteBinaryRemoved = isRemoteHasBinary && !SVNProperty.isBinaryMimeType((String) propDiff.get(SVNProperty.MIME_TYPE)); 
        boolean isRemoteBinary = isRemoteHasBinary && SVNProperty.isBinaryMimeType((String) propDiff.get(SVNProperty.MIME_TYPE)); 
        
        if (!isLocalBinary && isRemoteBinary) {
            isSpecial = props.getPropertyValue(SVNProperty.SPECIAL) != null;
            keywords = props.getPropertyValue(SVNProperty.KEYWORDS);
        } else if (!isLocalBinary || isRemoteBinaryRemoved) {
            isSpecial = props.getPropertyValue(SVNProperty.SPECIAL) != null;
            if (!isSpecial) {
                if (propDiff != null && propDiff.containsKey(SVNProperty.EOL_STYLE) && propDiff.get(SVNProperty.EOL_STYLE) != null) {
                    eolStyle = (String) propDiff.get(SVNProperty.EOL_STYLE);
                } else if (!isLocalBinary) {
                    eolStyle = props.getPropertyValue(SVNProperty.EOL_STYLE);
                }
                
                if (!isLocalBinary) {
                    keywords = props.getPropertyValue(SVNProperty.KEYWORDS);
                }
            }
        }

        File detranslatedFile = null;
        if (force || keywords != null || eolStyle != null || isSpecial) {
            File tmpFile = SVNAdminUtil.createTmpFile(dir);
            translateToNormalForm(dir.getFile(name), tmpFile, eolStyle, getEOL(eolStyle) == null, keywords, isSpecial);
            detranslatedFile = tmpFile;
        } else {
            detranslatedFile = dir.getFile(name);
        }
        
        return detranslatedFile;
    }
    
    private static void translateToNormalForm(File source, File destination, String eolStyle, boolean alwaysRepairEOLs, String keywords, boolean isSpecial) throws SVNException {
        byte[] eol = getBaseEOL(eolStyle);
        if (eolStyle != null && eol == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_UNKNOWN_EOL);
            SVNErrorManager.error(err);
        }
        
        Map keywordsMap = computeKeywords(keywords, null, null, null, null, null);
        boolean repair = (eolStyle != null && eol != null && !NATIVE_STYLE.equals(eolStyle)) || alwaysRepairEOLs; 
        copyAndTranslate(source, destination, eol, keywordsMap, isSpecial, false, repair);
    }

    private static void copyAndTranslate(File source, File destination, byte[] eol, Map keywords, boolean special, boolean expand, boolean repair) throws SVNException {
        boolean isSpecialPath = false;
        if (!SVNFileUtil.isWindows) {
            SVNFileType type = SVNFileType.getType(source);
            isSpecialPath = type == SVNFileType.SYMLINK;
        }
        
        if (special || isSpecialPath) {
            if (destination.exists()) {
                destination.delete();
            }
            if (SVNFileUtil.isWindows) {
                SVNFileUtil.copyFile(source, destination, true);
            } else if (expand) {
                // create symlink to target, and create it at dst
                SVNFileUtil.createSymlink(destination, source);
            } else {
                SVNFileUtil.detranslateSymlink(source, destination);
            }
            return;

        }        
        if (eol == null && (keywords == null || keywords.isEmpty())) {
            // no expansion, fast copy.
            SVNFileUtil.copyFile(source, destination, false);
            return;
        }

        OutputStream dst = null;
        InputStream src = null;
        OutputStream translatingStream = null;
        try {
            dst = SVNFileUtil.openFileForWriting(destination);
            src = SVNFileUtil.openFileForReading(source);
            translatingStream = new SVNTranslatorOutputStream(dst, eol, repair, keywords, expand);
            SVNTranslator.copy(src, translatingStream);
        } catch (IOExceptionWrapper ew) {
            if (ew.getOriginalException().getErrorMessage().getErrorCode() == SVNErrorCode.IO_INCONSISTENT_EOL) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_INCONSISTENT_EOL, "File ''{0}'' has inconsistent newlines", source);
                SVNErrorManager.error(err);
            }
            throw ew.getOriginalException();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, e.getLocalizedMessage());
            SVNErrorManager.error(err, e);
        } finally {
            try {
                dst.flush(); 
            } catch (IOException ioe) {
                //
            }
            SVNFileUtil.closeFile(src);
            SVNFileUtil.closeFile(translatingStream);
            SVNFileUtil.closeFile(dst);
        }
    }
    
    public static boolean checkNewLines(File file) {
        if (file == null || !file.exists() || file.isDirectory()) {
            return true;
        }
        InputStream is = null;
        try {
            is = SVNFileUtil.openFileForReading(file);
            int r;
            byte[] lastFoundEOL = null;
            byte[] currentEOL = null;
            while ((r = is.read()) >= 0) {
                if (r == '\n') {
                    currentEOL = LF;
                } else if (r == '\r') {
                    currentEOL = CR;
                    r = is.read();
                    if (r == '\n') {
                        currentEOL = CRLF;
                    }
                }
                if (lastFoundEOL == null) {
                    lastFoundEOL = currentEOL;
                } else if (currentEOL != null && lastFoundEOL != currentEOL) {
                    return false;
                }
            }
        } catch (IOException e) {
            return false;
        } catch (SVNException e) {
            return false;
        } finally {
            SVNFileUtil.closeFile(is);
        }
        return true;
    }
    
    public static void copy(InputStream src, OutputStream dst) throws IOException {
        byte[] buffer = new byte[8192];
        while(true) {
            int read = src.read(buffer);
            if (read <= 0) {
                return;
            }
            dst.write(buffer, 0, read);
        }
    }

    public static void copy(InputStream src, OutputStream dst, byte[] eol, Map keywords) throws IOException {
        if (keywords != null && keywords.isEmpty()) {
            keywords = null;
        }
        PushbackInputStream in = new PushbackInputStream(src, 2048);
        byte[] keywordBuffer = new byte[256];

        while (true) {
            int r = in.read();
            if (r < 0) {
                return;
            }
            if ((r == '\r' || r == '\n') && eol != null) {
                int next = in.read();
                dst.write(eol);
                if (r == '\r' && next == '\n') {
                    continue;
                }
                if (next < 0) {
                    return;
                }
                in.unread(next);
            } else if (r == '$' && keywords != null) {
                // advance in buffer for 256 more chars.
                dst.write(r);
                int length = in.read(keywordBuffer);
                int keywordLength = 0;
                for (int i = 0; i < length; i++) {
                    if (keywordBuffer[i] == '\r' || keywordBuffer[i] == '\n') {
                        // failure, save all before i, unread remains.
                        dst.write(keywordBuffer, 0, i);
                        in.unread(keywordBuffer, i, length - i);
                        keywordLength = -1;
                        break;
                    } else if (keywordBuffer[i] == '$') {
                        keywordLength = i + 1;
                        break;
                    }
                }
                if (keywordLength == 0) {
                    if (length > 0) {
                        dst.write(keywordBuffer, 0, length);
                    }
                } else if (keywordLength > 0) {
                    int from = translateKeyword(dst, keywords, keywordBuffer, keywordLength);
                    in.unread(keywordBuffer, from, length - from);
                }
            } else {
                dst.write(r);
            }
        }
    }

    private static int translateKeyword(OutputStream os, Map keywords, byte[] keyword, int length) throws IOException {
        // $$ = 0, 2 => 1,0
        String keywordName = null;
        int i = 0;
        for (i = 0; i < length; i++) {
            if (keyword[i] == '$' || keyword[i] == ':') {
                // from first $ to the offset i, exclusive
                keywordName = new String(keyword, 0, i, "UTF-8");
                break;
            }
            // write to os, we do not need it.
            os.write(keyword[i]);
        }

        if (!keywords.containsKey(keywordName)) {
            // unknown keyword, just write trailing chars.
            // already written is $keyword[i]..
            // but do not write last '$' - it could be a start of another
            // keyword.
            os.write(keyword, i, length - i - 1);
            return length - 1;
        }
        byte[] value = (byte[]) keywords.get(keywordName);
        // now i points to the first char after keyword name.
        if (length - i > 5 && keyword[i] == ':' && keyword[i + 1] == ':'
                && keyword[i + 2] == ' '
                && (keyword[length - 2] == ' ' || keyword[length - 2] == '#')) {
            // :: x $
            // fixed size keyword.
            // 1. write value to keyword
            int vOffset = 0;
            int start = i;
            for (i = i + 3; i < length - 2; i++) {
                if (value == null) {
                    keyword[i] = ' ';
                } else {
                    keyword[i] = vOffset < value.length ? value[vOffset] : (byte) ' ';
                }
                vOffset++;
            }
            keyword[i] = (byte) (value != null && vOffset < value.length ? '#' : ' ');
            // now save all.
            os.write(keyword, start, length - start);
        } else if (length - i > 4 && keyword[i] == ':' && keyword[i + 1] == ' ' && keyword[length - 2] == ' ') {
            // : x $
            if (value != null) {
                os.write(keyword, i, value.length > 0 ? 1 : 2); // ': ' or ':'
                if (value.length > 250) {
                    os.write(value, 0, 250);
                } else {
                    os.write(value);
                }
                os.write(keyword, length - 2, 2); // ' $';
            } else {
                os.write('$');
            }
        } else if (keyword[i] == '$' || (keyword[i] == ':' && keyword[i + 1] == '$')) {
            // $ or :$
            if (value != null) {
                os.write(':');
                os.write(' ');
                if (value.length > 250 - keywordName.length()) {
                    os.write(value, 0, 250 - keywordName.length());
                } else {
                    os.write(value);
                }
                if (value.length > 0) {
                    os.write(' ');
                }
                os.write('$');
            } else {
                os.write('$');
            }
        } else {
            // something wrong. write all, but not last $
            os.write(keyword, i, length - i - 1);
            return length - 1;
        }
        return length;

    }

    public static Map computeKeywords(String keywords, String u, String a, String d, String r, ISVNOptions options) {
        if (keywords == null) {
            return Collections.EMPTY_MAP;
        }
        boolean expand = u != null;
        byte[] date = null;
        byte[] idDate = null;
        byte[] url = null;
        byte[] rev = null;
        byte[] author = null;
        byte[] name = null;
        byte[] id = null;
        
        Date jDate = d == null ? null : SVNTimeUtil.parseDate(d);

        Map map = new HashMap();
        try {
            for (StringTokenizer tokens = new StringTokenizer(keywords," \t\n\b\r\f"); tokens.hasMoreTokens();) {
                String token = tokens.nextToken();
                if ("LastChangedDate".equalsIgnoreCase(token) || "Date".equalsIgnoreCase(token)) {
                    date = expand && date == null ? SVNFormatUtil.formatHumanDate(jDate, options).getBytes("UTF-8") : date;
                    map.put("LastChangedDate", date);
                    map.put("Date", date);
                } else if ("LastChangedRevision".equalsIgnoreCase(token) || "Revision".equalsIgnoreCase(token) || "Rev".equalsIgnoreCase(token)) {
                    rev = expand && rev == null ? r.getBytes("UTF-8") : rev;
                    map.put("LastChangedRevision", rev);
                    map.put("Revision", rev);
                    map.put("Rev", rev);
                } else if ("LastChangedBy".equalsIgnoreCase(token) || "Author".equalsIgnoreCase(token)) {
                    author = expand && author == null ? (a == null ? new byte[0] : a.getBytes("UTF-8")) : author;
                    map.put("LastChangedBy", author);
                    map.put("Author", author);
                } else if ("HeadURL".equalsIgnoreCase(token) || "URL".equalsIgnoreCase(token)) {
                    url = expand && url == null ? SVNEncodingUtil.uriDecode(u).getBytes("UTF-8") : url;
                    map.put("HeadURL", url);
                    map.put("URL", url);
                } else if ("Id".equalsIgnoreCase(token)) {
                    if (expand && id == null) {
                        rev = rev == null ? r.getBytes("UTF-8") : rev;
                        idDate = idDate == null ? SVNFormatUtil.formatDate(jDate).getBytes("UTF-8") : idDate;
                        name = name == null ? SVNEncodingUtil.uriDecode(SVNPathUtil.tail(u)).getBytes("UTF-8") : name;
                        author = author == null ? (a == null ? new byte[0] : a.getBytes("UTF-8")) : author;
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        bos.write(name);
                        bos.write(' ');
                        bos.write(rev);
                        bos.write(' ');
                        bos.write(idDate);
                        bos.write(' ');
                        bos.write(author);
                        bos.close();
                        id = bos.toByteArray();
                    }
                    map.put("Id", expand ? id : null);
                }
            }
        } catch (IOException e) {
            //
        }
        return map;
    }

    public static byte[] getEOL(String propertyValue) {
        if (NATIVE_STYLE.equals(propertyValue)) {
            return NATIVE;
        } else if (LF_STYLE.equals(propertyValue)) {
            return LF;
        } else if (CR_STYLE.equals(propertyValue)) {
            return CR;
        } else if (CRLF_STYLE.equals(propertyValue)) {
            return CRLF;
        }
        return null;
    }

    public static byte[] getBaseEOL(String eolStyle) {
        if (NATIVE_STYLE.equals(eolStyle)) {
            return LF;
        } else if (CR_STYLE.equals(eolStyle)) {
            return CR;
        } else if (LF_STYLE.equals(eolStyle)) {
            return LF;
        } else if (CRLF_STYLE.equals(eolStyle)) {
            return CRLF;
        }
        return null;
    }

    public static byte[] getWorkingEOL(String eolStyle) {
        if (NATIVE_STYLE.equals(eolStyle)) {
            return NATIVE;
        } else if (CR_STYLE.equals(eolStyle)) {
            return CR;
        } else if (LF_STYLE.equals(eolStyle)) {
            return LF;
        } else if (CRLF_STYLE.equals(eolStyle)) {
            return CRLF;
        }
        return null;
    }
}

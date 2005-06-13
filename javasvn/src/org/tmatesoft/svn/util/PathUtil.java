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

package org.tmatesoft.svn.util;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.internal.ws.fs.FSUtil;

/**
 * This class is a utility which aim is to help in work with path strings.
 * The <code>PathUtility</code> is used along with strings denoting relative
 * paths composed of entry names separated by '/' character (for example 
 * "/some/directory/some/file/").
 *   
 * @version 1.0
 * @author  TMate Software Ltd.
 * 
 */
public class PathUtil {
    /**
     * Determines if the path string contains no entry name. 
     * 
     * <p>
     * That is if the path is one of the following: <code>null</code>, "" or "/".
     * 
     * @param  path 	a path string that is to be checked up
     * @return 			<code>true</code> - if the path string contains no entries,
     * 					<code>false</code> otherwise 
     */
    public static final boolean isEmpty(String path) {
        return path == null || "".equals(path.trim()) || "/".equals(path.trim());
    }
    /**
     * Returns a substring of the original <code>path</code> string that doesn't
     * contain the last entry name. For example: 
     * <code>removeTail(<i>"some/path/to/entry/"</i>)</code>
     * @param path
     * @return
     */
    public static final String removeTail(String path) {
        if (isEmpty(path)) {
            return null;
        }
        path = removeTrailingSlash(path);
        int index = path.lastIndexOf('/');
        if (index <= 0) {
            return "/";
        }
        return path.substring(0, index);
    }

    public static final String append(String path, String segment) {
        path = isEmpty(path) ? "" : removeTrailingSlash(path);
        segment = isEmpty(segment) ? "" : removeLeadingSlash(segment);
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - "/".length());
        }
        if (segment.startsWith("/")) {
            segment = segment.substring("/".length());
        }
        return path + '/' + segment;
    }
    
    /**
     * Returns the substring that is the last entry from this path string without 
     * leading or trailing slashes. For example, <code>tail(<i>"/some/path/"</i>)</code>
     * will return <i>"path"</i>.
     * 
     * @param  path		a path string which last entry name is to be cut out
     * @return			the entry name itself 
     */
    public static String tail(String path) {
        path = removeTrailingSlash(path);
        int index = path.lastIndexOf('/');
        if (index >= 0) {
            return path.substring(index + 1);
        }
        return path;
    }

    public static String head(String path) {
        path = removeLeadingSlash(path);
        int index = path.indexOf('/');
        if (index >= 0) {
            return path.substring(0, index);
        }
        return path;
    }

    public static String removeLeadingSlash(String path) {
        if (path == null || path.length() == 0) {
            return "";
        }
        if (path.charAt(0) == '/') {
            path = path.substring(1);
        }
        return path;
    }
    
    /**
     * Removes the last trailing slash (if any) from the path string.
     * For example, <code>removeTralingSlash(<i>"some/path/"</i>)</code> will return
     * <i>"some/path"</i>.
     * 
     * @param  path		a path string 		
     * @return			the same path string without the last '/'
     */
    public static final String removeTrailingSlash(String path) {
        if (isEmpty(path)) {
            return path;
        }
        if (path.charAt(path.length() - 1) == '/') {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
    
    private static void encode(String urlPart, StringBuffer dst) {        
        for(StringTokenizer tokens = new StringTokenizer(urlPart, " /+()", true); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if (" ".equals(token)) {
                dst.append("%20");
            } else if ("+".equals(token) || "/".equals(token) || "(".equals(token) || ")".equals(token)) {
                dst.append(token);
            } else {
                try {
                    dst.append(URLEncoder.encode(token, "UTF-8"));
                } catch (IOException e) {
                    dst.append(token);
                }
            }
        }
    }
    
    public static String encode(String source) {
        StringBuffer sb = new StringBuffer();
        encode(source, sb);
        return sb.toString();
    }

    public static String decode(String source) {
        if (source == null) {
            return source;
        }
        StringBuffer dst = new StringBuffer();
        for(StringTokenizer tokens = new StringTokenizer(source, "+/:", true); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if ("+".equals(token)) {
                dst.append("+");
            } else if ("/".equals(token)) {
                dst.append("/");
            } else if (":".equals(token)) {
                dst.append(":");
            } else {
                try {
                    dst.append(URLDecoder.decode(token, "UTF-8"));
                } catch (IOException e) {
                    dst.append(token);
                }
            }
        }
        return dst.toString();
    }
    
    public static String getFSCommonRoot(String[] paths) {
        if (paths == null || paths.length == 0) {
            return null;            
        }
        String root = paths[0].replace(File.separatorChar, '/');
        for (int i = 1; i < paths.length; i++) {
			root = getCommonAncestor(root, paths[i].replace(File.separatorChar, '/'));
		}
        File fileRoot = new File(root);
        if (fileRoot.isFile()) {
        	root = PathUtil.removeTail(root);
        }
        return root;
    }
    
    public static String getCommonRoot(String[] paths) {
        if (paths.length == 1) {
            return PathUtil.removeLeadingSlash(PathUtil.removeTail(paths[0]));
        } 
        String root = paths[0].replace(File.separatorChar, '/');
        for (int i = 1; i < paths.length; i++) {
			root = getCommonAncestor(root, paths[i].replace(File.separatorChar, '/'));
		}
        root = PathUtil.removeLeadingSlash(root);
        root = PathUtil.removeTrailingSlash(root);
        for(int i = 0; i < paths.length; i++) {
        	if (paths[i].replace(File.separatorChar, '/').equals(root)) {
        		root = PathUtil.removeTail(root);
        		break;
        	}
        }
        return root;
    }

    private static String getCommonAncestor(String path1, String path2) {
    	// simplest case
    	String longerPath = path1.length() > path2.length() ? path1 : path2;
    	String root = path1.length() > path2.length() ? path2 : path1;
    	if ("".equals(root)) {
    		root = "/";
    	}
    	while(!PathUtil.isEmpty(root)) {
        	if (!root.endsWith("/")) {
        		root += "/";
        	}
    		boolean rootFound = FSUtil.isWindows ?
    				longerPath.toLowerCase().startsWith(root.toLowerCase()) :
    				longerPath.startsWith(root);
    		if (rootFound) {
    			return PathUtil.removeTrailingSlash(root);
    		}
    		root = PathUtil.removeTail(root);
    	}    	
    	return PathUtil.removeTrailingSlash(root);
    }

    public static boolean isURL(String pathOrUrl) {
        return pathOrUrl != null && 
            (pathOrUrl.startsWith("http://") || 
                    pathOrUrl.startsWith("https://") || 
                    pathOrUrl.startsWith("svn://") ||
                    pathOrUrl.startsWith("svn+ssh://"));
    }
}

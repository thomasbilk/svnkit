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

package org.tmatesoft.svn.cli;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tmatesoft.svn.core.io.SVNException;
import org.tmatesoft.svn.util.DebugLog;

/**
 * @author TMate Software Ltd.
 */
public class SVNCommandLine {
    
    private Set myUnaryArguments;
    private Map myBinaryArguments;
    private String myCommandName;
    private List myPaths;
    
    public SVNCommandLine(String[] commandLine) throws SVNException {
        init(commandLine);
    }
    
    public boolean hasArgument(SVNArgument argument) {
        return myBinaryArguments.containsKey(argument) || myUnaryArguments.contains(argument);
    }
    
    public Object getArgumentValue(SVNArgument argument) {
        return myBinaryArguments.get(argument);
    }
    
    public String getCommandName() {
        return myCommandName;
    }
    
    public int getPathCount() {
        return myPaths.size();
    }
    
    public String getPathAt(int index) {
        return (String) myPaths.get(index);
    }
    
    protected void init(String[] arguments) throws SVNException {
        
        myUnaryArguments = new HashSet();
        myBinaryArguments = new HashMap();
        myPaths = new ArrayList();
        
        SVNArgument previousArgument = null;
        String previousArgumentName = null;
        
        for(int i = 0; i < arguments.length; i++) {
            String argument = arguments[i];
            if (previousArgument != null) {
                // parse as value.
                if (argument.startsWith("--") || argument.startsWith("-")) {
                    throw new SVNException("argument '" + previousArgumentName + "' requires value");
                }
                Object value = previousArgument.parseValue(argument);
                DebugLog.log("value (2): " + value);
                myBinaryArguments.put(previousArgument, value);
                
                previousArgument = null;
                previousArgumentName = null;
                continue;
            }
            
            if (argument.startsWith("--")) {
                // long argument (--no-ignore)
                SVNArgument svnArgument = SVNArgument.findArgument(argument);
                if (svnArgument != null) {                    
                    if (svnArgument.hasValue()) {
                        previousArgument = svnArgument;
                        previousArgumentName = argument;
                    } else {
                        myUnaryArguments.add(svnArgument);
                    }
                } else {
                    throw new SVNException("invalid argument '" + argument + "'");
                }
            } else if (argument.startsWith("-")) {
                for(int j = 1; j < argument.length(); j++) {
                    String name = "-" + argument.charAt(j);
                    DebugLog.log("parsing argument: " + name);
                    
                    SVNArgument svnArgument = SVNArgument.findArgument(name);
                    if (svnArgument != null) {
                        if (svnArgument.hasValue()) {
                            if (j + 1 < argument.length()) {
                                String value = argument.substring(j + 1);
                                Object argValue = svnArgument.parseValue(value);
                                DebugLog.log("value: " + value);
                                myBinaryArguments.put(svnArgument, argValue);
                            } else {
                                previousArgument = svnArgument;
                                previousArgumentName = name;
                            }
                            j = argument.length();
                        } else {
                            myUnaryArguments.add(svnArgument);
                        }
                    } else {
                        throw new SVNException("invalid argument '" + name + "'");
                    }
                }
            } else {
                if (myCommandName == null) {
                    myCommandName = argument;
                } else {
                    myPaths.add(argument);
                }
            }
        }
        if (myCommandName == null) {
            throw new SVNException("no command name defined");
        }        
        if (myPaths.isEmpty()) {
            myPaths.add(".");
        }
    }
}
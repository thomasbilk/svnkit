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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.internal.io.dav.IDAVSSLManager;

/**
 * @author TMate Software Ltd.
 *
 */
public class SocketFactory {
	
	public static Socket createPlainSocket(String host, int port) throws IOException {
		return new Socket(createAddres(host), port);
	}

	public static Socket createSSLSocket(IDAVSSLManager manager, String host, int port) throws IOException {
		return manager.getSSLContext(host, port).getSocketFactory().createSocket(createAddres(host), port);
	}
	
	private static InetAddress createAddres(String hostName) throws UnknownHostException {
		byte[] bytes = new byte[4];
		int index = 0;
		for(StringTokenizer tokens = new StringTokenizer(hostName, "."); tokens.hasMoreTokens();) {
			String token = tokens.nextToken();
			try {
				byte b = (byte) Integer.parseInt(token);
				if (index < bytes.length) {
					bytes[index] = b;
					index++;
				} else {
					bytes = null;
					break;
				}
			} catch(NumberFormatException e) {
				bytes = null;
				break;
			}
		}
		if (bytes != null && index == 4) {
			return InetAddress.getByAddress(hostName, bytes);
		}		
		return InetAddress.getByName(hostName);
	}
}

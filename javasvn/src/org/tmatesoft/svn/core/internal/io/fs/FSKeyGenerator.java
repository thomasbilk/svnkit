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
package org.tmatesoft.svn.core.internal.io.fs;

import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;

/**
 * @version 1.0
 * @author  TMate Software Ltd.
 */
public class FSKeyGenerator {
    
    public static String generateNextKey(char[] oldKey) throws SVNException {
        char[] nextKey = new char[oldKey.length + 1];
        /* do we have a carry or not?
         * We start with a carry, because we're
         * incrementing the number, after all. 
         */
        boolean carry = true;
        /* Leading zeros are not allowed, except for the string "0". */
        if(oldKey.length > 1 && oldKey[0] == '0'){
            return null;
        }
        for(int i = oldKey.length - 1; i >= 0; i--){
            char c = oldKey[i];
            /* Validate as we go. */
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'z'))) {
                return null;
            }
            if(carry){
                if(c == 'z') {
                    nextKey[i] = '0';
                }else {
                    carry = false;
                    if(c == '9'){
                        nextKey[i] = 'a';
                    }else{
                        nextKey[i] = (char)(c + 1);
                    }
                }
            }else{
                nextKey[i] = c;
            }
        }
        /* The new length is old length + 1 if there's a carry out of the
         * leftmost digit. 
         */
        int nextKeyLength = oldKey.length + (carry ? 1 : 0);
        /* Ensure that we haven't overrun the (ludicrous) bound on key length.
         * Note that MAX_KEY_SIZE is a bound on the size including
         * the trailing null byte. 
         */
        if(nextKeyLength >= FSConstants.MAX_KEY_SIZE){
            SVNErrorManager.error("FATAL error: new key length is greater than the threshold " + FSConstants.MAX_KEY_SIZE);
        }
        /* Handle any leftover carry. */
        if(carry){
            System.arraycopy(nextKey, 0, nextKey, 1, oldKey.length);
            nextKey[0] = '1';
        }
        return new String(nextKey, 0, nextKeyLength);
    }
    
    public static String addKeys(String key1, String key2){
        int i1 = key1.length() - 1;
        int i2 = key2.length() - 1;
        int i3 = 0;
        int carry = 0;
        int val;
        char[] buf = new char[FSConstants.MAX_KEY_SIZE + 2];
        while(i1 >= 0 || i2 >= 0 || carry > 0){
            val = carry;
            if(i1 >= 0){
                val += key1.charAt(i1) <= '9' ? key1.charAt(i1) - '0' : key1.charAt(i1) - 'a' + 10;
            }
            if(i2 >= 0){
                val += key2.charAt(i2) <= '9' ? key2.charAt(i2) - '0' : key2.charAt(i2) - 'a' + 10;
            }
            carry = val / 36;
            val = val % 36;
            buf[i3++] = val <= 9 ? (char)('0' + val) : (char)(val - 10 + 'a');
            if(i1 >= 0){
                --i1;
            }
            if(i2 >= 0){
                --i2;
            }
        }
        /* Now reverse the resulting string. */
        StringBuffer result = new StringBuffer();
        for(int i = 0; i < i3; i++){
            result.append(buf[i3 - i - 1]);
        }
        return result.toString();
    }
}

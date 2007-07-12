/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

import org.tmatesoft.svn.util.SVNDebugLog;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNMergeRangeList {
    private SVNMergeRange[] myRanges;
    
    public SVNMergeRangeList(SVNMergeRange[] ranges) {
        myRanges = ranges;
    }
    
    public SVNMergeRange[] getRanges() {
        return myRanges;
    }
    
    public int getSize() {
        return myRanges.length;
    }
    
    public SVNMergeRangeList dup() {
        SVNMergeRange[] ranges = new SVNMergeRange[myRanges.length];
        for (int i = 0; i < myRanges.length; i++) {
            SVNMergeRange range = myRanges[i];
            ranges[i] = new SVNMergeRange(range.getStartRevision(), range.getEndRevision());
        }
        return new SVNMergeRangeList(ranges);
    }
    
    public SVNMergeRangeList merge(SVNMergeRangeList rangeList) {
        int i = 0;
        int j = 0;
        SVNMergeRange lastRange = null;
        Collection resultRanges = new LinkedList();
        while (i < myRanges.length && j < rangeList.myRanges.length) {
            SVNMergeRange range1 = myRanges[i];
            SVNMergeRange range2 = rangeList.myRanges[j];
            SVNMergeRange combinedRange = null;
            
            int res = range1.compareTo(range2);
            if (res == 0) {
                combinedRange = lastRange == null ? range1 : lastRange.combine(range1, true); 
                i++;
                j++;
            } else if (res < 0) {
                combinedRange = lastRange == null ? range1 : lastRange.combine(range1, true); 
                i++;
            } else { 
                combinedRange = lastRange == null ? range2 : lastRange.combine(range2, true); 
                j++;
            }
            if (combinedRange != lastRange) {
                lastRange = combinedRange;
                resultRanges.add(lastRange);
            }
        }
        SVNDebugLog.assertCondition(i >= myRanges.length || j >= rangeList.myRanges.length, "assertion failure in SVNMergeRangeList.merge()");
        for (; i < myRanges.length; i++) {
            SVNMergeRange range = myRanges[i];
            SVNMergeRange combinedRange = lastRange == null ? range : lastRange.combine(range, true); 
            if (combinedRange != lastRange) {
                lastRange = combinedRange;
                resultRanges.add(lastRange);
            }
        }
        for (; j < rangeList.myRanges.length; j++) {
            SVNMergeRange range = rangeList.myRanges[j];
            SVNMergeRange combinedRange = lastRange == null ? range : lastRange.combine(range, true); 
            if (combinedRange != lastRange) {
                lastRange = combinedRange;
                resultRanges.add(lastRange);
            }
        }
        return new SVNMergeRangeList((SVNMergeRange[]) resultRanges.toArray(new SVNMergeRange[resultRanges.size()]));
    }
    
    public SVNMergeRangeList combineRanges() {
        Collection combinedRanges = new LinkedList();
        SVNMergeRange lastRange = null;
        for (int k = 0; k < myRanges.length; k++) {
            SVNMergeRange nextRange = myRanges[k];
            SVNMergeRange combinedRange = lastRange == null ? nextRange : lastRange.combine(nextRange, false); 
            if (combinedRange != lastRange) {
                lastRange = combinedRange;
                combinedRanges.add(lastRange);
            }
        }
        SVNMergeRange[] ranges = (SVNMergeRange[]) combinedRanges.toArray(new SVNMergeRange[combinedRanges.size()]);
        Arrays.sort(ranges);
        return new SVNMergeRangeList(ranges);
    }
    
    public String toString() {
        String output = "";
        for (int i = 0; i < myRanges.length; i++) {
            SVNMergeRange range = myRanges[i];
            long startRev = range.getStartRevision();
            long endRev = range.getEndRevision();
            if (startRev == endRev) {
                output += String.valueOf(startRev);
            } else {
                output += startRev + '-' + endRev;
            }
            if (i < myRanges.length - 1) {
                output += ',';
            }
        }
        return output;
    }

    /**
     * Returns ranges which present in this range list but not in the 
     * argument range list. 
     */
    public SVNMergeRangeList diff(SVNMergeRangeList rangeList) {
        return removeOrIntersect(rangeList, true);
    }
    
    private SVNMergeRangeList removeOrIntersect(SVNMergeRangeList rangeList, boolean remove) {
        Collection ranges = new LinkedList();
        SVNMergeRange lastRange = null;
        SVNMergeRange range1 = null;
        int i = 0;
        int j = 0;
        int lastInd = -1;
        while (i < myRanges.length && j < rangeList.myRanges.length) {
            SVNMergeRange range2 = rangeList.myRanges[j];
            if (i != lastInd) {
                range1 = myRanges[i];
                lastInd = i;
            }
            
            if (range2.contains(range1)) {
                if (!remove) {
                    SVNMergeRange combinedRange = lastRange == null ? range1 : lastRange.combine(range1, true);
                    if (combinedRange != lastRange) {
                        lastRange = combinedRange;
                        ranges.add(lastRange);
                    }
                }
                
                i++;
                
                if (range2.equals(range1)) {
                    j++;
                }
            } else if (range2.intersects(range1)) {
                if (range1.getStartRevision() < range2.getStartRevision()) {
                    SVNMergeRange tmpRange = remove ? new SVNMergeRange(range1.getStartRevision(), range2.getStartRevision() - 1)
                                                    : new SVNMergeRange(range2.getStartRevision(), range1.getEndRevision());

                    SVNMergeRange combinedRange = lastRange == null ? tmpRange : lastRange.combine(tmpRange, true);
                    if (combinedRange != lastRange) {
                        lastRange = combinedRange;
                        ranges.add(lastRange);
                    }
                }
                
                if (range1.getEndRevision() > range2.getEndRevision()) {
                    if (!remove) {
                        SVNMergeRange tmpRange = new SVNMergeRange(range1.getStartRevision(), range2.getEndRevision());
                        SVNMergeRange combinedRange = lastRange == null ? tmpRange : lastRange.combine(tmpRange, true);
                        if (combinedRange != lastRange) {
                            lastRange = combinedRange;
                            ranges.add(lastRange);
                        }
                    }
                    range1.setStartRevision(range2.getEndRevision() + 1);
                } else {
                    i++;
                }
            } else {
                if (range2.compareTo(range1) < 0) {
                    j++;
                } else {
                    if (lastRange == null || !lastRange.canCombine(range1)) {
                        if (remove) {
                            lastRange = new SVNMergeRange(range1.getStartRevision(), range1.getEndRevision());
                            ranges.add(lastRange);
                        }
                    } else if (lastRange != null && lastRange.canCombine(range1)) {
                        lastRange = lastRange.combine(range1, false);
                    }
                    i++;
                }
            }
        }
        
        if (remove) {
            if (i == lastInd && i < myRanges.length) {
                SVNMergeRange combinedRange = lastRange == null ? range1 : lastRange.combine(range1, true);
                if (combinedRange != lastRange) {
                    lastRange = combinedRange;
                    ranges.add(lastRange);
                }
                i++;
            }
            for (; i < myRanges.length; i++) {
                SVNMergeRange range = myRanges[i];
                SVNMergeRange combinedRange = lastRange == null ? range : lastRange.combine(range, true);
                if (combinedRange != lastRange) {
                    lastRange = combinedRange;
                    ranges.add(lastRange);
                }
            }
        }
        return new SVNMergeRangeList((SVNMergeRange[]) ranges.toArray(new SVNMergeRange[ranges.size()]));
    }
}

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

package org.tmatesoft.svn.core.diff;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.diff.delta.SVNSequenceLine;

/**
 * @author Ian Sullivan 
 * @author TMate Software Ltd.
 */
public class SVNNormalDiffGenerator extends SVNSequenceDiffGenerator implements ISVNDiffGeneratorFactory {

    public static final String TYPE = "normal";
    private Map myGeneratorsCache;

    private SVNNormalDiffGenerator(Map properties) {
        super(properties);
    }
    
    private SVNNormalDiffGenerator() {
        super(null);
    }

    public void generateDiffHeader(String item, String leftInfo, String rightInfo, Writer output) throws IOException {
        output.write("*** ");
        output.write(item);
        output.write(getEOL());
    }
    
    protected void processBlock(int sourceStartLine, int sourceEndLine, SVNSequenceLine[] sourceLines, int targetStartLine, int targetEndLine,
            SVNSequenceLine[] targetLines, String encoding, Writer output) throws IOException {
        if(sourceStartLine > sourceEndLine){
            add(sourceStartLine, targetStartLine, targetEndLine, targetLines, encoding, output);
        }else if(targetStartLine > targetEndLine){
            delete(targetStartLine, sourceStartLine, sourceEndLine, sourceLines, encoding, output);
        }else{
            change(targetStartLine, targetEndLine, targetLines, sourceStartLine, sourceEndLine, sourceLines, encoding, output);
        }
    }
    
    protected String displayWhiteSpace(String s){
        if(getProperties().containsKey("showWhiteSpace")){
            s = s.replaceAll("\t", "<tb>");
            s = s.replaceAll(" ", ".");
        }
        return s;
    }
    
    /*
     *Normal diff output is a series of one or more blocks in the following
     *format
        change-command
        < target-file-line
        < target-file-line...
        ---
        > source-file-line
        > source-file-line...
     *There are three types of change commands. Each consists of a line
     *number or comma-separated range of lines in the target file, a single
     *character indicating the kind of change to make, and a line number or
     *comma-separated range of lines in the source file. All line numbers
     *are the original line numbers in each file.
     */

    /**
     * Handles a delete of lines from the target.
     * @param deleteAt the line where the lines would have appeared in the
     * source (0 based)
     * @param deleteStart first line deleted from target (0 based).
     * @param deleteEnd last line deleted from target (0 based).
     * @param deleteLines all the lines from the target file. Could be accessed
     * with <CODE>deleteStart</CODE> and <CODE>deleteEnd</CODE> to identify the
     * deleted lines.
     */
    protected void delete(int deleteAt, int deleteStart, int deleteEnd, SVNSequenceLine[] deleteLines, String encoding, Writer output) throws IOException {
        /*Change command is in the format `rdl'
         *Delete the lines in range r from the target file; line l is where
         *they would have appeared in the source file had they not been
         *deleted. For example, `5,7d3' means delete lines 5--7 of target file;
         *or, if changing source file into target file, append lines 5--7 of target file
         *after line 3 of source file. 
         */
        //deleteStart and deleteEnd are 0 based, display a 1 based value.
        int displayStart = deleteStart + 1;
        int displayEnd = deleteEnd + 1;
        int displayAt = deleteAt + 1; 
        println(displayStart + ((displayEnd != displayStart) ? ("," + displayEnd) : "") + "d" + displayAt, output);
        int delLine = deleteStart;
        while(delLine <= deleteEnd){
            println("<" + displayWhiteSpace(new String(deleteLines[delLine++].getBytes(), encoding)), output);
        }
    }

    /**
     * Handles the addition of lines to source.
     * @param addAt the line where the new lines would be added to target (0 based)
     * @param addStart the first line added from source (0 based)
     * @param addEnd the last line added from source (0 based)
     * @param addLines all the lines from the source file. Could be accessed
     * with <CODE>addStart</CODE> and <CODE>addEnd</CODE> to identify the added lines.
     */
    protected void add(int addAt, int addStart, int addEnd, SVNSequenceLine[] addLines, String encoding, Writer output) throws IOException{
        /*Change command is in the format `lar'
         *Add the lines in range r of the source file after line l of the
         *target file. For example, `8a12,15' means append lines 12--15 of
         *source file after line 8 of target file; or, if changing source file into target file,
         *delete lines 12--15 of source file.
         */
        int displayStart = addStart + 1;
        int displayEnd = addEnd + 1;
        int displayAt = addAt + 1;
        println(displayAt + "a" + displayStart + ((displayEnd != displayStart) ? ("," + displayEnd) : ""), output);
        int addLine = addStart;
        while(addLine <= addEnd){
            println(">" + displayWhiteSpace(new String(addLines[addLine++].getBytes(), encoding)), output);
        }
    }

    /**
     * Handles a change of a range of lines in target to a range of lines in source.
     * @param replaceStart the first line in target that will be replaced (0 based)
     * @param replaceEnd the last line in target that will be replaced (0 based)
     * @param replaceLines all the lines in target
     * @param replaceWithStart the first line in source to that will replace the
     * lines in target (0 based)
     * @param replaceWithEnd  the last line in source to that will replace the lines
     * in target (0 based)
     * @param replaceWithLines all the lines in source
     */
    protected void change(int replaceStart, int replaceEnd, SVNSequenceLine[] replaceLines,
            int replaceWithStart, int replaceWithEnd, SVNSequenceLine[] replaceWithLines, String encoding, Writer output) throws IOException{
        /*Change command is in the format `fct'
         *Replace the lines in range f of the target file with lines in range
         *t of the source file. This is like a combined add and delete, but
         *more compact. For example, `5,7c8,10' means change lines 5--7 of
         *target file to read as lines 8--10 of source file; or, if changing source file 
         *into target file, change lines 8--10 of source file to read as lines 5--7 of
         *target file.
         */
        int displayStart = replaceStart + 1;
        int displayEnd = replaceEnd + 1;
        int displayWithStart = replaceWithStart + 1;
        int displayWithEnd = replaceWithEnd + 1;
        println(
                displayStart + ((displayEnd != displayStart) ? ("," + displayEnd ) : "") +
                "c" + 
                displayWithStart + ((displayWithEnd != displayWithStart) ? ("," + displayWithEnd) : ""), output);
        int replaceLine = replaceStart;
        while(replaceLine <= replaceEnd){
            println("<" + displayWhiteSpace(new String(replaceLines[replaceLine++].getBytes(), encoding)), output);
        }
        println("---", output);

        int replaceWithLine = replaceWithStart;
        while(replaceWithLine <= replaceWithEnd){
            println(">" + displayWhiteSpace(new String(replaceWithLines[replaceWithLine++].getBytes(), encoding)), output);
        }
    }

    public ISVNDiffGenerator createGenerator(Map properties) {
        if (myGeneratorsCache == null) {
            myGeneratorsCache = new HashMap();
        }        
            
        ISVNDiffGenerator generator = (ISVNDiffGenerator) myGeneratorsCache.get(properties);
        if (generator != null) {
            return generator;
        }        
        generator = new SVNNormalDiffGenerator(properties);
        myGeneratorsCache.put(properties, generator);
        return generator;
    }
    
    public static void setup() {
        SVNDiffManager.registerDiffGeneratorFactory(new SVNNormalDiffGenerator(), SVNNormalDiffGenerator.TYPE);
    }
}

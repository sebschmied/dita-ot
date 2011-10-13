/*
 * This file is part of the DITA Open Toolkit project hosted on
 * Sourceforge.net. See the accompanying license.txt file for
 * applicable licenses.
 */

/*
 * (c) Copyright IBM Corp. 2007 All Rights Reserved.
 */
package org.dita.dost.reader;

import static org.dita.dost.util.Constants.*;
import static org.dita.dost.writer.DitaWriter.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.dita.dost.log.DITAOTLogger;
import org.dita.dost.log.MessageUtils;
import org.dita.dost.module.Content;
import org.dita.dost.module.ContentImpl;
import org.dita.dost.util.FileUtils;
import org.dita.dost.util.StringUtils;
import org.dita.dost.writer.ChunkTopicParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ProcessingInstruction;
import org.w3c.dom.Text;

/**
 * ChunkMapReader class, read ditamap file for chunking.
 *
 */
public final class ChunkMapReader implements AbstractReader {

    public static final String CHUNK_BY_DOCUMENT = "by-document";
    public static final String CHUNK_BY_TOPIC = "by-topic";
    public static final String CHUNK_TO_CONTENT = "to-content";
    public static final String CHUNK_TO_NAVIGATION = "to-navigation";

    private DITAOTLogger logger;

    private boolean chunkByTopic = false;

    private String filePath = null;
    //edited by william on 2009-09-10 for maintain iteration order start
    private LinkedHashMap<String, String> changeTable = null;
    //edited by william on 2009-09-10 for maintain iteration order end

    private Hashtable<String, String> conflictTable = null;


    private Set<String> refFileSet = null;

    private String ditaext = null;

    private String transtype = null;

    private ProcessingInstruction workdir = null; // Tagsmiths modification

    private ProcessingInstruction path2proj = null; // Tagsmiths modification

    private String processingRole = ATTR_PROCESSING_ROLE_VALUE_NORMAL;
    /**
     * Constructor.
     */
    public ChunkMapReader() {
        super();
        chunkByTopic=false;// By default, processor should chunk by document.
        changeTable = new LinkedHashMap<String, String>(INT_128);
        refFileSet = new HashSet<String>(INT_128);
        conflictTable = new Hashtable<String, String>(INT_128);
    }
    /**
     * read input file.
     * @param filename filename
     */
    public void read(final String filename) {
        final File inputFile = new File(filename);
        filePath = inputFile.getParent();
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            final DocumentBuilder builder = factory.newDocumentBuilder();
            final Document doc = builder.parse(filename);

            // Start Tagsmiths modification: Added logic to collect the
            // workdir and path2proj processing instructions.
            final NodeList docNodes = doc.getChildNodes();
            for (int i = 0; i < docNodes.getLength(); i++) {
                final Node node = docNodes.item(i);
                if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
                    final ProcessingInstruction pi = (ProcessingInstruction) node;
                    if (pi.getNodeName() == PI_WORKDIR_TARGET) {
                        workdir = pi;
                    } else if (pi.getNodeName().equals(PI_PATH2PROJ_TARGET)) {
                        path2proj = pi;
                    }
                }
            }
            // End Tagsmiths modification

            //get the document node
            final Element root = doc.getDocumentElement();
            //get the immediate child nodes
            final NodeList list = root.getChildNodes();
            final String rootChunkValue = root.getAttribute(ATTRIBUTE_NAME_CHUNK);
            if(rootChunkValue != null &&
                    rootChunkValue.contains(CHUNK_BY_TOPIC)){
                chunkByTopic = true;
            }else{
                chunkByTopic = false;
            }
            //chunk value = "to-content"
            //When @chunk="to-content" is specified on "map" element,
            //chunk module will change its @class attribute to "topicref"
            //and process it as if it were a normal topicref wich @chunk="to-content"
            if(root.getAttribute(ATTRIBUTE_NAME_CHUNK) != null &&
                    root.getAttribute(ATTRIBUTE_NAME_CHUNK).indexOf(CHUNK_TO_CONTENT)!=-1){
                // if to-content is specified on map element

                // create the reference to the new file on root element.
                final Random random = new Random();
                String newFilename = inputFile.getName().substring(
                        0, inputFile.getName().indexOf(FILE_EXTENSION_DITAMAP)) + ditaext;
                File newFile = new File(inputFile.getParentFile().getAbsolutePath(),newFilename);
                if (newFile.exists()) {
                    newFilename = "Chunk"
                            + random.nextInt(Integer.MAX_VALUE) + ditaext;
                    final String oldpath = newFile.getAbsolutePath();
                    newFile = new File(FileUtils.resolveFile(inputFile.getParentFile().getAbsolutePath(), newFilename));
                    // Mark up the possible name changing, in case that references might be updated.
                    conflictTable.put(newFile.getAbsolutePath(), FileUtils.removeRedundantNames(oldpath));
                }
                //change the class attribute to "topicref"
                final String originClassValue = root.getAttribute(ATTRIBUTE_NAME_CLASS);
                root.setAttribute(ATTRIBUTE_NAME_CLASS, originClassValue + MAP_TOPICREF.matcher);
                root.setAttribute(ATTRIBUTE_NAME_HREF, newFilename);

                //create the new file
                OutputStreamWriter newFileWriter = null;
                try{
                    newFileWriter = new OutputStreamWriter(new FileOutputStream(newFile), UTF8);
                    newFileWriter.write(XML_HEAD);
                    newFileWriter.write("<?workdir /"+newFile.getParentFile().getAbsolutePath()+"?>");
                    newFileWriter.write("<dita></dita>");
                    newFileWriter.flush();
                    newFileWriter.close();
                }catch (final Exception e) {
                    logger.logException(e);
                }finally{
                    try{
                        if(newFileWriter!=null){
                            newFileWriter.close();
                        }
                    }catch (final Exception e) {
                        logger.logException(e);
                    }
                }

                //process chunk
                processTopicref(root);

                //add newly created file to changeTable
                changeTable.put(newFile.getAbsolutePath(),newFile.getAbsolutePath());

                // restore original root element
                if(originClassValue != null){
                    root.setAttribute(ATTRIBUTE_NAME_CLASS, originClassValue);
                }
                //remove the href
                root.removeAttribute(ATTRIBUTE_NAME_HREF);

            }else{
                // if to-content is not specified on map element
                //process the map element's immediate child node(s)
                for (int i = 0; i < list.getLength(); i++){
                    final Node node = list.item(i);
                    Node classAttr = null;
                    String classValue = null;
                    if (node.getNodeType() == Node.ELEMENT_NODE){
                        classAttr = node.getAttributes().getNamedItem(ATTRIBUTE_NAME_CLASS);

                        if(classAttr != null){
                            classValue = classAttr.getNodeValue();
                        }

                        if(classValue != null && MAP_RELTABLE.matches(classValue)){
                            updateReltable((Element)node);
                        }
                        if(classValue != null && MAP_TOPICREF.matches(classValue)
                                && !MAPGROUP_D_TOPICGROUP.matches(classValue)){
                            processTopicref(node);
                        }

                    }
                }
            }

            //write the edited ditamap file to a temp file
            outputMapFile(inputFile.getAbsolutePath()+".chunk",root);
            if(!inputFile.delete()){
                final Properties prop = new Properties();
                prop.put("%1", inputFile.getPath());
                prop.put("%2", inputFile.getAbsolutePath()+".chunk");
                logger.logError(MessageUtils.getMessage("DOTJ009E", prop).toString());
            }
            if(!new File(inputFile.getAbsolutePath()+".chunk").renameTo(inputFile)){
                final Properties prop = new Properties();
                prop.put("%1", inputFile.getPath());
                prop.put("%2", inputFile.getAbsolutePath()+".chunk");
                logger.logError(MessageUtils.getMessage("DOTJ009E", prop).toString());
            }

        }catch (final Exception e){
            logger.logException(e);
        }

    }

    public void setLogger(final DITAOTLogger logger) {
        this.logger = logger;
    }

    private void outputMapFile(final String file, final Element root) {

        OutputStreamWriter output = null;
        try{
            output = new OutputStreamWriter(
                    new FileOutputStream(file),
                    UTF8);
            // Start Tagsmiths modification: XML_HEAD and the workdir and
            // path2proj processing instructions were not being sent to output.
            // The follow few lines corrects that problem.
            output.write(XML_HEAD);
            if (workdir != null) {
                output(workdir, output);
            }
            if (path2proj != null)
            {
                output(path2proj, output);
                // End Tagsmiths modification
            }

            output(root,output);
            output.flush();
            output.close();
        }catch (final Exception e) {
            logger.logException(e);
        }finally{
            try{
                if(output!=null){
                    output.close();
                }
            }catch (final Exception e) {
                logger.logException(e);
            }
        }
    }

    private void output(final ProcessingInstruction instruction,final Writer outputWriter) throws IOException{
        outputWriter.write("<?"+instruction.getTarget()+" "+instruction.getData()+"?>");
    }


    private void output(final Text text, final Writer outputWriter) throws IOException{
        outputWriter.write(StringUtils.escapeXML(text.getData()));
    }


    private void output(final Element elem, final Writer outputWriter) throws IOException{
        outputWriter.write("<"+elem.getNodeName());
        final NamedNodeMap attrMap = elem.getAttributes();
        for (int i = 0; i<attrMap.getLength(); i++){
            outputWriter.write(" "+attrMap.item(i).getNodeName()
                    +"=\""+StringUtils.escapeXML(attrMap.item(i).getNodeValue())
                    +"\"");
        }
        outputWriter.write(">");
        final NodeList children = elem.getChildNodes();
        Node child;
        for (int j = 0; j<children.getLength(); j++){
            child = children.item(j);
            switch (child.getNodeType()){
            case Node.TEXT_NODE:
                output((Text) child, outputWriter); break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                output((ProcessingInstruction) child, outputWriter); break;
            case Node.ELEMENT_NODE:
                output((Element) child, outputWriter);
            }
        }

        outputWriter.write("</"+elem.getNodeName()+">");
    }
    //process chunk
    private void processTopicref(final Node node) {
        NamedNodeMap attr = null;
        Node hrefAttr = null;
        Node chunkAttr = null;
        Node copytoAttr = null;
        Node scopeAttr = null;
        Node classAttr = null;
        Node xtrfAttr = null;
        Node processAttr = null;
        String hrefValue = null;
        String chunkValue = null;
        String copytoValue = null;
        String scopeValue = null;
        String classValue = null;
        String xtrfValue = null;
        String processValue = null;
        final String tempRole = processingRole;
        boolean prevChunkByTopic = false;

        attr = node.getAttributes();

        hrefAttr = attr.getNamedItem(ATTRIBUTE_NAME_HREF);
        chunkAttr = attr.getNamedItem(ATTRIBUTE_NAME_CHUNK);
        copytoAttr = attr.getNamedItem(ATTRIBUTE_NAME_COPY_TO);
        scopeAttr = attr.getNamedItem(ATTRIBUTE_NAME_SCOPE);
        classAttr = attr.getNamedItem(ATTRIBUTE_NAME_CLASS);
        xtrfAttr = attr.getNamedItem(ATTRIBUTE_NAME_XTRF);
        processAttr = attr.getNamedItem(ATTRIBUTE_NAME_PROCESSING_ROLE);

        if(hrefAttr != null){
            hrefValue = hrefAttr.getNodeValue();
        }
        if(chunkAttr != null){
            chunkValue = chunkAttr.getNodeValue();
        }
        if(copytoAttr != null){
            copytoValue = copytoAttr.getNodeValue();
        }
        if(scopeAttr != null) {
            scopeValue = scopeAttr.getNodeValue();
        }
        if(classAttr != null) {
            classValue = classAttr.getNodeValue();
        }
        if(xtrfAttr != null) {
            xtrfValue = xtrfAttr.getNodeValue();
        }
        if(processAttr != null) {
            processValue = processAttr.getNodeValue();
            processingRole = processValue;
        }
        //This file is chunked(by-topic)
        if (xtrfValue != null && xtrfValue.contains("generated_by_chunk")) {
            return;
        }

        //set chunkByTopic if there is "by-topic" or "by-document" in chunkValue
        if(chunkValue != null &&
                (chunkValue.contains(CHUNK_BY_TOPIC) ||
                        chunkValue.contains(CHUNK_BY_DOCUMENT))){
            //a temp value to store the flag
            prevChunkByTopic = chunkByTopic;
            //if there is "by-topic" then chunkByTopic should be set to true;
            chunkByTopic = chunkValue.contains(CHUNK_BY_TOPIC);
        }

        if("external".equalsIgnoreCase(scopeValue)
                || (hrefValue != null && !FileUtils.fileExists(FileUtils.resolveFile(filePath, hrefValue)))
                || (MAPGROUP_D_TOPICHEAD.matches(classValue) && chunkValue == null)||
                //added by William on 2009-09-17 for chunk bug #2860199 start
                ////support topicref without href attribute
                (MAP_TOPICREF.matches(classValue) && chunkValue == null && hrefValue == null)
                //added by William on 2009-09-17 for chunk bug #2860199 end
                ) {
            //|| (ATTR_PROCESSING_ROLE_VALUE_RESOURCE_ONLY.equalsIgnoreCase(processValue))) {
            //Skip external links or non-existing href files.
            //Skip topic head entries.
            //Skip @processing-role=resource-only entries.
            if(chunkValue != null &&
                    (chunkValue.contains(CHUNK_BY_TOPIC) ||
                            chunkValue.contains(CHUNK_BY_DOCUMENT))){
                chunkByTopic = prevChunkByTopic;
            }
            processChildTopicref(node);
            //chunk "to-content"
        } else if(chunkValue != null &&
                //edited on 20100818 for bug:3042978
                chunkValue.indexOf(CHUNK_TO_CONTENT) != -1 && (hrefAttr != null || copytoAttr != null || node.hasChildNodes())){
            //if this is the start point of the content chunk
            //TODO very important start point(to-content).
            processChunk((Element)node,false, chunkByTopic);
        }else if(chunkValue != null &&
                chunkValue.indexOf(CHUNK_TO_NAVIGATION)!=-1 &&
                INDEX_TYPE_ECLIPSEHELP.equals(transtype)){
            //if this is the start point of the navigation chunk
            if(chunkValue != null &&
                    (chunkValue.contains(CHUNK_BY_TOPIC) ||
                            chunkValue.contains(CHUNK_BY_DOCUMENT))){
                //restore the chunkByTopic value
                chunkByTopic = prevChunkByTopic;
            }
            processChildTopicref(node);
            //create new map file
            //create new map's root element
            final Node root = node.getOwnerDocument().getDocumentElement().cloneNode(false);
            //create navref element
            final Element navref = node.getOwnerDocument().createElement("navref");
            final Random random = new Random();
            final String newMapFile = "MAPCHUNK" + random.nextInt(Integer.MAX_VALUE) + ".ditamap";
            navref.setAttribute("mapref",newMapFile);
            //replace node with navref
            node.getParentNode().replaceChild(navref,node);
            root.appendChild(node);
            // generate new file
            final String navmap = FileUtils.resolveFile(filePath,newMapFile);
            changeTable.put(navmap, navmap);
            outputMapFile(navmap,(Element)root);
            //chunk "by-topic"
        }else if(chunkByTopic){
            //TODO very important start point(by-topic).
            processChunk((Element)node,true, chunkByTopic);
            if(chunkValue != null &&
                    (chunkValue.contains(CHUNK_BY_TOPIC) ||
                            chunkValue.contains(CHUNK_BY_DOCUMENT))){
                chunkByTopic = prevChunkByTopic;
            }
            processChildTopicref(node);
        }else{
            String currentPath = null;
            if(copytoValue != null){
                currentPath = FileUtils.resolveFile(filePath, copytoValue);
            }else if(hrefValue != null){
                currentPath = FileUtils.resolveFile(filePath, hrefValue);
            }
            if(currentPath != null){
                if(changeTable.containsKey(currentPath)){
                    changeTable.remove(currentPath);
                }
                if(!refFileSet.contains(currentPath)){
                    refFileSet.add(currentPath);
                }
            }

            // Here, we have a "by-document" chunk, simply
            // send it to the output.
            if ((chunkValue != null || !chunkByTopic) && currentPath != null
                    && !ATTR_PROCESSING_ROLE_VALUE_RESOURCE_ONLY.equals(processingRole)) {
                changeTable.put(currentPath, currentPath);
            }

            if (chunkValue != null &&
                    (chunkValue.contains(CHUNK_BY_TOPIC) ||
                            chunkValue.contains(CHUNK_BY_DOCUMENT))){
                chunkByTopic = prevChunkByTopic;
            }

            processChildTopicref(node);
        }

        //restore chunkByTopic if there is "by-topic" or "by-document" in chunkValue
        if(chunkValue != null &&
                (chunkValue.contains(CHUNK_BY_TOPIC) ||
                        chunkValue.contains(CHUNK_BY_DOCUMENT))){
            chunkByTopic = prevChunkByTopic;
        }

        processingRole = tempRole;

    }


    private void processChildTopicref(final Node node) {
        final NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++){
            final Node current = children.item(i);
            if(current.getNodeType()==Node.ELEMENT_NODE){
                final String classValue  = ((Element)current).getAttribute(ATTRIBUTE_NAME_CLASS);
                final String hrefValue = ((Element)current).getAttribute(ATTRIBUTE_NAME_HREF);
                final String xtrfValue = ((Element)current).getAttribute(ATTRIBUTE_NAME_XTRF);
                if(MAP_TOPICREF.matches(classValue)){
                    if((hrefValue.length() != 0 &&
                            !"generated_by_chunk".equals(xtrfValue) &&
                            ! FileUtils.resolveFile(filePath,hrefValue)
                            .equals(changeTable.get(FileUtils.resolveFile(filePath,hrefValue)))) ||
                            MAPGROUP_D_TOPICHEAD.matches(classValue)
                            ){

                        //make sure hrefValue make sense and target file
                        //is not generated file or the element is topichead
                        processTopicref(current);
                        //added by William on 2009-09-18 for chunk bug #2860199 start
                        //support topicref without href attribute
                    }else if(hrefValue.length() == 0){
                        processTopicref(current);
                    }
                    //added by William on 2009-09-18 for chunk bug #2860199 end
                }
            }
        }

    }

    private void processChunk(final Element elem, final boolean separate, final boolean chunkByTopic) {
        //set up ChunkTopicParser
        try{
            final ChunkTopicParser chunkParser = new ChunkTopicParser();
            chunkParser.setLogger(logger);
            chunkParser.setup(changeTable, conflictTable, refFileSet, elem, separate, chunkByTopic, ditaext);
            chunkParser.write(filePath);
        }catch (final Exception e) {
            logger.logException(e);
        }
    }

    private void updateReltable(final Element elem) {
        final String hrefValue = elem.getAttribute(ATTRIBUTE_NAME_HREF);
        String resulthrefValue = null;
        if (hrefValue.length() != 0){
            if(changeTable.containsKey(FileUtils.resolveFile(filePath,hrefValue))){
                if (hrefValue.indexOf(SHARP)!=-1){
                    resulthrefValue=FileUtils.getRelativePathFromMap(filePath+UNIX_SEPARATOR+"stub.ditamap"
                            ,FileUtils.resolveFile(filePath,hrefValue))
                            + hrefValue.substring(hrefValue.indexOf(SHARP)+1);
                }else{
                    resulthrefValue=FileUtils.getRelativePathFromMap(filePath+UNIX_SEPARATOR+"stub.ditamap"
                            ,FileUtils.resolveFile(filePath,hrefValue));
                }
                elem.setAttribute(ATTRIBUTE_NAME_HREF, resulthrefValue);
            }
        }
        final NodeList children = elem.getChildNodes();
        for(int i = 0; i < children.getLength(); i++){
            final Node current = children.item(i);
            if(current.getNodeType() == Node.ELEMENT_NODE){
                final String classValue = ((Element)current).getAttribute(ATTRIBUTE_NAME_CLASS);
                if (MAP_TOPICREF.matches(classValue)){

                }
            }
        }
    }
    
    /**
     * get content.
     * @return content value {@code LinkedHashMap<String, String>}
     */
    public Content getContent() {
        final Content content = new ContentImpl();
        content.setValue(changeTable);
        return content;
    }
    /**
     * get conflict table.
     * @return conflict table
     */
    public Hashtable<String, String> getConflicTable() {
        return this.conflictTable;
    }
    /**
     * Set up environment.
     * @param ditaext ditaext
     * @param transtype transtype
     */
    public void setup(final String ditaext, final String transtype) {
        this.ditaext = ditaext;
        this.transtype = transtype;

    }

}

/*
 * This file is part of the DITA Open Toolkit project hosted on
 * Sourceforge.net. See the accompanying license.txt file for
 * applicable licenses.
 */

/*
 * (c) Copyright IBM Corp. 2004, 2005 All Rights Reserved.
 */
package org.dita.dost.module;

import static org.dita.dost.util.Constants.*;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.dita.dost.exception.DITAOTException;
import org.dita.dost.log.DITAOTLogger;
import org.dita.dost.pipeline.AbstractPipelineInput;
import org.dita.dost.pipeline.AbstractPipelineOutput;
import org.dita.dost.reader.MapIndexReader;
import org.dita.dost.util.FileUtils;
import org.dita.dost.util.ListUtils;
import org.dita.dost.util.StringUtils;
import org.dita.dost.writer.DitaIndexWriter;

/**
 * MoveIndexModule implement the move index step in preprocess. It reads the index
 * information from ditamap file and move these information to different
 * corresponding dita topic file.
 * 
 * @author Zhang, Yuan Peng
 */
final class MoveIndexModule implements AbstractPipelineModule {

    private final ContentImpl content;
    private DITAOTLogger logger;

    /**
     * Default constructor of MoveIndexModule class.
     */
    public MoveIndexModule() {
        super();
        content = new ContentImpl();
    }

    public void setLogger(final DITAOTLogger logger) {
        this.logger = logger;
    }

    /**
     * Entry point of MoveIndexModule.
     * 
     * @param input Input parameters and resources.
     * @return null
     * @throws DITAOTException exception
     */
    public AbstractPipelineOutput execute(final AbstractPipelineInput input) throws DITAOTException {
        if (logger == null) {
            throw new IllegalStateException("Logger not set");
        }
        Set<Map.Entry<String, String>> mapSet;
        Iterator<Map.Entry<String, String>> i;
        String targetFileName;
        final MapIndexReader indexReader = new MapIndexReader();
        indexReader.setLogger(logger);
        final DitaIndexWriter indexInserter = new DitaIndexWriter();
        indexInserter.setLogger(logger);
        final String baseDir = input.getAttribute(ANT_INVOKER_PARAM_BASEDIR);
        String tempDir = input.getAttribute(ANT_INVOKER_PARAM_TEMPDIR);

        if (!new File(tempDir).isAbsolute()) {
            tempDir = new File(baseDir, tempDir).getAbsolutePath();
        }

        indexReader.setMatch(new StringBuffer(MAP_TOPICREF.localName)
        .append(SLASH).append(MAP_TOPICMETA.localName)
        .append(SLASH).append(TOPIC_KEYWORDS.localName).toString());

        Properties properties = null;
        try{
            properties = ListUtils.getDitaList();
        }catch(final IOException e){
            throw new DITAOTException(e);
        }

        final Set<String> fullditamaplist = StringUtils.restoreSet(properties.getProperty(FULL_DITAMAP_LIST));
        for(final String fileName : fullditamaplist){
            //FIXME: this reader needs parent directory for further process
            indexReader.read(new File(tempDir, fileName).getAbsolutePath());
        }

        mapSet = (Set<Map.Entry<String, String>>) indexReader.getContent().getCollection();
        i = mapSet.iterator();
        while (i.hasNext()) {
            final Map.Entry<String, String> entry = i.next();
            targetFileName = entry.getKey();
            logger.logInfo("Processing " + targetFileName);
            targetFileName = targetFileName.indexOf(SHARP) != -1
                    ? targetFileName.substring(0, targetFileName.indexOf(SHARP))
                            : targetFileName;
                    if (targetFileName.endsWith(FILE_EXTENSION_DITA) ||
                            targetFileName.endsWith(FILE_EXTENSION_XML)){
                        content.setValue(entry.getValue());
                        indexInserter.setContent(content);
                        if (FileUtils.fileExists(entry.getKey())){
                            indexInserter.write(entry.getKey());
                        }else{
                            logger.logError(" ERROR FILE DOES NOT EXIST " + entry.getKey());
                        }

                    }
        }
        return null;
    }

}

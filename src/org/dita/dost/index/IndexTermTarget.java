/*
 * This file is part of the DITA Open Toolkit project hosted on
 * Sourceforge.net. See the accompanying license.txt file for 
 * applicable licenses.
 */

/*
 * (c) Copyright IBM Corp. 2005 All Rights Reserved.
 */
package org.dita.dost.index;

import org.dita.dost.util.Constants;

/**
 * This class represent the target of an index term.
 * 
 * @version 1.0 2005-05-11
 * 
 * @author Wu, Zhi Qiang
 */
public class IndexTermTarget {
    /** Name (title) of the target topic */
    private String targetName = null;

    /** URI of the target topic */
    private String targetURI = null;

    /**
     * Create a empty target.
     */
    public IndexTermTarget() {
    }

    /**
     * Get the target topic's name (title).
     * 
     * @return Returns the targetName.
     */
    public String getTargetName() {
        return targetName;
    }

    /**
     * Set the target topic's name (title).
     * 
     * @param name
     *            The targetName to set.
     */
    public void setTargetName(String name) {
        this.targetName = name;
    }

    /**
     * Get the target topic's URI.
     * 
     * @return Returns the targetURI.
     */
    public String getTargetURI() {
        return targetURI;
    }

    /**
     * Set the target topic's URI.
     * 
     * @param uri
     *            The targetURI to set.
     */
    public void setTargetURI(String uri) {
        this.targetURI = uri;
    }

    /**
     * The index term targets will be equal if the target topics have same name and URI value.
     * 
     * @param obj
     */
    public boolean equals(Object obj) {
        if (obj instanceof IndexTermTarget) {
            IndexTermTarget target = (IndexTermTarget) obj;
            
            if (targetName.equals(target.getTargetName())
                    && targetURI.equals(target.getTargetURI())) {
                return true;
            }
        }

        return false;
    }
    
    /**
     * Generate hash code for IndexTermTarget
     */
    public int hashCode() {
        int result = Constants.INT_17;

        result = Constants.INT_37 * result + targetName.hashCode();
        result = Constants.INT_37 * result + targetURI.hashCode();

        return result;
    }

	/** 
	 * Generate String for IndexTermTarget, with the format "{Target name: name, Target URL: uri}"
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return new StringBuffer("{Target name: ").append(targetName).append(
				", Target URL: ").append(targetURI).append("}").toString();
	}
}

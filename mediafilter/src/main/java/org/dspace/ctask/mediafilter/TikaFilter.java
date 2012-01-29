/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.mediafilter;

import java.io.InputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.apache.log4j.Logger;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParseContext;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Context;
import org.dspace.curate.Curator;

/**
 * TikaFilter tasks leverage the Apache Tika framework to perform content
 * and metadata extraction from Item bitstreams.
 *
 * @author richardrodgers
 */
public abstract class TikaFilter extends MediaFilter
{
	private static final Logger log = Logger.getLogger(TikaFilter.class);
	// map of mime-types to Tika parsers that can process them
	protected Map<String, Parser> mimeMap = new HashMap<String, Parser>();
	protected ParseContext pctx = null;
    
    @Override 
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        String parsers = taskProperty("filter.parsers");
        Map<String, Parser> specialParsers = new HashMap<String, Parser>();
        if (parsers != null)  {
        	for (String parser : parsers.split(",")) {
        		specialParsers.put(parser, null);
        	}
        }
        // interrogate installed parsers and collect mime types they support
        Iterator<Parser> parserIter = ServiceLoader.load(Parser.class).iterator();
        pctx = new ParseContext();
        while (parserIter.hasNext()) {
    		Parser parser = parserIter.next();
    		for (MediaType mt : parser.getSupportedTypes(pctx)) {
    			mimeMap.put(mt.getType(), parser);
    		}
    		// add to special parsers list if present
    		String pname = parser.getClass().getName();
    		if (specialParsers.containsKey(pname)) {
    			specialParsers.put(pname, parser);
    		}
        }
        // next, specially configured parsers trump default ones
        if (specialParsers.size() > 0) {
        	for (String pname : specialParsers.keySet()) {
        		Parser sparser = specialParsers.get(pname);
        		if (sparser != null) {
        			for (MediaType mt : sparser.getSupportedTypes(pctx)) {
        				mimeMap.put(mt.getType(), sparser);
        			}
        		} else {
        			log.error("Could not find configured parser: " + pname);
        		}
        	}
        }
    }
    
    @Override
    protected boolean canFilter(Item item, Bitstream bitstream) {
    	return mimeMap.containsKey(bitstream.getFormat().getMIMEType());
    }
}

/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.mediafilter;

import java.io.IOException;
import java.sql.SQLException;

import org.apache.log4j.Logger;
import org.apache.commons.io.input.ReaderInputStream;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParsingReader;
import org.apache.tika.parser.ParseContext;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Item;
import org.dspace.curate.Mutative;

/**
 * ExtractText task produces text derivatives and stores them in designated
 * bundles. Primary purpose is to expose this derivative artifact for indexing,
 * although it could also be regarded as a crude preservation transformation.
 * Roughly equivalent to the family of MediaFilters for text extraction
 * (PDF, HTML, Word, Powerpoint, etc) from org.dspace.app.mediafilter,
 * but invokes the Apache Tika framework to provide extraction services. 
 * Succeeds if one or more derivatives are created, otherwise fails.
 *
 * @author richardrodgers
 */
@Mutative
public class ExtractText extends TikaFilter
{
	private static final Logger log = Logger.getLogger(ExtractText.class);

    @Override
    protected boolean filterBitstream(Item item, Bitstream bitstream)
        		throws AuthorizeException, IOException, SQLException {
    	Parser parser = mimeMap.get(bitstream.getFormat().getMIMEType());
    	if (parser != null) {
    		pctx.set(Parser.class, parser);
    		return createDerivative(item, bitstream,
    								new ReaderInputStream(
    				new ParsingReader(parser, bitstream.retrieve(), new Metadata(), pctx)));
    	} 
    	return false;
    }
}

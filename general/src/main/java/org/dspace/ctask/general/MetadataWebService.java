/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import java.io.InputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.DCValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Curator;
import org.dspace.curate.Mutative;
import org.dspace.curate.Suspendable;

/**
 * MetadataWebService task calls a web service using metadata from
 * passed item to obtain data. Depending on configuration, this
 * data may be assigned to item metadata fields, or just recorded in the
 * task result string. Task succeeds if web service call succeeds and 
 * configured updates occur, fails if task user not authorized or item
 * lacks metadata to call service, and returns error in all other cases
 * (except skip status for non-item objects).
 * Intended use: cataloging tool in workflow and general curation.
 * The task uses a URL 'template' to compose the service call, e.g.
 * 
 * http://www.sherpa.ac.uk/romeo/api29.php?issn={dc.identifier.issn}
 * 
 * Task will substitute the value of the passed item's metadata field
 * in the {parameter} position. If multiple values are present in the
 * item field, the first value is used.
 * 
 * The task also uses a property (the datamap) to determine what data
 * to extract from the service response and how to use it, e.g.
 * 
 * //publisher/name=>dc.publisher,//romeocolour
 * 
 * Task will evaluate the left-hand side (or entire token) of each
 * comma-separated token in the property as an XPath 1.0 expression into
 * the response document, and if there is a mapping symbol (e.g.'=>') and
 * value, it will assign the response document value(s) to the named
 * metadata field in the passed item. If the response document contains
 * multiple values, they will all be assigned to the item field. The
 * mapping symbol governs the nature of metadata field assignment:
 * 
 * '->' mapping will add to any existing values in the item field
 * '=>' mapping will replace any existing values in the item field
 * '~>' mapping will add *only* if item field has no existing values
 * 
 * Unmapped data (without a mapping symbol) will simply be added to the task
 * result string, prepended by the XPath expression (a little prettified).
 * 
 * A very rudimentary facility for transformation of data is supported, e.g.
 * 
 * http://www.crossref.org/openurl/?id={doi:dc.relation.isversionof}&format=unixref
 *
 * The 'doi:' prefix will cause the task to look for a 'transform' with that
 * name, which is applied to the metadata value before parameter substitution
 * occurs. Transforms are defined in a task property such as the following:
 * 
 * transform.doi = match 10. trunc 60
 * 
 * This means exclude the value string up to the occurrence of '10.', then
 * truncate after 60 characters. The only transform functions currently defined:
 * 
 * 'cut' <number> = remove number leading characters
 * 'trunc' <number> = remove trailing characters after number length
 * 'match' <pattern> = start match at pattern
 * 
 * If the transform results in an invalid state (e.g. cutting more characters
 * than are in the value), the condition will be logged and the 
 * un-transformed value used.
 *
 * Transforms may also be used in datamaps, e.g.
 * 
 *  //publisher/name=>shorten:dc.publisher,//romeocolour
 *  
 *  which would apply the 'shorten' transform to the service response value(s)
 *  prior to metadata field assignment.
 * 
 * @author richardrodgers
 */
@Mutative
@Suspendable
public class MetadataWebService extends AbstractCurationTask
{
    /** log4j category */
    private static final Logger log = Logger.getLogger(MetadataWebService.class);
    // URL of web service with template parameters
    private String urlTemplate = null;
    // template parameter
    private String templateParam = null;
    // Item metadata field to use in service call
    private String lookupField = null;
    // Optional transformation of lookupField
    private String lookupTransform = null;
    // response data to map/record
    private List<DataInfo> dataList = null;
    // response document parsing tools
    private DocumentBuilder docBuilder = null;
    
    /**
     * Initializes task
     * @param curator  Curator object performing this task
     * @param taskId the configured local name of the task 
     */
    @Override
    public void init(Curator curator, String taskId) throws IOException {
    	super.init(curator, taskId);
    	urlTemplate = taskProperty("template");
    	templateParam = urlTemplate.substring(urlTemplate.indexOf("{") + 1,
    			                              urlTemplate.indexOf("}"));
    	String[] parsed = parseTransform(templateParam);
    	lookupField = parsed[0];
    	lookupTransform = parsed[1];
    	XPath xpath = XPathFactory.newInstance().newXPath();
    	dataList = new ArrayList<DataInfo>();
    	try {
    		for (String entry : taskProperty("datamap").split(",")) {
    			String src = entry;
    			String mapping = null;
    			String field = null;
    			int mapIdx = getMapIndex(entry);
    			if (mapIdx > 0) {
    				src = entry.substring(0, mapIdx);
    				mapping = entry.substring(mapIdx, mapIdx + 2);
    				field = entry.substring(mapIdx + 2);
    			}
    			int slIdx = src.lastIndexOf("/");
        		String label = (slIdx > 0) ? src.substring(slIdx + 1) : src;
    			XPathExpression expr = xpath.compile(src);
    			dataList.add(new DataInfo(expr, label, mapping, field));
    		}
    	} catch (XPathExpressionException xpeE) {
    		log.error("caught exception: " + xpeE);
        	// no point in continuing
        	throw new IOException(xpeE.getMessage(), xpeE);  			
    	}
    	// initialize response document parser
    	DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    	factory.setNamespaceAware(true);
    	try {
    		docBuilder = factory.newDocumentBuilder();
    	} catch (ParserConfigurationException pcE) {
    		log.error("caught exception: " + pcE);
    		// no point in continuing
    		throw new IOException(pcE.getMessage(), pcE);
    	}
    }
    
    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @throws IOException
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException  {
    	
    	int status = Curator.CURATE_SKIP;
    	StringBuilder resultSb = new StringBuilder();
    	
        if (dso.getType() == Constants.ITEM) {
            Item item = (Item)dso;
            String itemId = item.getHandle();
            if (itemId == null) {
            	// we are still in workflow - no handle assigned - try title
            	DCValue[] titleDc = item.getMetadata("dc", "title", null, Item.ANY);
            	String title = (titleDc.length > 0) ? titleDc[0].value : "untitled - dbId: " + item.getID();
            	itemId = "Workflow item: " + title;
            }
            resultSb.append(itemId);
            // Only proceed if item has a value for service template parameter
            DCValue[] dcVals = item.getMetadata(lookupField);
            if (dcVals.length > 0 && dcVals[0].value.length() > 0) {
            	String value = transform(dcVals[0].value, lookupTransform);
            	status = callService(value, item, resultSb);
            } else {
            	resultSb.append(" lacks metadata value required for service: ").append(lookupField);
            	status = Curator.CURATE_FAIL;
            }
        } else {
           resultSb.append("Object skipped");
        }
        report(resultSb.toString());
        setResult(resultSb.toString());
        return status;
    }
    
    private int callService(String value, Item item, StringBuilder resultSb) throws IOException {
    	
    	String callUrl = urlTemplate.replaceAll("\\{" + templateParam + "\\}", value);
    	HttpClient client = new DefaultHttpClient();
    	HttpGet req = new HttpGet(callUrl);
    	HttpResponse resp = client.execute(req);
    	int status = Curator.CURATE_ERROR;
    	int statusCode = resp.getStatusLine().getStatusCode();
    	if (statusCode == HttpStatus.SC_OK) {
    		HttpEntity entity = resp.getEntity();
    		if (entity != null) {
    			// boiler-plate handling taken from Apache 4.1 javadoc
    			InputStream instream = entity.getContent();
                try {
                	Document doc = docBuilder.parse(instream);
                	status = processResponse(doc, item, resultSb);
            	} catch (SAXException saxE) {
            		log.error("caught exception: " + saxE);
            		resultSb.append(" unable to read response document");
                } catch (RuntimeException ex) {
                	// In case of an unexpected exception you may want to abort
                	// the HTTP request in order to shut down the underlying
                	// connection and release it back to the connection manager.
                	req.abort();
                	log.error("caught exception: " + ex);
                	throw ex;
                } finally {
                	// Closing the input stream will trigger connection release
                	instream.close();
                }
                // When HttpClient instance is no longer needed,
                // shut down the connection manager to ensure
                // immediate deallocation of all system resources
                client.getConnectionManager().shutdown();
    		} else {
    			log.error(" obtained no valid service response");
    			resultSb.append("no service response");
    		}
    	} else {
    		log.error("service returned non-OK status: " + statusCode);
    		resultSb.append("no service response");
    	}
    	return status;
    }
    
    private int processResponse(Document doc, Item item, StringBuilder resultSb) {
       	boolean update = false;
       	int status = Curator.CURATE_ERROR;
       	try {
       		for (DataInfo info : dataList) {
       			NodeList nodes = (NodeList)info.expr.evaluate(doc, XPathConstants.NODESET);
       			// if data found and we are mapping, check assignment policy
       			if (nodes.getLength() > 0 && info.mapping != null) {
       				if ("=>".equals(info.mapping)) {
       					item.clearMetadata(info.schema, info.element, info.qualifier, Item.ANY);
       				} else if ("~>".equals(info.mapping)) {
       					if (item.getMetadata(info.schema, info.element, info.qualifier, Item.ANY).length > 0) {
       						// there are values, so don't overwrite
       						continue;
       					}
       				}
       			}
       			for (int i = 0; i < nodes.getLength(); i++) {
       				Node node = nodes.item(i);
       				String tvalue = transform(node.getFirstChild().getNodeValue(), info.transform);
       				// assign to metadata field if mapped
       				if (info.mapping != null) {
       					item.addMetadata(info.schema, info.element, info.qualifier, Item.ANY, tvalue);
       					update = true;
       				}
       				// add to result string in any case
       				resultSb.append(info.label).append(": ").append(tvalue).append(" ");
       			}
       		}
       		// update Item if it has changed
       		if (update) {
       			item.update();
       		}
       		status = Curator.CURATE_SUCCESS;
       	} catch (AuthorizeException authE) {
    		log.error("caught exception: " + authE);
    		resultSb.append(" not authorized to update");
    		status = Curator.CURATE_FAIL;
       	} catch (SQLException sqlE) {
    		log.error("caught exception: " + sqlE);
    		resultSb.append(" error updating metadata");
       	} catch (XPathExpressionException xpeE) {
    		log.error("caught exception: " + xpeE);
    		resultSb.append(" error reading response document");
       	}
        return status;
    }
    
    private String transform(String value, String transDef) {
    	if (transDef == null) {
    		return value;
    	}
    	String[] tokens = transDef.split("\\s+");
    	String retValue = value;
    	for (int i = 0; i < tokens.length; i+= 2) {
    		if ("cut".equals(tokens[i]) || "trunc".equals(tokens[i])) {
    			int index = Integer.parseInt(tokens[i+1]);
    			if (retValue.length() > index) {
    				if ("cut".equals(tokens[i])) {
    					retValue = retValue.substring(index);
    				} else {
    					retValue = retValue.substring(0, index);
    				}
    			} else {
    				log.error("requested cut/trunc: " + index + " exceeds value length");
    				return value;
    			}
    		} else if ("match".equals(tokens[i])) {
    			int index2 = retValue.indexOf(tokens[i+1]);
    			if (index2 > 0) {
    				retValue = retValue.substring(index2);
    			} else {
    				log.error("requested match: " + tokens[i+1] + " failed");
    				return value;
    			}
    		} else {
    			log.error(" unknown transform operation: " + tokens[i]);
    			return value;
    		}
    	}
    	return retValue;
    }
    
    private int getMapIndex(String mapping) {
    	int index = mapping.indexOf("->");
    	if (index == -1) {
    		index = mapping.indexOf("=>");
    	}
    	if (index == -1) {
    		index = mapping.indexOf("~>");
    	}
    	return index;
    }

    private String[] parseTransform(String field) {
    	String[] parsed = new String[2];
    	parsed[0] = field;
       	int txIdx = field.indexOf(":");
    	if (txIdx > 0) {
    		// transform specified
    		String txName = field.substring(0, txIdx);
    		parsed[1] = taskProperty("transform." + txName);
    		if (parsed[1] == null) {
    			log.error("no transform found for: " + txName);
    		}
    		parsed[0] = field.substring(txIdx + 1);
    	}
    	return parsed;
    }
    
    private class DataInfo {
    	public XPathExpression expr; // compiled XPath espression for data
    	public String label;		// label for data in result string
    	public String mapping;		// data mapping symbol: ->,=>,~>, or null = unmapped
    	public String schema;		// item metadata field mapping target, null = unmapped
    	public String element;		// item metadata field mapping target, null = unmapped
    	public String qualifier;	// item metadata field mapping target, null = unmapped
    	public String transform;	// optional transformation of data before field assignment
    	
    	public DataInfo(XPathExpression expr, String label, String mapping, String field) {
    		this.expr = expr;
    		this.label = label;
    		this.mapping = mapping;
    		if (field != null) {
    			String[] parsed = parseTransform(field);
    			String[] parts = parsed[0].split("\\.");
    			this.schema = parts[0];
    			this.element = parts[1];
    			this.qualifier = (parts.length == 3) ? parts[2] : null;
    			this.transform = parsed[1];
    		}
    	}
    }
}
/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.general;

import java.io.IOException;
import java.sql.SQLException;

import org.dspace.content.DCValue;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.curate.AbstractCurationTask;

import static org.dspace.curate.Curator.*;

/**
 * RoboIssueDate determines whether the dc.date.issued was
 * automatically assigned, using equality with
 * dc.date.accessioned as the test.
 * Task succeeds if it's a robo-date, else fails.
 * Upon success, task will write a CSV line to the reporting
 * stream with:
 * id,collectionId,title,date.issued,date.accessioned
 *
 * @author richardrodgers
 */

public class RoboIssueDate extends AbstractCurationTask
{   
    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @throws IOException
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException  {
        if (dso.getType() == Constants.ITEM) {
            try {
                Item item = (Item)dso;
                DCValue[] issued = item.getMetadata("dc.date.issued");
                DCValue[] accessioned = item.getMetadata("dc.date.accessioned");
                if (issued != null && accessioned != null && issued[0].value.equals(accessioned[0].value)) {
                    StringBuilder csv = new StringBuilder();
                    csv.append(item.getID()).append(",").
                    append(item.getOwningCollection().getID()).append(",").
                    append("\"").append(item.getName()).append("\",").
                    append(issued[0].value).append(",").
                    append(accessioned[0].value).append("\n");
                    report(csv.toString());
                    return CURATE_SUCCESS;
                }
                return CURATE_FAIL;
            } catch (SQLException sqlE) {
                throw new IOException("Caught SQL exception: " + sqlE.getMessage());
            }
        } else {
            return CURATE_SKIP;
        }
    }
}

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

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Collection;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.content.ItemIterator;
import org.dspace.core.Constants;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Distributive;
import org.dspace.curate.Mutative;
import org.dspace.handle.HandleManager;

import static org.dspace.curate.Curator.*;

/**
 * MergeCollection moves all items from the collection on which the task is
 * performed to the configured merge target collection. Merge target must
 * exist, and is specified by the required task property 'target' (which 
 * takes a handle). The other (boolean) task property 'inheritdefaults' if
 * true causes the items policies to be reset to the default values of the
 * target. Task succeeds if all items are merged, error if target cannot
 * be resolved or not a collection handle.
 *
 * @author richardrodgers
 */

@Distributive
@Mutative
public class MergeCollection extends AbstractCurationTask {   
    /**
     * Perform the curation task upon passed DSO
     *
     * @param dso the DSpace object
     * @throws IOException
     */
    @Override
    public int perform(DSpaceObject dso) throws IOException  {
        if (dso.getType() == Constants.COLLECTION) {
            Collection fromColl = (Collection)dso;
            Collection toColl = null;
            boolean inheritDefaults = taskBooleanProperty("inheritdefaults", true);
            int numMerged = 0;
            try {
                DSpaceObject toDso = HandleManager.resolveToObject(curationContext(), taskProperty("target"));
                if (toDso != null && toDso.getType() == Constants.COLLECTION) {
                    toColl = (Collection)toDso;
                } else {
                    setResult("Invalid target collection: " + taskProperty("target"));
                    return CURATE_ERROR;
                }
                ItemIterator fromIter = fromColl.getItems();
                while (fromIter.hasNext()) {
                    Item item = fromIter.next();
                    item.move(fromColl, toColl, inheritDefaults);
                    // decache item to prevent memory problems if collection very large
                    item.decache();
                    ++numMerged;
                }
            } catch (AuthorizeException authE) {
                throw new IOException("AuthorizeException: " + authE.getMessage());
            } catch (SQLException sqlE) {
                throw new IOException("SQLException: " + sqlE.getMessage());
            }
            setResult(numMerged + "items have been merged from: " + fromColl.getHandle() + " into : " + toColl.getHandle());
            return CURATE_SUCCESS;
        } else {
            setResult("Not a collection, skipped");
            return CURATE_SKIP;
        }
    }
}

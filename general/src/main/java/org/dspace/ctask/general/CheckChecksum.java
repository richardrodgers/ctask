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
import org.dspace.content.Bitstream;
import org.dspace.content.Bundle;
import org.dspace.content.DSpaceObject;
import org.dspace.content.Item;
import org.dspace.core.Constants;
import org.dspace.curate.AbstractCurationTask;
import org.dspace.curate.Suspendable;
import org.dspace.curate.Utils;

import static org.dspace.curate.Curator.*;

/**
 * CheckChecksum computes a checksum for each selected bitstream
 * and compares it to the stored ingest-time calculated value.
 * Task succeeds if all checksums agree, else fails.
 *
 * @author richardrodgers
 */

@Suspendable(invoked=Invoked.INTERACTIVE)
public class CheckChecksum extends AbstractCurationTask
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
            Item item = (Item)dso;
            try {
                for (Bundle bundle : item.getBundles()) {
                    for (Bitstream bs : bundle.getBitstreams()) {
                        String compCs = Utils.checksum(bs.retrieve(), bs.getChecksumAlgorithm());
                        if (! compCs.equals(bs.getChecksum())) {
                            String result = "Checksum discrepancy in item: " + item.getHandle() +
                                      " for bitstream: '" + bs.getName() + "' (seqId: " + bs.getSequenceID() + ")" +
                                      " ingest: " + bs.getChecksum() + " current: " + compCs;
                            report(result);
                            setResult(result);
                            return CURATE_FAIL;
                        }
                    }
                }
            } catch (AuthorizeException authE) {
                throw new IOException("AuthorizeException: " + authE.getMessage());
            } catch (SQLException sqlE) {
                throw new IOException("SQLException: " + sqlE.getMessage());
            }
            setResult("All bitstream checksums agree in item: " + item.getHandle());
            return CURATE_SUCCESS;
        } else {
            return CURATE_SKIP;
        }
    }
}

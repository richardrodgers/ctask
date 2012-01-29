/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.ctask.mediafilter;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import org.dspace.authorize.AuthorizeException;
import org.dspace.content.Bitstream;
import org.dspace.content.BitstreamFormat;
import org.dspace.content.Item;
import org.dspace.curate.Curator;
import org.dspace.curate.Mutative;

/**
 * ScaleImage task produces image derivatives and stores them in
 * designated bundles. Rough equivalent to the merger of the thumbnail
 * (JPEG) and branded image functionality in MediaFilter. The task
 * succeeds if all eligible derivatives are created, otherwise fails.
 * - Credits to authors of JPEGFilter and BrandedPreview code, from
 * which most of this was taken.
 * 
 * @author richardrodgers
 */
@Mutative
public class ScaleImage extends MediaFilter
{
	// derivative image dimensions
	private float maxWidth = 0;
	private float maxHeight = 0;
	// optional branding parameters
	private int brandHeight = 0;
	private String brandText = null;
	private String brandAbbrev = null;
	private String brandFont = null;
	private int brandFontPoint = 0;
	// supported input formats
	private List<String> mimeTypes = Arrays.asList(ImageIO.getReaderMIMETypes());
	private List<String> suffixes = Arrays.asList(ImageIO.getReaderFileSuffixes());
    
    @Override 
    public void init(Curator curator, String taskId) throws IOException {
        super.init(curator, taskId);
        
        maxWidth = Float.parseFloat(taskProperty("image.maxwidth"));
        maxHeight = Float.parseFloat(taskProperty("image.maxheight"));
        // optional branding properties - any or all may be null
        brandHeight = taskIntProperty("brand.height", 0);
        brandText = taskProperty("brand.text");
        brandAbbrev = taskProperty("brand.abbrev");
        brandFont = taskProperty("brand.font");
        brandFontPoint = taskIntProperty("brand.fontpoint", 0);
    }
    
    @Override
    protected boolean canFilter(Item item, Bitstream bitstream) {
    	BitstreamFormat bsf = bitstream.getFormat();
    	if (mimeTypes.contains(bsf.getMIMEType())) {
    		return true;
    	}
    	// now grovel thru the file suffixes
    	for (String sfx: bsf.getExtensions()) {
    		if (suffixes.contains(sfx)) {
    			return true;
    		}
    	}
    	return false;
    }
    
    @Override
    protected boolean filterBitstream(Item item, Bitstream bitstream)
    		throws AuthorizeException, IOException, SQLException {
    
        // read in bitstream's image
        BufferedImage buf = ImageIO.read(bitstream.retrieve());

        // now get the image dimensions
        float xsize = (float) buf.getWidth(null);
        float ysize = (float) buf.getHeight(null);

        // scale by x first if needed
        if (xsize > maxWidth) {
            // calculate scaling factor so that xsize * scale = new size (max)
            float scale_factor = maxWidth / xsize;

            // now reduce x and y size
            xsize = xsize * scale_factor;
            ysize = ysize * scale_factor;
        }

        // scale by y if needed
        if (ysize > maxHeight) {
            float scale_factor = maxHeight / ysize;

            // now reduce x size
            // and y size
            xsize = xsize * scale_factor;
            ysize = ysize * scale_factor;
        }

        // create an image buffer for the dervative with the new xsize, ysize
        BufferedImage derivative = new BufferedImage((int) xsize, (int) ysize + brandHeight,
                                                    BufferedImage.TYPE_INT_RGB);

        // now render the image into the derivative buffer
        Graphics2D g2d = derivative.createGraphics();
        g2d.drawImage(buf, 0, 0, (int) xsize, (int) ysize, null);
        
        // insert branding if defined
        if (brandHeight > 0) {
        	Font font = new Font(brandFont, Font.PLAIN, brandFontPoint);
        	String id = (item.getHandle() == null) ? "" : "hdl:" + item.getHandle();
        	int width = (int)xsize;
        	BufferedImage brandImage = new BufferedImage(width, brandHeight, BufferedImage.TYPE_INT_RGB);
        	// Do fitting calculations based on overall image size (hard-coded! - may should redo)
        	String text = null;
        	if (width >= 350) {
        		text = brandText;
        	} else if (width >= 190) {
        		text = brandAbbrev;
        	}
        	if (text != null) {
        		drawTextImage(brandImage, "bl", font, text);  // bl = bottom left
        	}
        	drawTextImage(brandImage, "br", font, id);  // br = bottom right
    		g2d.drawImage(brandImage, 0, (int)ysize, width, (int) 20, null);
        }

        // now create an input stream for the thumbnail buffer
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        ImageIO.write(derivative, "jpeg", baos);

        return createDerivative(item, bitstream, new ByteArrayInputStream(baos.toByteArray()));
    }
    
	/**
	 * do the text placements and preparatory work for the brand image generation
	 */
	private void drawTextImage(BufferedImage brandImage, String location, Font font, String brandText) {
		int imgWidth = brandImage.getWidth();
		int imgHeight = brandImage.getHeight();

		int bx, by, tx, ty, bWidth, bHeight;
		int xOffset = 5; // was hard-coded

		Graphics2D g2 = brandImage.createGraphics();
		g2.setFont(font);
		FontMetrics fm = g2.getFontMetrics();

		bWidth = fm.stringWidth(brandText) + xOffset * 2 + 1;
		bHeight = fm.getHeight();

		bx = 0;
        by = 0;

		if (location.equals("tl")) {
			bx = 0;
			by = 0;
		} else if (location.equals("tr")) {
			bx = imgWidth - bWidth;
			by = 0;
		} else if (location.equals("bl")) {
			bx = 0;
			by = imgHeight - bHeight;
		} else if (location.equals("br")) {
			bx = imgWidth - bWidth;
			by = imgHeight - bHeight;
		}

		Rectangle box = new Rectangle(bx, by, bWidth, bHeight);
		tx = bx + xOffset;
		ty = by + fm.getAscent();

    	g2.setColor(Color.black);
		g2.fill(box);
		g2.setColor(Color.white);
		g2.drawString(brandText, tx, ty);
	}
}

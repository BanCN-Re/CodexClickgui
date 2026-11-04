import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Rasterises the exact Rhine Lab mark from LBEILC/RhineLabUI src/brand.ts (MIT).
 * The two open ribbon subpaths are stroked at 26/310 units, the signs at 15.
 *
 * Run: java tools/MarkRender.java  (single-file source launch, JDK 11+)
 */
public class MarkRender {
    public static void main(String[] args) throws Exception {
        int scale = 4;
        int width = 310 * scale, height = 145 * scale;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(scale, scale);
        g.setColor(Color.WHITE);

        Path2D.Double ribbon = new Path2D.Double();
        ribbon.moveTo(156, 75);
        ribbon.curveTo(127, 48, 103, 15, 70, 15);
        ribbon.curveTo(37, 15, 15, 39, 15, 70);
        ribbon.curveTo(15, 101, 38, 128, 70, 128);
        ribbon.curveTo(103, 128, 127, 96, 176, 52);
        ribbon.moveTo(155, 75);
        ribbon.curveTo(182, 99, 208, 128, 240, 128);
        ribbon.curveTo(273, 128, 295, 105, 295, 73);
        ribbon.curveTo(295, 41, 273, 15, 240, 15);
        ribbon.curveTo(221, 15, 207, 23, 192, 38);
        g.setStroke(new BasicStroke(26, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(ribbon);

        Path2D.Double signs = new Path2D.Double();
        signs.moveTo(44, 70);
        signs.lineTo(94, 70);
        signs.moveTo(69, 45);
        signs.lineTo(69, 95);
        signs.moveTo(219, 70);
        signs.lineTo(263, 70);
        g.setStroke(new BasicStroke(15, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
        g.draw(signs);
        g.dispose();

        File out = new File("src/main/resources/assets/carnation/textures/rhine_mark.png");
        out.getParentFile().mkdirs();
        ImageIO.write(image, "PNG", out);
        System.out.println("wrote " + out + " " + out.length());
    }
}

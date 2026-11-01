package vn.studyfix.client.screen;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/** Local capture/encoding only. Streaming is enabled only after the server relay is integrated. */
public final class ScreenCaptureService {
    private final int maxWidth;
    private final float jpegQuality;
    public ScreenCaptureService(int maxWidth, float jpegQuality) {
        if (maxWidth < 320 || maxWidth > 3840) throw new IllegalArgumentException("maxWidth phải trong 320..3840");
        this.maxWidth = maxWidth; this.jpegQuality = Math.max(0.2f, Math.min(0.9f, jpegQuality));
    }
    public BufferedImage capture() throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Không có desktop/display để chụp màn hình.");
        Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage source = new Robot().createScreenCapture(bounds);
        double scale = Math.min(1.0, (double) maxWidth / source.getWidth());
        if (scale >= 1.0) return source;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        try { g.drawImage(source, 0, 0, width, height, null); } finally { g.dispose(); }
        return resized;
    }
    public byte[] captureJpeg() throws Exception { return toJpeg(capture()); }
    public byte[] toJpeg(BufferedImage image) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("Không có ImageIO JPEG writer.");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (params.canWriteCompressed()) { params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); params.setCompressionQuality(jpegQuality); }
            writer.write(null, new IIOImage(image, null, null), params); out.flush(); return bytes.toByteArray();
        } finally { writer.dispose(); }
    }
}

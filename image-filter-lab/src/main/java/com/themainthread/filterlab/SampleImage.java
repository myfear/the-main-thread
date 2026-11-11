package com.themainthread.filterlab;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import javax.imageio.ImageIO;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class SampleImage {

    static {
        System.setProperty("java.awt.headless", "true");
    }

    private static final int WIDTH = 1280;
    private static final int HEIGHT = 800;
    private static final int HORIZON = 520;
    private static final int STRIP = 72;

    private volatile byte[] encoded;

    public BufferedImage create() {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        drawSky(g);
        drawGlow(g);
        drawMountains(g);
        drawWater(g);
        drawBoat(g);
        drawGrain(image);
        drawTestStrip(g);

        g.dispose();
        return image;
    }

    public synchronized byte[] png() {
        if (encoded == null) {
            BufferedImage image = create();
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                ImageIO.write(image, "png", output);
                encoded = output.toByteArray();
            }
            catch (IOException exception) {
                throw new IllegalStateException("Unable to create the sample image", exception);
            }
        }
        return encoded;
    }

    private void drawSky(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(12, 27, 43), 0, HORIZON, new Color(239, 166, 103)));
        g.fillRect(0, 0, WIDTH, HORIZON);

        g.setColor(new Color(255, 231, 190, 34));
        g.fill(new Ellipse2D.Double(120, 90, 540, 54));
        g.setColor(new Color(255, 231, 190, 22));
        g.fill(new Ellipse2D.Double(640, 158, 470, 34));
        g.setColor(new Color(255, 240, 214, 16));
        g.fill(new Ellipse2D.Double(60, 300, 360, 22));
    }

    private void drawGlow(Graphics2D g) {
        int cx = 906;
        int cy = 322;
        for (int i = 180; i > 58; i -= 6) {
            int alpha = (int) (52 * (1 - (i - 58) / 130.0));
            g.setColor(new Color(255, 222, 164, Math.max(alpha, 0)));
            g.fill(new Ellipse2D.Double(cx - i, cy - i, i * 2, i * 2));
        }
        g.setColor(new Color(255, 240, 202));
        g.fill(new Ellipse2D.Double(cx - 56, cy - 56, 112, 112));
        g.setColor(new Color(255, 250, 232, 210));
        g.fill(new Ellipse2D.Double(cx - 46, cy - 46, 92, 92));
    }

    private void drawMountains(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 380, new Color(44, 66, 76), 0, HORIZON, new Color(25, 42, 50)));
        g.fill(new Polygon(
                new int[] { 0, 150, 300, 470, 640, 780, 980, 1140, WIDTH, WIDTH, 0 },
                new int[] { HORIZON, 432, 466, 398, 470, 412, 463, 430, 452, HORIZON, HORIZON }, 11));

        g.setPaint(new GradientPaint(0, 430, new Color(24, 38, 44), 0, HORIZON, new Color(11, 22, 27)));
        g.fill(new Polygon(
                new int[] { 0, 190, 360, 560, 735, 905, 1080, WIDTH, WIDTH, 0 },
                new int[] { HORIZON, 478, 500, 454, 492, 462, 486, 470, HORIZON, HORIZON }, 10));

        g.setColor(new Color(226, 188, 148, 58));
        g.setStroke(new BasicStroke(2f));
        g.drawLine(0, HORIZON, WIDTH, HORIZON);
    }

    private void drawWater(Graphics2D g) {
        g.setPaint(new GradientPaint(0, HORIZON, new Color(188, 116, 76), 0, HEIGHT - STRIP,
                new Color(15, 31, 38)));
        g.fillRect(0, HORIZON, WIDTH, HEIGHT - STRIP - HORIZON);

        Random random = new Random(17);
        g.setStroke(new BasicStroke(1f));
        for (int y = HORIZON + 8; y < HEIGHT - STRIP - 4; y += 4) {
            int alpha = 30 + random.nextInt(55);
            g.setColor(new Color(255, 213, 160, alpha));
            int x = random.nextInt(140);
            while (x < WIDTH) {
                int length = 14 + random.nextInt(90);
                g.drawLine(x, y, Math.min(WIDTH, x + length), y);
                x += length + 16 + random.nextInt(80);
            }
        }

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        for (int i = 0; i < 20; i++) {
            int y = HORIZON + 14 + i * 8;
            int width = 34 + i * 11;
            g.setColor(new Color(255, 220, 172, 80 - i * 3));
            g.fill(new Ellipse2D.Double(906 - width / 2.0, y, width, 3));
        }
        g.setComposite(AlphaComposite.SrcOver);
    }

    private void drawBoat(Graphics2D g) {
        g.setColor(new Color(12, 19, 22));
        g.fill(new Polygon(new int[] { 246, 372, 350, 268 }, new int[] { 610, 610, 646, 646 }, 4));
        g.setStroke(new BasicStroke(3f));
        g.drawLine(310, 610, 310, 512);
        g.setColor(new Color(238, 226, 205, 235));
        g.fill(new Polygon(new int[] { 312, 312, 358 }, new int[] { 520, 604, 604 }, 3));
        g.setColor(new Color(205, 91, 53, 220));
        g.fill(new Polygon(new int[] { 308, 308, 278 }, new int[] { 530, 600, 600 }, 3));
        g.setColor(new Color(8, 17, 21));
        g.fill(new Ellipse2D.Double(236, 644, 140, 8));
    }

    private void drawGrain(BufferedImage image) {
        Random random = new Random(41);
        for (int y = 0; y < HEIGHT - STRIP; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int rgb = image.getRGB(x, y);
                int noise = random.nextInt(11) - 5;
                int r = clamp(((rgb >> 16) & 0xff) + noise);
                int g = clamp(((rgb >> 8) & 0xff) + noise);
                int b = clamp((rgb & 0xff) + noise);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
    }

    private void drawTestStrip(Graphics2D g) {
        int top = HEIGHT - STRIP;
        g.setColor(new Color(14, 20, 23));
        g.fillRect(0, top, WIDTH, STRIP);

        int x = 26;
        int y = top + 14;
        int patch = 40;
        for (int i = 0; i < 16; i++) {
            int value = i * 255 / 15;
            g.setColor(new Color(value, value, value));
            g.fillRect(x, y, patch, patch);
            x += patch + 2;
        }

        x += 14;
        int[][] colours = {
                { 115, 82, 68 }, { 194, 150, 130 }, { 98, 122, 157 }, { 87, 108, 67 },
                { 133, 128, 177 }, { 103, 189, 170 }, { 214, 126, 44 }, { 80, 91, 166 },
                { 193, 90, 99 }, { 94, 60, 108 }, { 157, 188, 64 }, { 224, 163, 46 } };
        for (int[] colour : colours) {
            g.setColor(new Color(colour[0], colour[1], colour[2]));
            g.fillRect(x, y, patch, patch);
            x += patch + 2;
        }

        g.setColor(new Color(201, 196, 184));
        g.setFont(new Font("Monospaced", Font.PLAIN, 11));
        g.drawString("PEMBANA IMAGE FILTERS / TEST FRAME 01", 26, top + 68);
        g.drawString("1280 x 800  /  24-BIT RGB  /  BUILD 2026-11-11", 760, top + 68);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}

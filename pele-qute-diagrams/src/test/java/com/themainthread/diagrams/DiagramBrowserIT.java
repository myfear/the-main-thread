package com.themainthread.diagrams;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;

@QuarkusIntegrationTest
class DiagramBrowserIT {
    private static Playwright playwright;
    private static Browser browser;
    private BrowserContext context;
    private Page page;
    private final List<String> errors = new ArrayList<>();

    @TestHTTPResource
    URI baseUri;

    @BeforeAll
    static void openBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        if (playwright != null) playwright.close();
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1440, 1000));
        page = context.newPage();
        page.onPageError(errors::add);
        page.navigate(baseUri.toString());
        assertThat(page.locator("#capture")).isEnabled();
        assertThat(page.locator("#live-diagram .pele-node")).hasCount(7);
    }

    @AfterEach
    void closePage() {
        context.close();
        assertTrue(errors.isEmpty(), "Browser errors: " + errors);
    }

    @Test
    void changesGeometryAtTheContainerBreakpointWithoutChangingTheGraph() throws IOException {
        String source = page.locator("#source").inputValue();
        Object nodes = nodeIds();
        Object edges = edgeIds();
        setWidth(641);
        waitForDirection(false);
        setWidth(639);
        waitForDirection(true);
        assertEquals(nodes, nodeIds());
        assertEquals(edges, edgeIds());
        setWidth(360);
        waitForDirection(true);
        assertEquals(source, page.locator("#source").inputValue());
        saveScreenshot("narrow");
        setWidth(960);
        waitForDirection(false);
        saveScreenshot("desktop");
    }

    @Test
    void openingDetailsChangesTheDiagramWithoutChangingTheViewport() {
        Object viewportWidth = page.evaluate("window.innerWidth");
        waitForDirection(false);
        page.locator("#details-toggle").click();
        waitForDirection(true);
        assertEquals(viewportWidth, page.evaluate("window.innerWidth"));
        assertThat(page.locator("#order-details")).isVisible();
        page.locator("#details-toggle").click();
        waitForDirection(false);
    }

    @Test
    void scaleModePreservesGeometryAndUsesUniqueSvgIds() {
        page.locator("#layout").selectOption("scale");
        String viewBox = page.locator("#scaled-diagram svg").getAttribute("viewBox");
        setWidth(360);
        assertEquals(viewBox, page.locator("#scaled-diagram svg").getAttribute("viewBox"));
        assertTrue((Boolean) page.evaluate("""
                () => {
                    const ids = [...document.querySelectorAll('svg [id]')].map(el => el.id);
                    return new Set(ids).size === ids.length;
                }
                """));
        page.locator("#layout").selectOption("adapt");
        waitForDirection(true);
    }

    @Test
    void exportsIndependentSvgAndTwoTimesPngInBothThemes() throws IOException {
        for (String theme : List.of("light", "dark")) {
            page.locator("#theme").selectOption(theme);
            page.locator("#density").selectOption("2");
            page.locator("#capture").click();
            assertThat(page.locator("#download-svg")).isEnabled();
            assertThat(page.locator("#download-png")).isEnabled();
            page.waitForFunction("document.querySelector('#png-preview').naturalWidth > 0");
            String svgUrl = page.locator("#svg-preview").getAttribute("src");
            Path directory = Path.of("target", "browser-evidence");
            Files.createDirectories(directory);
            Download svgDownload = page.waitForDownload(() -> page.locator("#download-svg").click());
            Path svgPath = directory.resolve(theme + ".svg");
            svgDownload.saveAs(svgPath);
            Download pngDownload = page.waitForDownload(() -> page.locator("#download-png").click());
            Path pngPath = directory.resolve(theme + ".png");
            pngDownload.saveAs(pngPath);
            String svg = Files.readString(svgPath);
            assertTrue(svg.contains("http://www.w3.org/2000/svg"));
            assertTrue(svg.contains("Order received"));
            assertFalse(svg.contains("<script"));
            BufferedImage png = ImageIO.read(pngPath.toFile());
            Number width = (Number) page.locator("#svg-preview").evaluate("img => img.naturalWidth");
            Number height = (Number) page.locator("#svg-preview").evaluate("img => img.naturalHeight");
            assertEquals(width.intValue() * 2, png.getWidth());
            assertEquals(height.intValue() * 2, png.getHeight());
            assertEquals(theme.equals("light") ? 0xffffff : 0x252530, png.getRGB(0, 0) & 0xffffff);
            // Decode the file on a fresh page with no application stylesheet.
            try (Page independent = context.newPage()) {
                independent.navigate(svgPath.toAbsolutePath().toUri().toString());
                assertThat(independent.locator("svg")).isVisible();
                assertThat(independent.locator(".pele-node")).hasCount(7);
            }
            setWidth(360);
            waitForDirection(true);
            assertEquals(svgUrl, page.locator("#svg-preview").getAttribute("src"));
            setWidth(960);
            waitForDirection(false);
        }
        page.locator("#density").selectOption("1");
        page.locator("#capture").click();
        assertThat(page.locator("#download-png")).isEnabled();
        page.waitForFunction("document.querySelector('#png-preview').naturalWidth === document.querySelector('#svg-preview').naturalWidth");
    }

    @Test
    void invalidAndEmptyInputDisableExportsAndRecover() {
        page.locator("#capture").click();
        assertThat(page.locator("#download-svg")).isEnabled();
        page.locator("summary").click();
        page.locator("#source").fill("flowchart LR\n A[broken");
        assertThat(page.locator("#render-error")).isVisible();
        assertThat(page.locator("#capture")).isDisabled();
        assertThat(page.locator("#download-svg")).isDisabled();
        assertThat(page.locator("#previews")).isHidden();
        page.locator("#source").fill("");
        assertThat(page.locator("#render-error")).containsText("Enter a Mermaid flowchart");
        page.locator("#reset-source").click();
        assertThat(page.locator("#capture")).isEnabled();
        assertThat(page.locator("#render-error")).isHidden();
        assertThat(page.locator("#live-diagram .pele-node")).hasCount(7);
    }

    @Test
    void narrowViewportStaysContainedAndKeyboardControlsWork() throws IOException {
        page.setViewportSize(390, 844);
        waitForDirection(true);
        page.locator("#width").focus();
        page.keyboard().press("Home");
        assertThat(page.locator("#requested-width")).hasText("320 px");
        assertTrue((Boolean) page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"));
        saveScreenshot("mobile");
        page.locator("#details-toggle").click();
        assertTrue((Boolean) page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"));
        page.locator("#capture").click();
        assertThat(page.locator("#download-png")).isEnabled();
    }

    private void setWidth(int width) {
        page.locator("#width").evaluate("(input, value) => { input.value = value; input.dispatchEvent(new Event('input', { bubbles: true })); }", width);
        page.waitForFunction("expected => Math.round(document.querySelector('#diagram-width').getBoundingClientRect().width) === expected", width);
    }

    private void waitForDirection(boolean vertical) {
        page.waitForFunction("""
                vertical => {
                    const svg = document.querySelector('#live-diagram svg');
                    if (!svg) return false;
                    const start = svg.querySelector('[data-id="received"]').getCTM();
                    const next = svg.querySelector('[data-id="reserve"]').getCTM();
                    return vertical ? Math.abs(start.e - next.e) < 1 && next.f > start.f
                        : Math.abs(start.f - next.f) < 1 && next.e > start.e;
                }
                """, vertical);
    }

    private Object nodeIds() {
        return page.locator("#live-diagram .pele-node").evaluateAll("nodes => nodes.map(node => node.dataset.id).sort()");
    }

    private Object edgeIds() {
        return page.locator("#live-diagram .pele-edge").evaluateAll("edges => edges.map(edge => edge.dataset.id).sort()");
    }

    private void saveScreenshot(String name) throws IOException {
        Path directory = Path.of("target", "browser-evidence");
        Files.createDirectories(directory);
        page.screenshot(new Page.ScreenshotOptions().setFullPage(true).setPath(directory.resolve(name + ".png")));
    }
}

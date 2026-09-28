package com.themainthread.keyboard;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import com.deque.html.axecore.playwright.AxeBuilder;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.Tracing;
import com.microsoft.playwright.options.AriaRole;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

@QuarkusTest
class WorkflowTest {
    @TestHTTPResource("/")
    URL base;

    Playwright playwright;
    Browser browser;
    BrowserContext context;
    Page page;

    @BeforeEach
    void open() {
        playwright = Playwright.create();
        String engine = System.getProperty("browser", "chromium");
        BrowserType type = switch (engine) {
            case "firefox" -> playwright.firefox();
            case "webkit" -> playwright.webkit();
            default -> playwright.chromium();
        };
        browser = type.launch();
        context = browser.newContext();
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true));
        page = context.newPage();
    }

    @AfterEach
    void close(TestInfo info) throws Exception {
        try {
            if (context != null) {
                Path directory = Path.of("target", "browser-traces");
                Files.createDirectories(directory);
                context.tracing().stop(new Tracing.StopOptions().setPath(
                        directory.resolve(info.getTestMethod().orElseThrow().getName() + ".zip")));
                context.close();
            }
        } finally {
            if (browser != null) browser.close();
            if (playwright != null) playwright.close();
        }
    }

    private void start() {
        page.navigate(base.toString());
        page.keyboard().press("Tab");
        assertThat(page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Skip to request"))).isFocused();
        page.keyboard().press("Enter");
        page.keyboard().press("Tab");
        assertThat(page.getByLabel("Purpose (required)")).isFocused();
    }

    private void details(String purpose, String quantity) {
        page.keyboard().type(purpose);
        page.keyboard().press("Tab");
        assertThat(page.getByLabel("Quantity (required)")).isFocused();
        page.keyboard().press("ControlOrMeta+A");
        page.keyboard().type(quantity);
        page.keyboard().press("Tab");
        assertThat(page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Review request").setExact(true))).isFocused();
        page.keyboard().press("Enter");
    }

    private void scan() {
        var result = new AxeBuilder(page).analyze();
        assertTrue(result.getViolations().isEmpty(), result.getViolations().toString());
    }

    @Test
    void completeWorkflowUsingOnlyKeyboard() {
        start();
        scan();
        details("Short", "9");
        assertThat(page.locator(".errors")).isFocused();
        assertThat(page.locator(".errors li")).hasCount(2);
        assertThat(page.getByLabel("Purpose (required)")).hasAttribute("aria-invalid", "true");
        assertThat(page.getByLabel("Purpose (required)")).hasAttribute("aria-describedby", "purpose-hint purpose-error");
        assertThat(page.getByLabel("Quantity (required)")).hasValue("9");
        scan();
        page.keyboard().press("Tab");
        page.keyboard().press("Enter");
        assertThat(page.getByLabel("Purpose (required)")).isFocused();
        page.keyboard().press("ControlOrMeta+A");
        details("Java training workshop", "3");
        assertThat(page.locator("#step-title")).isFocused();
        assertThat(page.locator("#step-title")).hasText("Step 2 of 2: Review your request");
        scan();
        page.keyboard().press("Tab");
        assertThat(page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Edit details"))).isFocused();
        page.keyboard().press("Enter");
        assertThat(page.locator("#step-title")).hasText("Step 1 of 2: Request details");
        assertThat(page.locator("#step-title")).isFocused();
        assertThat(page.getByLabel("Purpose (required)")).hasValue("Java training workshop");
        assertThat(page.getByLabel("Quantity (required)")).hasValue("3");
        assertThat(page.getByLabel("Purpose (required)")).hasAttribute("aria-invalid", "false");
        scan();
        page.keyboard().press("Tab");
        page.keyboard().press("ControlOrMeta+A");
        details("Java training workshop", "3");
        assertThat(page.locator("#step-title")).isFocused();
        page.keyboard().press("Tab");
        page.keyboard().press("Tab");
        assertThat(page.getByRole(AriaRole.BUTTON,
                new Page.GetByRoleOptions().setName("Submit demo request"))).isFocused();
        page.keyboard().press("Enter");
        assertThat(page.locator("#step-title")).hasText("Demo request submitted");
        assertThat(page.locator("#step-title")).isFocused();
        assertThat(page.locator("#workflow")).containsText("for 3 laptops");
        assertThat(page).hasTitle("Demo request submitted — Equipment requests");
        scan();
    }

    @Test
    void slowResponseAnnouncesBusyAndDoesNotStealFocus() {
        AtomicReference<Route> pending = new AtomicReference<>();
        page.route("**/review", pending::set);
        start();
        details("Java training workshop", "2");
        assertThat(page.getByRole(AriaRole.STATUS)).hasText("Checking request details…");
        assertThat(page.locator("#workflow")).hasAttribute("aria-busy", "true");
        assertThat(page.getByRole(AriaRole.BUTTON)).hasAttribute("aria-disabled", "true");
        page.keyboard().press("Tab");
        assertThat(page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Start a new request"))).isFocused();
        pending.get().resume();
        assertThat(page.locator("#step-title")).hasText("Step 2 of 2: Review your request");
        assertThat(page.getByRole(AriaRole.STATUS)).containsText("Return to the request");
        assertThat(page.getByRole(AriaRole.LINK,
                new Page.GetByRoleOptions().setName("Start a new request"))).isFocused();
        assertThat(page.locator("#workflow")).hasAttribute("aria-busy", "false");
    }

    @Test
    void failedRequestRetainsDataAndCanBeRetried() {
        page.route("**/review", route -> route.fulfill(new Route.FulfillOptions().setStatus(503)));
        start();
        details("Java training workshop", "2");
        assertThat(page.getByRole(AriaRole.STATUS)).containsText("Try again");
        assertThat(page.getByLabel("Purpose (required)")).hasValue("Java training workshop");
        assertThat(page.getByRole(AriaRole.BUTTON)).isFocused();
        assertThat(page.locator("#workflow")).hasAttribute("aria-busy", "false");
        page.unroute("**/review");
        page.keyboard().press("Enter");
        assertThat(page.locator("#step-title")).hasText("Step 2 of 2: Review your request");
    }

    @Test
    void disconnectedRequestRetainsFocus() {
        page.route("**/review", Route::abort);
        start();
        details("Java training workshop", "2");
        assertThat(page.getByRole(AriaRole.STATUS)).containsText("Try again");
        assertThat(page.getByRole(AriaRole.BUTTON)).isFocused();
        assertThat(page.locator("#workflow")).hasAttribute("aria-busy", "false");
    }

    @Test
    void finalPostRevalidatesHiddenFields() throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(base + "confirm"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("HX-Request", "true")
                    .POST(HttpRequest.BodyPublishers.ofString("purpose=x&quantity=99&intent=submit"))
                    .build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(422, response.statusCode());
            assertTrue(response.body().contains("There are 2 errors"));
        }
    }

    @Test
    void worksWithoutJavaScript() {
        try (BrowserContext plain = browser.newContext(new Browser.NewContextOptions().setJavaScriptEnabled(false))) {
            Page original = page;
            page = plain.newPage();
            try {
                start();
                details("Java training workshop", "2");
                assertThat(page.locator("#step-title")).hasText("Step 2 of 2: Review your request");
                page.keyboard().press("Tab"); // Skip link after full navigation.
                page.keyboard().press("Enter");
                page.keyboard().press("Tab"); // Edit details.
                page.keyboard().press("Tab"); // Submit.
                assertThat(page.getByRole(AriaRole.BUTTON,
                        new Page.GetByRoleOptions().setName("Submit demo request"))).isFocused();
                page.keyboard().press("Enter");
                assertThat(page.locator("#step-title")).hasText("Demo request submitted");
            } finally {
                page = original;
            }
        }
    }
}

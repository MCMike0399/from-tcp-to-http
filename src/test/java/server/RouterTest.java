package server;

import http.HttpRequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.OutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Router in isolation. No server or network needed --
 * these verify the routing table logic: registration, lookup, and dispatch.
 */
@DisplayName("Module 4: Router Tests (E4.2)")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RouterTest {

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    /** A no-op handler, useful when we only need identity checks. */
    private static final HttpHandler NOOP = (req, out) -> {};

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    @Order(1)
    void router_exactMatch_findsHandler() {
        Router router = new Router();
        HttpHandler handler = (req, out) -> {};
        router.addRoute("GET", "/hello", handler);

        HttpHandler found = router.findHandler("GET", "/hello");

        assertThat(found)
                .as("After addRoute(\"GET\", \"/hello\", handler), "
                  + "findHandler(\"GET\", \"/hello\") must return that handler. "
                  + "The simplest implementation uses a Map keyed by (method, path).")
                .isNotNull()
                .isSameAs(handler);
    }

    @Test
    @Order(2)
    void router_noMatch_returnsNull() {
        Router router = new Router();
        router.addRoute("GET", "/hello", NOOP);

        HttpHandler found = router.findHandler("GET", "/missing");

        assertThat(found)
                .as("When no route matches the requested (method, path), "
                  + "findHandler() must return null. The server uses this signal "
                  + "to generate a 404 Not Found response.")
                .isNull();
    }

    @Test
    @Order(3)
    void router_multipleRoutes_correctDispatch() {
        Router router = new Router();
        HttpHandler handlerA = (req, out) -> {};
        HttpHandler handlerB = (req, out) -> {};
        HttpHandler handlerC = (req, out) -> {};

        router.addRoute("GET", "/a", handlerA);
        router.addRoute("GET", "/b", handlerB);
        router.addRoute("GET", "/c", handlerC);

        assertThat(router.findHandler("GET", "/a"))
                .as("Router must dispatch /a to handlerA, not to another handler.")
                .isSameAs(handlerA);

        assertThat(router.findHandler("GET", "/b"))
                .as("Router must dispatch /b to handlerB, not to another handler.")
                .isSameAs(handlerB);

        assertThat(router.findHandler("GET", "/c"))
                .as("Router must dispatch /c to handlerC, not to another handler.")
                .isSameAs(handlerC);
    }

    @Test
    @Order(4)
    void router_methodAware_differentHandlersPerMethod() {
        Router router = new Router();
        HttpHandler getHandler = (req, out) -> {};
        HttpHandler postHandler = (req, out) -> {};

        router.addRoute("GET", "/data", getHandler);
        router.addRoute("POST", "/data", postHandler);

        HttpHandler foundGet = router.findHandler("GET", "/data");
        HttpHandler foundPost = router.findHandler("POST", "/data");

        assertThat(foundGet)
                .as("GET /data and POST /data are distinct routes. "
                  + "The router key must include the HTTP method, not just the path. "
                  + "A common approach: key = method + \" \" + path.")
                .isSameAs(getHandler);

        assertThat(foundPost)
                .as("POST /data must return the post handler, not the GET handler.")
                .isSameAs(postHandler);

        assertThat(foundGet)
                .as("GET and POST handlers for the same path must be different objects.")
                .isNotSameAs(foundPost);
    }

    @Test
    @Order(5)
    void router_trailingSlash_handledConsistently() {
        Router router = new Router();
        HttpHandler handler = (req, out) -> {};
        router.addRoute("GET", "/hello", handler);

        HttpHandler withSlash = router.findHandler("GET", "/hello/");
        HttpHandler withoutSlash = router.findHandler("GET", "/hello");

        // The router may or may not normalize trailing slashes.
        // Both behaviors are acceptable, but the result must be deterministic.
        // We document the actual behavior here rather than prescribing one.
        assertThat(withoutSlash)
                .as("The registered path '/hello' must always match '/hello'.")
                .isSameAs(handler);

        // If trailing slash matches, both should be the same handler.
        // If it doesn't match, withSlash should be null.
        // Either is fine -- we just verify consistency.
        if (withSlash != null) {
            assertThat(withSlash)
                    .as("If the router normalizes trailing slashes, "
                      + "'/hello/' should resolve to the same handler as '/hello'. "
                      + "This is a reasonable convenience behavior.")
                    .isSameAs(handler);
        } else {
            assertThat(withSlash)
                    .as("The router treats '/hello' and '/hello/' as distinct paths. "
                      + "This is strict but correct -- HTTP URIs are case-sensitive "
                      + "and path-segment-sensitive per RFC 9110 Section 4.2.3.")
                    .isNull();
        }
    }
}

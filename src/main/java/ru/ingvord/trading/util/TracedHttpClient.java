package ru.ingvord.trading.util;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Namespace;
import com.amazonaws.xray.entities.Subsegment;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class TracedHttpClient extends HttpClient {
    private final HttpClient delegate;

    public TracedHttpClient(HttpClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public Optional<CookieHandler> cookieHandler() {
        return delegate.cookieHandler();
    }

    @Override
    public Optional<Duration> connectTimeout() {
        return delegate.connectTimeout();
    }

    @Override
    public Redirect followRedirects() {
        return delegate.followRedirects();
    }

    @Override
    public Optional<ProxySelector> proxy() {
        return delegate.proxy();
    }

    @Override
    public SSLContext sslContext() {
        return delegate.sslContext();
    }

    @Override
    public SSLParameters sslParameters() {
        return delegate.sslParameters();
    }

    @Override
    public Optional<Authenticator> authenticator() {
        return delegate.authenticator();
    }

    @Override
    public Version version() {
        return delegate.version();
    }

    @Override
    public Optional<Executor> executor() {
        return delegate.executor();
    }

    public <T> HttpResponse<T> send(
            HttpRequest request,
            HttpResponse.BodyHandler<T> responseBodyHandler
    ) throws IOException, InterruptedException {

        String url = request.uri().toString();
        String host = request.uri().getHost();
        String method = request.method();

        // Sanitize URL to remove sensitive data (bot tokens, API keys, etc.)
        String sanitizedUrl = sanitizeUrl(url);

        // Create subsegment for this HTTP call
        Subsegment subsegment = AWSXRay.beginSubsegment(host);

        try {
            // Set subsegment as HTTP type
            subsegment.setNamespace(Namespace.REMOTE.toString());

            // Add HTTP metadata
            subsegment.putHttp("request", Map.of(
                    "method", method,
                    "url", sanitizedUrl
            ));

            // Make the actual HTTP call
            HttpResponse<T> response = delegate.send(request, responseBodyHandler);

            // Add response metadata
            subsegment.putHttp("response", Map.of(
                    "status", response.statusCode(),
                    "content_length", response.headers()
                            .firstValue("content-length")
                            .orElse("unknown")
            ));

            // Mark as error if not 2xx
            if (response.statusCode() >= 400) {
                subsegment.setError(true);
                if (response.statusCode() >= 500) {
                    subsegment.setFault(true);
                }
            }

            return response;

        } catch (Exception e) {
            // Record exception
            subsegment.addException(e);
            throw e;
        } finally {
            // Always end the subsegment
            AWSXRay.endSubsegment();
        }
    }

    /**
     * Sanitize URL by removing sensitive information like bot tokens, API keys, etc.
     *
     */
    private String sanitizeUrl(String url) {
        // Redact Telegram bot tokens (format: /botTOKEN/method)
        return url.replaceAll("/bot[0-9]+:[A-Za-z0-9_-]+/", "/bot[REDACTED]/");
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
        throw new UnsupportedOperationException();
    }
}

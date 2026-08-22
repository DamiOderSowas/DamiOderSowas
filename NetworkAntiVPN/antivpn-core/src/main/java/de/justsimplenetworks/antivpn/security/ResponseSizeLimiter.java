package de.justsimplenetworks.antivpn.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * A {@link HttpResponse.BodyHandler} that enforces a hard cap on response
 * size, aborting the request as soon as the cap is exceeded instead of
 * buffering an unbounded amount of attacker- or provider-controlled data in
 * memory. Used by every outbound provider HTTP request.
 */
public final class ResponseSizeLimiter {

    private ResponseSizeLimiter() {
    }

    public static HttpResponse.BodyHandler<String> ofLimitedString(int maxBytes) {
        return responseInfo -> new BoundedBodySubscriber(maxBytes);
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<String> {

        private final int maxBytes;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final CompletableFuture<String> result = new CompletableFuture<>();
        private Flow.Subscription subscription;
        private int received;

        private BoundedBodySubscriber(int maxBytes) {
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<String> getBody() {
            return result;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            if (result.isDone()) {
                return;
            }
            for (ByteBuffer buf : item) {
                int remaining = buf.remaining();
                received += remaining;
                if (received > maxBytes) {
                    subscription.cancel();
                    result.completeExceptionally(
                            new IOException("Response exceeded the configured max size of " + maxBytes + " bytes"));
                    return;
                }
                byte[] bytes = new byte[remaining];
                buf.get(bytes);
                buffer.writeBytes(bytes);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            result.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (!result.isDone()) {
                result.complete(buffer.toString(StandardCharsets.UTF_8));
            }
        }
    }
}

package com.orders.config;

import java.net.URI;
import java.util.Locale;

import org.springframework.http.HttpMethod;
import org.springframework.lang.Nullable;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientRequestObservationContext;
import org.springframework.web.reactive.function.client.DefaultClientRequestObservationConvention;

/**
 * Nombres de span para salidas {@link org.springframework.web.reactive.function.client.WebClient} en Zipkin:
 * prefijo {@code webclient} para no confundirlos con spans <strong>servidor</strong> ({@code http …} entrantes al micro).
 */
public class DescriptiveClientRequestObservationConvention extends DefaultClientRequestObservationConvention {

    @Override
    public String getContextualName(ClientRequestObservationContext context) {
        ClientRequest request = context.getRequest();
        if (request == null) {
            return super.getContextualName(context);
        }
        String path = pathOnly(request.url());
        if (path == null || path.isBlank()) {
            return super.getContextualName(context);
        }
        HttpMethod method = request.method();
        String verb = method != null ? method.name().toLowerCase(Locale.ROOT) : "request";
        return "webclient " + verb + " " + path;
    }

    @Nullable
    private static String pathOnly(@Nullable URI uri) {
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        if (path != null && !path.isBlank()) {
            return path;
        }
        // p. ej. URI relativa sin authority
        String s = uri.toString();
        int q = s.indexOf('?');
        return q >= 0 ? s.substring(0, q) : s;
    }
}

package com.attendance.course;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AuthServiceClient {
    private final RestClient client;
    private final String internalKey;

    public AuthServiceClient(RestClient.Builder builder, @Value("${auth-service.base-url}") String baseUrl,
            @Value("${auth-service.internal-key}") String internalKey) {
        this.client = builder.baseUrl(baseUrl).build();
        this.internalKey = internalKey;
    }

    public EmployeeReference findEmployeeByEmail(String email) {
        try {
            return client.get().uri(uri -> uri.path("/internal/employees/by-email")
                            .queryParam("email", email).build())
                    .header("X-Internal-Key", internalKey)
                    .retrieve().body(EmployeeReference.class);
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Zaposleni nije pronađen.");
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Servis za autentifikaciju trenutno nije dostupan.");
        }
    }
}

package com.attendance.analytics;

import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CourseDataClient {
    private final RestClient client;

    public CourseDataClient(@Value("${course-service.base-url}") String baseUrl) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(3000);
        requests.setReadTimeout(5000);
        client = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    }

    public AnalyticsData.Source load(UUID offeringId, String bearerToken) {
        try {
            AnalyticsData.Source data = client.get()
                    .uri("/api/offerings/{id}/analytics-source", offeringId)
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve().body(AnalyticsData.Source.class);
            if (data == null || data.enrollments() == null || data.sessions() == null
                    || data.attendances() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Podaci predmeta nisu potpuni.");
            }
            return data;
        } catch (HttpClientErrorException error) {
            throw new ResponseStatusException(error.getStatusCode(), "Podaci predmeta nisu dostupni.");
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servis predmeta nije dostupan.");
        }
    }

    public void requireAccess(UUID offeringId, String bearerToken) {
        try {
            client.get().uri("/api/offerings/{id}/analytics-access", offeringId)
                    .headers(headers -> headers.setBearerAuth(bearerToken))
                    .retrieve().toBodilessEntity();
        } catch (HttpClientErrorException error) {
            throw new ResponseStatusException(error.getStatusCode(), "Podaci predmeta nisu dostupni.");
        } catch (RestClientException error) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Servis predmeta nije dostupan.");
        }
    }
}

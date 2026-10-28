package com.plotpack.parcel.client;

import com.plotpack.common.web.BusinessException;
import com.plotpack.common.web.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Synchronous call to user-service: the package cannot be created for a customer who doesn't exist. */
@Component
public class UserClient {

    public record CustomerView(Long id, String name, String phone) {
    }

    private final RestClient http;

    public UserClient(RestClient userRestClient) {
        this.http = userRestClient;
    }

    public CustomerView customer(Long customerId) {
        try {
            return http.get().uri("/api/users/{id}", customerId).retrieve().body(CustomerView.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "Customer " + customerId + " not found");
        } catch (RestClientException e) {
            throw new BusinessException(ErrorCode.DEPENDENCY_UNAVAILABLE, "user-service unavailable");
        }
    }
}

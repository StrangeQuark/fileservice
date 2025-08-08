// Integration file: Auth

package com.strangequark.fileservice.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
public class AuthUtility {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthUtility.class);

    @Value("${SERVICE_SECRET_FILE}")
    private String SERVICE_SECRET_FILE;

    public String authenticateServiceAccount() {
        try {
            LOGGER.info("Attempting to authenticate service account");

            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("clientId", "file");
            requestBody.put("clientPassword", SERVICE_SECRET_FILE);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestBody, headers);

            String response = new RestTemplate().postForObject(
                    "http://auth-service:6001/api/auth/service-account/authenticate",
                    requestEntity,
                    String.class
            );

            response = response.replace("\"", "");
            response = response.replace("}", "");

            LOGGER.info(response);
            LOGGER.info((response.indexOf("jwtToken:") + 9) + "");
            LOGGER.info(String.valueOf(response.contains("jwtToken")));

            if(!response.contains("jwtToken"))
                throw new RuntimeException("jwtToken not found in authentication response");

            LOGGER.info("Service account authentication success");
            return response.substring(response.indexOf("jwtToken:") + 9).trim();
        } catch (RestClientException ex) {
            LOGGER.error(ex.getMessage());
            return null;
        }
    }

    public String getUserId(String username) {
        try {
            LOGGER.info("Attempting to get user id");

            String accessToken = authenticateServiceAccount();

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(accessToken);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> responseEntity = new RestTemplate().exchange(
                    "http://auth-service:6001/api/auth/user/get-user-id?username=" + username,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            String response = responseEntity.getBody();

            if (response == null) {
                LOGGER.error("getUserId response is null");
                return null;
            }

            LOGGER.info("User id retrieval success");
            return response.replace("\"", "").trim();
        } catch (RestClientException ex) {
            LOGGER.error(ex.getMessage());
            return null;
        }
    }
}

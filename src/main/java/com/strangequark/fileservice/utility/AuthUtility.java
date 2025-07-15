// Integration file: Auth

package com.strangequark.fileservice.utility;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class AuthUtility {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthUtility.class);

    public String getUserId(String username) {
        try {
            LOGGER.info("Attempting to get user id");

            String response = new RestTemplate().getForObject("http://auth-service:6001/api/auth/user/get-user-id?username=" + username, String.class);
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

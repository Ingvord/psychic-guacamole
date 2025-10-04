package ru.ingvord.trading.repository;

import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;

import java.io.IOException;

public class SecretRepository {
    private final SsmClient ssmClient = SsmClient.builder().build();

    /**
     * Resolves a parameter value with optional SSM Parameter Store lookup.
     *
     * <p>Parameters prefixed with {@code "ssm:"} are fetched from AWS Systems Manager
     * Parameter Store (e.g., {@code "ssm:/telegram-bot/token"}). All other values
     * are returned as literal strings.
     *
     * <p>This allows environment-agnostic configuration:
     * <pre>{@code
     * // Production: BOT_TOKEN=ssm:/telegram-bot/token
     * // Local:      BOT_TOKEN=123456:ABCdef
     * String token = getParameter(System.getenv("BOT_TOKEN"));
     * }</pre>
     *
     * @param parameterName the parameter value or SSM path (with {@code "ssm:"} prefix)
     * @return the resolved parameter value
     * @throws IOException if SSM retrieval fails
     */
    public String getParameter(String parameterName) throws IOException {
        if(parameterName.startsWith("ssm:"))
            return getParameter(parameterName.substring(4),true);
        else
            return parameterName;
    }

    private String getParameter(String parameterName, boolean withDecryption) throws IOException {

        try {
            GetParameterRequest request = GetParameterRequest.builder()
                    .name(parameterName)
                    .withDecryption(withDecryption)
                    .build();

            GetParameterResponse response = ssmClient.getParameter(request);
            return response.parameter().value();

        } catch (Exception e) {
            throw new IOException("Failed to get parameter: " + parameterName, e);
        }
    }
}

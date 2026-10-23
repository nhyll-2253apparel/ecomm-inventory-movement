package com._apparel.ecomm_inventory_movement.service;

import com._apparel.ecomm_inventory_movement.controller.InventoryController.ManualAdjustmentRequest;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@Service
public class InventoryAdjustmentService {

    private static final Logger log = LoggerFactory.getLogger(InventoryAdjustmentService.class);

    private final RestClient restClient;
    private final String oauthUrl;
    private final String uploadUrl;
    private final String viewerUrl;
    private final String oauthUsername;
    private final String oauthPassword;

    public InventoryAdjustmentService(
            @Value("${inventory.adjustment.oauth-url}") String oauthUrl,
            @Value("${inventory.adjustment.upload-url}") String uploadUrl,
            @Value("${inventory.adjustment.viewer-url}") String viewerUrl,
            @Value("${inventory.adjustment.oauth-username:}") String oauthUsername,
            @Value("${inventory.adjustment.oauth-password:}") String oauthPassword) {
        this.restClient = RestClient.create();
        this.oauthUrl = oauthUrl;
        this.uploadUrl = uploadUrl;
        this.viewerUrl = viewerUrl;
        this.oauthUsername = oauthUsername;
        this.oauthPassword = oauthPassword;
    }

    public boolean adjust(ManualAdjustmentRequest request) {
        log.info("Starting manual inventory adjustment for warehouse {} with quantity {}",
                request.warehouse(), request.quantity());
        return adjust(request, retrieveAccessToken());
    }

    public BetaAdjustmentResult adjustBeta(String style, String color, int size) {
        log.info("Starting beta inventory movement for size {}", size);
        String accessToken;
        try {
            accessToken = retrieveAccessToken();
            if (!hasQualifiedStock(accessToken, "DC", style, color, size, "BETWEEN 1 AND 3")) {
                log.warn("Beta inventory movement stopped: no qualifying stock in DC warehouse for size {}", size);
                return new BetaAdjustmentResult(false, "Style has no qualified stocks in DC Warehouse");
            }
            if (!hasQualifiedStock(accessToken, "CA", style, color, size, ">= 10")) {
                log.warn("Beta inventory movement stopped: no qualifying stock in CA warehouse for size {}", size);
                return new BetaAdjustmentResult(false, "Style has no qualified stocks in CA Warehouse");
            }
        } catch (RestClientException | IllegalStateException exception) {
            log.error("Beta inventory stock validation failed for size {}", size, exception);
            throw exception;
        }

        try {
            if (!adjust(new ManualAdjustmentRequest("DC", style, color, String.valueOf(size), 5), accessToken)) {
                log.warn("Beta inventory adjustment was rejected by DC warehouse for size {}", size);
                return new BetaAdjustmentResult(false, "Failed adjustment in DC warehouse");
            }
        } catch (RestClientException exception) {
            log.error("Beta inventory adjustment API call failed for DC warehouse, size {}", size, exception);
            return new BetaAdjustmentResult(false, "Failed adjustment in DC warehouse");
        }

        try {
            if (!adjust(new ManualAdjustmentRequest("CA", style, color, String.valueOf(size), -5), accessToken)) {
                log.warn("Beta inventory adjustment was rejected by CA warehouse for size {}", size);
                return new BetaAdjustmentResult(false, "Failed adjustment in CA warehouse");
            }
        } catch (RestClientException exception) {
            log.error("Beta inventory adjustment API call failed for CA warehouse, size {}", size, exception);
            return new BetaAdjustmentResult(false, "Failed adjustment in CA warehouse");
        }
        log.info("Beta inventory movement completed successfully for size {}", size);
        return new BetaAdjustmentResult(true, "Successful inventory movement");
    }

    public ScheduledAdjustmentResult runScheduledAdjustment(
            int caMinThreshold,
            int caMaxThreshold,
            int dcMinThreshold,
            int dcMaxThreshold,
            int quantityToTransfer) {
        if (caMinThreshold > caMaxThreshold || dcMinThreshold > dcMaxThreshold) {
            throw new IllegalArgumentException("Minimum stock thresholds must not exceed maximum thresholds");
        }

        String accessToken = retrieveAccessToken();
        int movements = 0;
        int failures = 0;
        for (int size = 1; size <= 18; size++) {
            List<?> dcRows = findAvailableInventory(
                    accessToken, "DC", size, dcMinThreshold, dcMaxThreshold, null, null);
            for (Object row : dcRows) {
                if (!(row instanceof Map<?, ?> inventoryRow)
                        || inventoryRow.get("STYLE") == null
                        || inventoryRow.get("CLR") == null) {
                    log.error("Inventory viewer API returned a row without STYLE or CLR for DC warehouse, size {}",
                            size);
                    failures++;
                    continue;
                }

                String style = String.valueOf(inventoryRow.get("STYLE"));
                String color = String.valueOf(inventoryRow.get("CLR"));
                if (findAvailableInventory(
                        accessToken, "CA", size, caMinThreshold, caMaxThreshold, style, color).isEmpty()) {
                    continue;
                }

                try {
                    if (!adjust(new ManualAdjustmentRequest(
                            "DC", style, color, String.valueOf(size), quantityToTransfer), accessToken)) {
                        log.error("Failed adjustment in DC warehouse for style {}, color {}, size {}",
                                style, color, size);
                        failures++;
                        continue;
                    }
                } catch (RestClientException exception) {
                    log.error("Failed adjustment in DC warehouse for style {}, color {}, size {}",
                            style, color, size, exception);
                    failures++;
                    continue;
                }

                try {
                    if (!adjust(new ManualAdjustmentRequest(
                            "CA", style, color, String.valueOf(size), -quantityToTransfer), accessToken)) {
                        log.error("Failed adjustment in CA warehouse for style {}, color {}, size {}",
                                style, color, size);
                        failures++;
                        continue;
                    }
                } catch (RestClientException exception) {
                    log.error("Failed adjustment in CA warehouse for style {}, color {}, size {}",
                            style, color, size, exception);
                    failures++;
                    continue;
                }
                log.info("Successful inventory movement");
                movements++;
            }
        }
        return new ScheduledAdjustmentResult(failures == 0 && movements > 0, movements, failures);
    }

    private String retrieveAccessToken() {
        if (oauthUsername.isBlank() || oauthPassword.isBlank()) {
            log.error("Cannot request inventory OAuth token: credentials are not configured");
            throw new IllegalStateException("Inventory adjustment OAuth credentials are not configured");
        }

        MultiValueMap<String, String> tokenRequest = new LinkedMultiValueMap<>();
        tokenRequest.add("grant_type", "client_credentials");
        OAuthResponse tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(oauthUrl)
                    .headers(headers -> headers.setBasicAuth(oauthUsername, oauthPassword))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(tokenRequest)
                    .retrieve()
                    .body(OAuthResponse.class);
        } catch (RestClientException exception) {
            log.error("Inventory OAuth API call failed", exception);
            throw exception;
        }

        String accessToken = tokenResponse == null ? null : tokenResponse.accessToken();
        if (accessToken == null || accessToken.isBlank()) {
            log.error("Inventory OAuth API response did not contain an access token");
            throw new IllegalStateException("Inventory adjustment OAuth response did not contain an access token");
        }
        log.info("Inventory OAuth API call completed successfully");
        return accessToken;
    }

    private boolean adjust(ManualAdjustmentRequest request, String accessToken) {
        AdjustmentUpload upload = new AdjustmentUpload(List.of(new AdjustmentLine(
                request.warehouse(),
                request.style(),
                request.color(),
                request.size(),
                String.valueOf(request.quantity()),
                "000")));
        UploadResponse uploadResponse;
        try {
            uploadResponse = restClient.post()
                    .uri(uploadUrl)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(upload)
                    .retrieve()
                    .body(UploadResponse.class);
        } catch (RestClientException exception) {
            log.error("Inventory adjustment API call failed for warehouse {}", request.warehouse(), exception);
            throw exception;
        }

        boolean successful = uploadResponse != null && "Success".equals(uploadResponse.status());
        if (successful) {
            log.info("Inventory adjustment API call succeeded for warehouse {} with quantity {}",
                    request.warehouse(), request.quantity());
        } else {
            log.warn("Inventory adjustment API returned an unsuccessful response for warehouse {}",
                    request.warehouse());
        }
        return successful;
    }

    private boolean hasQualifiedStock(
            String accessToken,
            String warehouse,
            String style,
            String color,
            int size,
            String stockCondition) {
        String filter = "WH = '" + warehouse + "' AND STYLE = '" + escapeFilterValue(style)
                + "' AND CLR = '" + escapeFilterValue(color) + "' AND AVAIL_STOCK_SZ" + size + " " + stockCondition;
        ViewerRequest request = new ViewerRequest(
                "STYLE, CLR, AVAIL_STOCK_SZ" + size,
                filter,
                "STYLE, CLR, WH");

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(viewerUrl)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException exception) {
            log.error("Inventory viewer API call failed for warehouse {} and size {}", warehouse, size, exception);
            throw exception;
        }
        Object rows = response == null ? null : response.get("VR_INVENT_WH_Z");
        if (!(rows instanceof List<?> inventoryRows)) {
            log.error("Inventory viewer API returned an invalid VR_INVENT_WH_Z response for warehouse {} and size {}",
                    warehouse, size);
            throw new IllegalStateException("Inventory viewer response did not contain VR_INVENT_WH_Z rows");
        }
        log.info("Inventory viewer API call succeeded for warehouse {} and size {}; found {} rows",
                warehouse, size, inventoryRows.size());
        return !inventoryRows.isEmpty();
    }

    private List<?> findAvailableInventory(
            String accessToken,
            String warehouse,
            int size,
            int minimum,
            int maximum,
            String style,
            String color) {
        StringBuilder filter = new StringBuilder("WH = '").append(warehouse).append("' ");
        if (style != null && color != null) {
            filter.append("AND STYLE = '").append(escapeFilterValue(style))
                    .append("' AND CLR = '").append(escapeFilterValue(color)).append("' ");
        }
        filter.append("AND AVAIL_STOCK_SZ").append(size).append(" BETWEEN ")
                .append(minimum).append(" AND ").append(maximum);
        ViewerRequest request = new ViewerRequest(
                "STYLE, CLR, AVAIL_STOCK_SZ" + size,
                filter.toString(),
                "STYLE, CLR, WH");

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri(viewerUrl)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
        } catch (RestClientException exception) {
            log.error("Inventory viewer API call failed for warehouse {} and size {}", warehouse, size, exception);
            throw exception;
        }
        Object rows = response == null ? null : response.get("VR_INVENT_WH_Z");
        if (!(rows instanceof List<?> inventoryRows)) {
            log.error("Inventory viewer API returned an invalid VR_INVENT_WH_Z response for warehouse {} and size {}",
                    warehouse, size);
            throw new IllegalStateException("Inventory viewer response did not contain VR_INVENT_WH_Z rows");
        }
        log.info("Inventory viewer API call succeeded for warehouse {} and size {}; found {} rows",
                warehouse, size, inventoryRows.size());
        return inventoryRows;
    }

    private String escapeFilterValue(String value) {
        return value.replace("'", "''");
    }

    private record OAuthResponse(@JsonProperty("access_token") String accessToken) {
    }

    private record ViewerRequest(String COLUMNS, String FILTER, String SORT) {
    }

    private record UploadResponse(String status) {
    }

    public record BetaAdjustmentResult(boolean successful, String message) {
    }

    public record ScheduledAdjustmentResult(boolean successful, int movements, int failures) {
    }

    private record AdjustmentUpload(@JsonProperty("INV_ADJ") List<AdjustmentLine> adjustments) {
    }

    private record AdjustmentLine(
            @JsonProperty("WHOUSE") String warehouse,
            @JsonProperty("STYLE") String style,
            @JsonProperty("COLOR_NO") String color,
            @JsonProperty("SIZE_NAME") String size,
            @JsonProperty("ADJ_QTY") String quantity,
            @JsonProperty("BIN") String bin) {
    }
}

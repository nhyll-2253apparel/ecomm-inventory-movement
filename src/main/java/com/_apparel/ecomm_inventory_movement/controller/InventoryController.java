package com._apparel.ecomm_inventory_movement.controller;

import com._apparel.ecomm_inventory_movement.entity.InventoryLi;
import com._apparel.ecomm_inventory_movement.service.InventoryAdjustmentService;
import com._apparel.ecomm_inventory_movement.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService service;
    private final InventoryAdjustmentService adjustmentService;

    @GetMapping({"/", "/job-inventory-adjustment"})
    public String job_inventory_adjustment() {
        return "menu/job_inventory_adjustment";
    }

    @GetMapping({"/", "/plm-styles-integration"})
    public String plm_styles_integration(Model model) {
        model.addAttribute("inventory", service.getAllInventory());
        return "menu/plm_styles_integration";
    }

    @GetMapping({"/", "/inventory-movement"})
    public String inventory_movement(Model model) {
        model.addAttribute("inventory", service.getAllInventory());
        return "menu/inventory_movement";
    }

    @GetMapping("/inventory")
    @ResponseBody
    public List<InventoryLi> getAll() {
        return service.getAllInventory();
    }

    @GetMapping("/inventory/style/{style}")
    @ResponseBody
    public List<InventoryLi> getByStyle(
            @PathVariable String style) {

        return service.findByStyle(style);
    }

    @PostMapping("/inventory/adjustment/manual")
    @ResponseBody
    public ResponseEntity<Map<String, String>> adjustManually(
            @Valid @RequestBody ManualAdjustmentRequest request) {
        boolean successful = adjustmentService.adjust(request);
        return ResponseEntity.ok(Map.of("status", successful ? "Success" : "Failed"));
    }

    @PostMapping("/inventory/adjustment/beta")
    @ResponseBody
    public ResponseEntity<InventoryAdjustmentService.BetaAdjustmentResult> adjustBeta(
            @Valid @RequestBody BetaAdjustmentRequest request) {
        return ResponseEntity.ok(adjustmentService.adjustBeta(
                request.style(),
                request.color(),
                request.size()));
    }

    @PostMapping("/inventory/adjustment/scheduled")
    @ResponseBody
    public ResponseEntity<InventoryAdjustmentService.ScheduledAdjustmentResult> runScheduledAdjustment(
            @Valid @RequestBody ScheduledAdjustmentRequest request) {
        return ResponseEntity.ok(adjustmentService.runScheduledAdjustment(
                request.caMinThreshold(),
                request.caMaxThreshold(),
                request.dcMinThreshold(),
                request.dcMaxThreshold(),
                request.quantityToTransfer()));
    }

    public record ManualAdjustmentRequest(
            @NotBlank String warehouse,
            @NotBlank String style,
            @NotBlank String color,
            @NotBlank String size,
            @Positive int quantity) {
    }

    public record BetaAdjustmentRequest(
            @NotBlank String style,
            @NotBlank String color,
            @Positive int size) {
    }

    public record ScheduledAdjustmentRequest(
            @NotNull @Min(0) Integer caMinThreshold,
            @NotNull @Min(0) Integer caMaxThreshold,
            @NotNull @Min(0) Integer dcMinThreshold,
            @NotNull @Min(0) Integer dcMaxThreshold,
            @NotNull @Positive Integer quantityToTransfer) {

        @AssertTrue(message = "Minimum thresholds must not exceed maximum thresholds")
        public boolean hasOrderedThresholds() {
            return caMinThreshold == null || caMaxThreshold == null || dcMinThreshold == null || dcMaxThreshold == null
                    || (caMinThreshold <= caMaxThreshold && dcMinThreshold <= dcMaxThreshold);
        }
    }
}

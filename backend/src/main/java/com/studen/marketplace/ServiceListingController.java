package com.studen.marketplace;

import com.studen.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/users/me/services")
public class ServiceListingController {

    private final ServiceListingService serviceListingService;

    public ServiceListingController(ServiceListingService serviceListingService) {
        this.serviceListingService = serviceListingService;
    }

    @GetMapping
    public List<ServiceResponse> getMyServices(@AuthenticationPrincipal UserPrincipal principal) {
        return serviceListingService.listMyServices(principal.getId());
    }

    @PostMapping
    public ResponseEntity<ServiceResponse> createService(@AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ServiceRequest request) {
        ServiceResponse response = serviceListingService.createService(principal.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{serviceId}")
    public ServiceResponse updateService(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId, @Valid @RequestBody ServiceRequest request) {
        return serviceListingService.updateService(principal.getId(), serviceId, request);
    }

    @PostMapping("/{serviceId}/publish")
    public ServiceResponse publishService(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId) {
        return serviceListingService.publishService(principal.getId(), serviceId);
    }

    @PostMapping("/{serviceId}/deactivate")
    public ServiceResponse deactivateService(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId) {
        return serviceListingService.deactivateService(principal.getId(), serviceId);
    }

    @PostMapping("/{serviceId}/reactivate")
    public ServiceResponse reactivateService(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId) {
        return serviceListingService.reactivateService(principal.getId(), serviceId);
    }

    @PutMapping("/{serviceId}/availability")
    public ServiceResponse setAvailability(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId, @RequestBody UpdateServiceAvailabilityRequest request) {
        return serviceListingService.setAvailability(principal.getId(), serviceId, request.available());
    }

    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> deleteService(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId) {
        serviceListingService.deleteService(principal.getId(), serviceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{serviceId}/media")
    public ServiceMediaResponse uploadMedia(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId, @RequestParam("file") MultipartFile file) {
        return serviceListingService.uploadMedia(principal.getId(), serviceId, file);
    }

    @DeleteMapping("/{serviceId}/media/{mediaId}")
    public ServiceResponse removeMedia(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId, @PathVariable UUID mediaId) {
        return serviceListingService.removeMedia(principal.getId(), serviceId, mediaId);
    }

    @PutMapping("/{serviceId}/media/order")
    public ServiceResponse reorderMedia(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId, @Valid @RequestBody UpdateServiceMediaOrderRequest request) {
        return serviceListingService.reorderMedia(principal.getId(), serviceId, request);
    }

    @PutMapping("/{serviceId}/media/{mediaId}/cover")
    public ServiceResponse setCoverMedia(@AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID serviceId, @PathVariable UUID mediaId) {
        return serviceListingService.setCoverMedia(principal.getId(), serviceId, mediaId);
    }
}

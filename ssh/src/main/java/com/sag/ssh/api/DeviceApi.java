package com.sag.ssh.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.sag.ssh.ActionResult;
import com.sag.ssh.service.DeviceService;

@RestController
public class DeviceApi {
    @Autowired
    private DeviceService deviceService;

    @PreAuthorize("hasRole('ROLE_SSH_ADMIN')")
    @PostMapping(value = "/device", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ManagedObjectRepresentation> addDevice(@RequestBody CreateDevice createDevice) {
        return new ResponseEntity<>(deviceService.addDevice(createDevice), HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('ROLE_SSH_READ')")
    @GetMapping(value = "/device/{deviceId}/properties", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getProperties(@PathVariable String deviceId,
            @RequestParam Optional<List<String>> ids) {
        return new ResponseEntity<>(deviceService.getProperties(deviceId, ids), HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ROLE_SSH_READ')")
    @GetMapping(value = "/device/{deviceId}/properties/{propertyId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ActionResult> getProperty(@PathVariable String deviceId, @PathVariable String propertyId) {
        return new ResponseEntity<>(deviceService.getProperty(deviceId, propertyId), HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ROLE_SSH_ADMIN')")
    @PostMapping(value = "/device/{deviceId}/actions/{actionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ActionResult> executeAction(@PathVariable String deviceId, @PathVariable String actionId,
            @RequestBody Map<String, Object> parameters) {
        return new ResponseEntity<>(deviceService.executeAction(deviceId, actionId, parameters), HttpStatus.OK);
    }

    @PreAuthorize("hasRole('ROLE_SSH_ADMIN')")
    @DeleteMapping(value = "/device/{deviceId}")
    public ResponseEntity<String> deleteDevice(@PathVariable String deviceId) {
        deviceService.deleteDevice(deviceId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

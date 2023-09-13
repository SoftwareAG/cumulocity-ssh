package com.sag.ssh.api;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.sag.ssh.service.ProfileService;

import lombok.extern.slf4j.Slf4j;

@RestController
@Slf4j
public class ProfileApi {

    @Autowired
    private InventoryApi inventoryApi;

    @Autowired
    private ProfileService profileService;

    @PreAuthorize("hasRole('ROLE_SSH_ADMIN')")
    @PostMapping("/profile")
    public ResponseEntity<ManagedObjectRepresentation> createProfile(@RequestBody CreateProfile createProfile) {
        ManagedObjectRepresentation mor = new ManagedObjectRepresentation();
        mor.setName(createProfile.getName());
        mor.setType("SSHProfile");
        Profile profile = new Profile();
        profile.setCategories(getDefaultCategories());
        profile.setProperties(getDefaultProperties());
        profile.setActions(getDefaultActions());
        mor.set(profile);
        mor = inventoryApi.create(mor);
        profileService.addProfile(mor.getId().getValue(), profile);
        log.info("Created new profile {}", createProfile.getName());
        return new ResponseEntity<>(mor, HttpStatus.CREATED);
    }

    private List<Category> getDefaultCategories() {
        List<Category> categories = new ArrayList<>();

        categories.add(new Category().withName("general").withLabel("General"));
        categories.add(new Category().withName("network").withLabel("Network"));
        categories.add(new Category().withName("certificates").withLabel("Certificates"));
        categories.add(new Category().withName("Updates").withLabel("updates"));

        return categories;
    }

    private List<Property> getDefaultProperties() {
        List<Property> properties = new ArrayList<>();

        properties.add(new Property().withName("hostname").withLabel("Hostname").withScript("hostname").withStore(true)
                .withCanDelete(false).withCategories(List.of("general")));

        return properties;
    }

    private List<DeviceOperation> getDefaultActions() {
        List<DeviceOperation> operations = new ArrayList<>();

        operations.add(new DeviceOperation().withId("reboot").withName("Reboot").withScript("sudo reboot")
                .withCategories(List.of("general")));

        return operations;
    }

    @PreAuthorize("hasRole('ROLE_SSH_ADMIN')")
    @PutMapping("/profile/{profileId}")
    public ResponseEntity<ManagedObjectRepresentation> updateProfile(@RequestBody Profile updateProfile,
            @PathVariable String profileId) {
        ManagedObjectRepresentation profile = new ManagedObjectRepresentation();
        profile.setId(GId.asGId(profileId));
        profile.set(updateProfile);
        profile = inventoryApi.update(profile);
        profileService.addProfile(profileId, updateProfile);
        profileService.loadProfiles();
        return new ResponseEntity<>(profile, HttpStatus.CREATED);
    }

    @PreAuthorize("hasRole('ROLE_SSH_ADMIN')")
    @DeleteMapping("/profile/{profileId}")
    public ResponseEntity<Void> deleteProfile(@PathVariable String profileId) {
        inventoryApi.delete(GId.asGId(profileId));
        profileService.removeProfile(profileId);
        profileService.loadProfiles();
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}

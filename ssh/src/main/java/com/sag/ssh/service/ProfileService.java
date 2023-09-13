package com.sag.ssh.service;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.inventory.InventoryFilter;
import com.sag.ssh.api.Profile;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class ProfileService {
    @Autowired
    private InventoryApi inventoryApi;

    private Map<String, Map<String, Profile>> profiles = new HashMap<>();
    private Map<String, Map<String, String>> deviceProfiles = new HashMap<>();

    @Autowired
    private MicroserviceSubscriptionsService subscriptionService;

    public Profile getProfile(String profileId) {
        return profiles.get(subscriptionService.getTenant()).get(profileId);
    }

    public Profile getDeviceProfile(String deviceId) {
        return getProfile(deviceProfiles.computeIfAbsent(subscriptionService.getTenant(), t -> new HashMap<>())
                .computeIfAbsent(deviceId, d -> inventoryApi.get(GId.asGId(d)).getProperty("profile").toString()));
    }

    public void addProfile(String profileId, Profile profile) {
        String tenant = subscriptionService.getTenant();
        this.profiles.computeIfAbsent(tenant, t -> new HashMap<>()).put(profileId, profile);
    }

    public void removeProfile(String profileId) {
        this.profiles.get(subscriptionService.getTenant()).remove(profileId);
    }

    public void addDeviceProfile(GId deviceId, String profileId) {
        this.deviceProfiles.computeIfAbsent(subscriptionService.getTenant(), t -> new HashMap<>())
                .put(deviceId.getValue(), profileId);
    }

    public void removeDevice(String deviceId) {
        this.deviceProfiles.get(subscriptionService.getTenant()).remove(deviceId);
    }

    public void loadProfiles() {
        InventoryFilter filter = new InventoryFilter().byType("SSHProfile");
        inventoryApi.getManagedObjectsByFilter(filter).get().allPages().forEach(p -> {
            log.info("Found profile {}", p.getName());
            Profile profile = p.get(Profile.class);
            if (profile != null) {
                addProfile(p.getId().getValue(), profile);
            }
        });
    }
}

package com.sag.ssh.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.model.ID;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.rest.representation.identity.ExternalIDRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.rest.representation.tenant.OptionRepresentation;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.identity.IdentityApi;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.option.TenantOptionApi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.sag.ssh.ActionResult;
import com.sag.ssh.api.CreateDevice;
import com.sag.ssh.api.DeviceOperation;
import com.sag.ssh.api.Property;

import c8y.IsDevice;
import c8y.RequiredAvailability;
import c8y.SupportedOperations;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class DeviceService {

    @Autowired
    private InventoryApi inventoryApi;

    @Autowired
    private IdentityApi identityApi;

    @Autowired
    private AgentService agentService;

    @Autowired
    private TenantOptionApi tenantOptionApi;

    @Autowired
    private MicroserviceSubscriptionsService subscriptionsService;

    @Autowired
    private ProfileService profileService;

    @Autowired
    private CommandService commandService;

    private TemplateEngine templateEngine;

    public DeviceService() {
        this.templateEngine = new SpringTemplateEngine();
        StringTemplateResolver templateResolver = new StringTemplateResolver();
        templateResolver.setTemplateMode(TemplateMode.TEXT);
        templateEngine.setTemplateResolver(templateResolver);
        this.templateEngine.setTemplateResolver(templateResolver);
    }

    public ManagedObjectRepresentation addDevice(CreateDevice createDevice) {
        ManagedObjectRepresentation device = new ManagedObjectRepresentation();
        device.setName(createDevice.getDeviceName());
        device.set(createDevice.getConfiguration());
        device.set(new IsDevice());
        device.setProperty("profile", createDevice.getProfile());
        SupportedOperations supportedOperations = new SupportedOperations();
        supportedOperations.add("c8y_Command");
        supportedOperations.add("c8y_Restart");
        supportedOperations.add("c8y_SoftwareUpdate");
        device.set(supportedOperations);
        device.set(new RequiredAvailability(2));
        device = inventoryApi.create(device);
        agentService.addDevice(device);
        log.info("New child device created with Id {}", device.getId().getValue());
        inventoryApi.getManagedObjectApi(agentService.getAgent().getId()).addChildDevice(device.getId());
        OptionRepresentation or = new OptionRepresentation();
        or.setCategory(device.getId().getValue());
        or.setKey("credentials.pass");
        or.setValue(createDevice.getCredentials());
        tenantOptionApi.save(or);
        return device;
    }

    public Map<String, Object> getProperties(String deviceId, Optional<List<String>> ids) {
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, Object> result = new HashMap<>();
        final List<Property> properties = new ArrayList<>();
        if (ids.isPresent()) {
            ids.get().forEach(id -> {
                Property property = profileService.getDeviceProfile(deviceId).getProperty(id);
                if (property != null) {
                    properties.add(property);
                } else {
                    log.error("Property {} doesn't exist in profile {}", id, profileService.getDeviceProfile(deviceId));
                }
            });

        } else {
            properties.addAll(profileService.getDeviceProfile(deviceId).getProperties());
        }
        ManagedObjectRepresentation toUpdate = new ManagedObjectRepresentation();
        toUpdate.setId(GId.asGId(deviceId));
        properties.forEach(
                property -> {
                    ActionResult commandResult = commandService.run(property.getScript(), deviceId);
                    if (commandResult.getStatus() == 0) {
                        if (property.getJson()) {
                            try {
                                result.put(property.getName(), mapper.readTree(commandResult.getResult()));
                            } catch (JsonMappingException e) {
                                e.printStackTrace();
                            } catch (JsonProcessingException e) {
                                e.printStackTrace();
                            }
                        } else {
                            result.put(property.getName(), commandResult.getResult());
                        }
                        if (property.getStore()) {
                            if (property.getName().contains(".")) {
                                String[] path = property.getName().split("\\.");
                                Map<String, Object> map = new HashMap<>();
                                if (toUpdate.hasProperty(path[0])) {
                                    map = (Map<String, Object>) toUpdate.get(path[0]);
                                } else {
                                    toUpdate.setProperty(path[0], map);
                                }
                                for (int i = 1; i < path.length - 1; i++) {
                                    if (!map.containsKey(path[i])) {
                                        map.put(path[i], new HashMap<>());
                                    }
                                    map = (Map<String, Object>) map.get(path[i]);
                                }
                                map.put(path[path.length - 1], result.get(property.getName()));
                            } else {
                                if (property.getJson()) {
                                    JsonNode node = (JsonNode) result.get(property.getName());
                                    ObjectReader reader = mapper.readerFor(new TypeReference<List<LinkedHashMap>>() {
                                    });
                                    try {
                                        toUpdate.setProperty(property.getName(), reader.readValue(node));
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    toUpdate.setProperty(property.getName(), result.get(property.getName()));
                                }
                            }
                        }
                        if (property.getName().equals("serial")) {
                            String serial = result.get(property.getName()).toString();
                            ID id = new ID("c8y_Serial", serial);
                            ExternalIDRepresentation extId = null;
                            try {
                                extId = identityApi.getExternalId(id);
                            } catch (SDKException e) {
                                log.info("Serial Id {} will be set on device {}", serial, deviceId);
                            }
                            if (extId == null) {
                                extId = new ExternalIDRepresentation();
                                extId.setExternalId(serial);
                                extId.setType("c8y_Serial");
                                extId.setManagedObject(toUpdate);
                                identityApi.create(extId);
                            }
                        }
                    } else {
                        result.put(property.getName(), commandResult.getError());
                    }
                });
        inventoryApi.update(toUpdate);

        return result;
    }

    public ActionResult getProperty(String deviceId, String propertyId) {
        Property property = profileService.getDeviceProfile(deviceId).getProperty(propertyId);
        ActionResult result = commandService.run(property.getScript(), deviceId);
        if (property.getStore() && result.getStatus() == 0) {
            ManagedObjectRepresentation toUpdate = new ManagedObjectRepresentation();
            toUpdate.setId(GId.asGId(deviceId));
            toUpdate.setProperty(propertyId, result);
            inventoryApi.update(toUpdate);
        }

        return result;
    }

    public ActionResult executeAction(String deviceId, String actionId,
            Map<String, Object> parameters) {
        DeviceOperation action = profileService.getDeviceProfile(deviceId).getAction(actionId);
        ActionResult actionResult = new ActionResult();
        if (action != null) {
            final Context context = new Context();
            if (parameters != null) {
                context.setVariables(parameters);
            }
            context.setVariable("device", inventoryApi.get(GId.asGId(deviceId)));
            MicroserviceCredentials microserviceCredentials = subscriptionsService
                    .getCredentials(subscriptionsService.getTenant()).get();
            context.setVariable("user", microserviceCredentials.getTenant() + "/"
                    + microserviceCredentials.getUsername());
            context.setVariable("password", microserviceCredentials.getPassword());
            log.info(context.toString());
            String script = templateEngine.process(action.getScript(), context);
            log.info("Will execute script: {}", script);
            actionResult.addActionResult(commandService.run(script, deviceId));
        } else {
            actionResult = actionResult.withStatus(1).withError("Action " + actionId + " is not defined.");
        }
        return actionResult;
    }

    public void deleteDevice(String deviceId) {
        inventoryApi.delete(GId.asGId(deviceId));
        profileService.removeDevice(deviceId);
        agentService.removeDevice(deviceId);
    }

}

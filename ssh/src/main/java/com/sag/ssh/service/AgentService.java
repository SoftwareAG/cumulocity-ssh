package com.sag.ssh.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.cumulocity.microservice.context.ContextService;
import com.cumulocity.microservice.context.credentials.MicroserviceCredentials;
import com.cumulocity.microservice.subscription.model.MicroserviceSubscriptionAddedEvent;
import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.model.Agent;
import com.cumulocity.model.ID;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.model.measurement.MeasurementValue;
import com.cumulocity.rest.representation.identity.ExternalIDRepresentation;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.rest.representation.measurement.MeasurementRepresentation;
import com.cumulocity.rest.representation.operation.OperationRepresentation;
import com.cumulocity.sdk.client.SDKException;
import com.cumulocity.sdk.client.devicecontrol.DeviceControlApi;
import com.cumulocity.sdk.client.identity.IdentityApi;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.inventory.ManagedObjectReferenceCollection;
import com.cumulocity.sdk.client.measurement.MeasurementApi;
import com.cumulocity.sdk.client.notification.Subscription;
import com.cumulocity.sdk.client.notification.SubscriptionListener;
import com.sag.ssh.ActionResult;
import com.sag.ssh.api.Measurement;

import c8y.IsDevice;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class AgentService {

    @Autowired
    private MicroserviceSubscriptionsService subscriptionsService;

    @Autowired
    private CommandService commandService;

    @Autowired
    private DeviceControlApi deviceControlApi;

    @Autowired
    private IdentityApi identityApi;

    @Autowired
    private InventoryApi inventoryApi;

    @Autowired
    private MeasurementApi measurementApi;

    @Autowired
    private ContextService<MicroserviceCredentials> contextService;

    @Autowired
    private ProfileService profileService;

    public ManagedObjectRepresentation getAgent() {
        return agents.get(subscriptionsService.getTenant());
    }

    public static final String SSH_AGENT = "SSH_AGENT";

    private Map<String, ManagedObjectRepresentation> agents = new HashMap<>();

    private Map<String, List<String>> devices = new HashMap<>();

    @EventListener
    private void init(MicroserviceSubscriptionAddedEvent event) {
        ManagedObjectRepresentation agent = null;
        Optional<ExternalIDRepresentation> extId = findExternalId(SSH_AGENT, SSH_AGENT);
        if (!extId.isPresent()) {
            agent = new ManagedObjectRepresentation();
            agent.setType(SSH_AGENT);
            agent.setName(SSH_AGENT);
            agent.set(new Agent());
            agent.set(new IsDevice());
            agent = inventoryApi.create(agent);

            ExternalIDRepresentation externalId = new ExternalIDRepresentation();
            externalId.setExternalId(SSH_AGENT);
            externalId.setType(SSH_AGENT);
            externalId.setManagedObject(agent);
            identityApi.create(externalId);
        } else {
            agent = extId.get().getManagedObject();
        }
        try {
            log.info("Agent Id is {}", agent.getId().getValue());
            deviceControlApi.getNotificationsSubscriber().subscribe(agent.getId(),
                    new OperationDispatcherSubscriptionListener(subscriptionsService.getTenant()));
        } catch (Exception e) {
            log.error("Can't subscribe to operation", e);
        }
        agents.put(subscriptionsService.getTenant(), agent);
        profileService.loadProfiles();
        ManagedObjectReferenceCollection coll = inventoryApi.getManagedObjectApi(getAgent().getId())
                .getChildDevices();
        if (coll != null) {
            coll.get().allPages()
                    .forEach(d -> devices.computeIfAbsent(subscriptionsService.getTenant(), t -> new ArrayList<>())
                            .add(d.getManagedObject().getId().getValue()));
        }
    }

    public void addDevice(ManagedObjectRepresentation device) {
        devices.computeIfAbsent(subscriptionsService.getTenant(), t -> new ArrayList<>())
                .add(device.getId().getValue());
    }

    public void removeDevice(String deviceId) {
        devices.get(subscriptionsService.getTenant()).remove(deviceId);
    }

    public class OperationDispatcherSubscriptionListener
            implements SubscriptionListener<GId, OperationRepresentation> {

        public OperationDispatcherSubscriptionListener(String tenant) {
            this.tenant = tenant;
        }

        private String tenant;

        @Override
        public void onError(Subscription<GId> sub, Throwable e) {
            log.error("OperationDispatcher error!", e);
        }

        @Override
        public void onNotification(Subscription<GId> sub, OperationRepresentation operation) {
            try {
                subscriptionsService.runForTenant(tenant, () -> commandService.process(operation));
            } catch (SDKException e) {
                log.error("OperationDispatcher error!", e);
            }
        }
    }

    public Optional<ExternalIDRepresentation> findExternalId(String externalId, String type) {
        ID id = new ID();
        id.setType(type);
        id.setValue(externalId);
        ExternalIDRepresentation extId = null;
        try {
            extId = identityApi.getExternalId(id);
        } catch (SDKException e) {
            log.info("External ID {} with type {} not found", externalId, type);
        }
        return Optional.ofNullable(extId);
    }

    @Scheduled(fixedRate = 1, timeUnit = TimeUnit.MINUTES)
    private void acquireMetrics() {
        final DateTime time = DateTime.now();
        subscriptionsService.runForEachTenant(() -> {
            devices.getOrDefault(subscriptionsService.getTenant(), new ArrayList<>()).forEach(deviceId -> {
                profileService.getDeviceProfile(deviceId).getMeasurements().forEach(m -> sendMetric(deviceId, m, time));
            });
        });
    }

    @Async
    private void sendMetric(String deviceId, Measurement m,
            DateTime time) {
        if (m.getScript() != null && !m.getScript().isEmpty()) {
            ActionResult result = commandService.run(m.getScript(),
                    deviceId);
            if (result.getStatus() == 0) {
                sendMeasurement(deviceId, m.getName(), m.getSeries(), m.getUnit(), new BigDecimal(result.getResult()),
                        time);
            }
        }
    }

    private MicroserviceCredentials createContextWithoutApiKey(MicroserviceCredentials source) {
        return new MicroserviceCredentials(
                source.getTenant(),
                source.getUsername(),
                source.getPassword(),
                source.getOAuthAccessToken(),
                "NOT_EXISTING", // added to replace context, check:
                                // com.cumulocity.microservice.context.annotation.EnableContextSupportConfiguration.contextScopeConfigurer
                source.getTfaToken(),
                null);
    }

    private void sendMeasurement(String deviceId, String type, String series, String unit,
            BigDecimal value, DateTime time) {
        MeasurementRepresentation m = new MeasurementRepresentation();
        Map<String, MeasurementValue> measurementValueMap = new HashMap<>();

        MeasurementValue mv = new MeasurementValue();
        mv.setValue(value);
        mv.setUnit(unit);
        measurementValueMap.put(series, mv);
        m.set(measurementValueMap, type);
        m.setType(type);
        m.setDateTime(time);
        ManagedObjectRepresentation device = new ManagedObjectRepresentation();
        device.setId(GId.asGId(deviceId));
        m.setSource(device);
        MicroserviceCredentials noAppKeyContext = createContextWithoutApiKey(contextService.getContext());
        contextService.callWithinContext(noAppKeyContext, () -> measurementApi.create(m));
    }
}

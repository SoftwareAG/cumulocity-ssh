package com.sag.ssh.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.signature.BuiltinSignatures;
import org.apache.sshd.common.signature.Signature;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.cumulocity.microservice.subscription.service.MicroserviceSubscriptionsService;
import com.cumulocity.model.idtype.GId;
import com.cumulocity.model.operation.OperationStatus;
import com.cumulocity.model.option.OptionPK;
import com.cumulocity.rest.representation.inventory.ManagedObjectRepresentation;
import com.cumulocity.rest.representation.operation.OperationRepresentation;
import com.cumulocity.rest.representation.tenant.OptionRepresentation;
import com.cumulocity.sdk.client.devicecontrol.DeviceControlApi;
import com.cumulocity.sdk.client.inventory.BinariesApi;
import com.cumulocity.sdk.client.inventory.InventoryApi;
import com.cumulocity.sdk.client.option.TenantOptionApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sag.ssh.Action;
import com.sag.ssh.ActionResult;
import com.sag.ssh.api.Configuration;

import c8y.Command;
import c8y.Restart;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class CommandService {
    @Autowired
    private TenantOptionApi tenantOptionApi;

    @Autowired
    private InventoryApi inventoryApi;

    @Autowired
    private DeviceControlApi deviceControlApi;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private BinariesApi binariesApi;

    @Autowired
    private MicroserviceSubscriptionsService subscriptionsService;

    private Map<String, Map<String, Configuration>> configurations = new HashMap<>();

    private ActionResult softwareUpdate(List<Map<String, String>> softwareUpdates, GId deviceId) {
        final ActionResult currentActionResult = new ActionResult();
        ManagedObjectRepresentation device = inventoryApi.get(deviceId);
        final List<Map<String, String>> softwareList = new ArrayList<>();
        if (device.hasProperty("c8y_SoftwareList")) {
            softwareList.addAll((List<Map<String, String>>) device.getProperty("c8y_SoftwareList"));
        }
        softwareUpdates.forEach(softwareUpdate -> {
            if (softwareUpdate.get("action").equals("install")) {
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("url", softwareUpdate.get("url"));
                parameters.put("filename", softwareUpdate.get("name")
                        + "-"
                        + softwareUpdate.get("version"));
                ActionResult localResult = deviceService.executeAction(deviceId.getValue(),
                        "installSoftware", parameters);
                if (localResult.getStatus() == 0) {
                    softwareList.add(softwareUpdate);
                }
                currentActionResult.addActionResult(localResult);
            } else if (softwareUpdate.get("action").equals("delete")) {
                Map<String, Object> parameters = new HashMap<>();
                parameters.put("filename", softwareUpdate.get("name")
                        + "-"
                        + softwareUpdate.get("version"));
                ActionResult localResult = deviceService.executeAction(deviceId.getValue(),
                        "uninstallSoftware", parameters);
                if (localResult.getStatus() == 0) {
                    Object[] entryToDelete = new Object[1];
                    softwareList.forEach(entry -> {
                        if (entry.get("name").equals(softwareUpdate.get("name"))
                                && entry.get("version").equals(softwareUpdate.get("version"))) {
                            entryToDelete[0] = entry;
                        }
                    });
                    if (entryToDelete[0] != null) {
                        softwareList.remove(entryToDelete[0]);
                    }
                }
                currentActionResult.addActionResult(localResult);
            }
        });
        ManagedObjectRepresentation toUpdate = new ManagedObjectRepresentation();
        toUpdate.setId(device.getId());
        toUpdate.setProperty("c8y_SoftwareList", softwareList);
        inventoryApi.update(toUpdate);
        return currentActionResult;
    }

    private ActionResult configUpdate(Map<String, String> downloadConfigFile, GId deviceId) {
        ActionResult result;
        try {
            Map<String, Object> config = null;
            if (downloadConfigFile.get("url").contains("/inventory/binaries/")) {
                InputStream is = binariesApi.downloadFile(GId.asGId(downloadConfigFile.get("url").split("/")[5]));
                ObjectMapper objectMapper = new ObjectMapper();
                config = objectMapper.readValue(is, Map.class);
            } else {
                URL url = new URL(downloadConfigFile.get("url"));
                ObjectMapper objectMapper = new ObjectMapper();
                config = objectMapper.readValue(url, Map.class);
            }
            log.info(config.toString());
            Map<String, String> updatedConfig = new HashMap<>();
            if (downloadConfigFile.containsKey("name")) {
                updatedConfig.put("name", downloadConfigFile.get("name"));
            } else {
                updatedConfig.put("name", downloadConfigFile.get("type"));
            }
            updatedConfig.put("type", downloadConfigFile.get("type"));
            updatedConfig.put("url", downloadConfigFile.get("url"));
            updatedConfig.put("time", DateTime.now().toDateTimeISO().toString());
            ManagedObjectRepresentation mor = new ManagedObjectRepresentation();
            mor.setId(deviceId);
            mor.setProperty("c8y_Configuration_" + downloadConfigFile.get("type"), updatedConfig);
            inventoryApi.update(mor);
            result = deviceService.executeAction(deviceId.getValue(),
                    config.get("action").toString(), config);
        } catch (IOException e) {
            e.printStackTrace();
            result = new ActionResult(1, "", e.getMessage());
        }
        return result;
    }

    public void process(OperationRepresentation operation) {
        ActionResult result = new ActionResult(0, "", "");
        operation.setStatus(OperationStatus.EXECUTING.toString());
        deviceControlApi.update(operation);
        if (operation.get(Command.class) != null) {
            result = run(operation.get(Command.class).getText(), operation.getDeviceId().getValue());
        } else if (operation.get(Restart.class) != null) {
            result = deviceService.executeAction(operation.getDeviceId().getValue(), "reboot", null);
        } else if (operation.hasProperty("c8y_SoftwareUpdate")) {
            List<Map<String, String>> softwareUpdates = (List<Map<String, String>>) operation
                    .getProperty("c8y_SoftwareUpdate");
            result = softwareUpdate(softwareUpdates, operation.getDeviceId());
        } else if (operation.get(Action.class) != null) {
            Action action = operation.get(Action.class);
            result = deviceService.executeAction(operation.getDeviceId().getValue(),
                    action.getActionId(),
                    action.getParameters());
        } else if (operation.hasProperty("c8y_DownloadConfigFile")) {
            Map<String, String> downloadConfigFile = (Map<String, String>) operation
                    .getProperty("c8y_DownloadConfigFile");
            result = configUpdate(downloadConfigFile, operation.getDeviceId());
        } else if (operation.hasProperty("c8y_DeviceProfile")) {
            Map<String, Object> deviceProfile = (Map<String, Object>) operation
                    .getProperty("c8y_DeviceProfile");
            Map<String, Object> updatedDeviceProfile = new HashMap<>();
            updatedDeviceProfile.put("profileName", operation.getProperty("profileName"));
            updatedDeviceProfile.put("profileId", operation.getProperty("profileId"));
            updatedDeviceProfile.put("profileExecuted", false);
            ManagedObjectRepresentation toUpdate = new ManagedObjectRepresentation();
            toUpdate.setId(operation.getDeviceId());
            toUpdate.setProperty("c8y_Profile", updatedDeviceProfile);
            inventoryApi.update(toUpdate);
            final ActionResult currentActionResult = new ActionResult();
            if (deviceProfile.containsKey("software")) {
                currentActionResult
                        .addActionResult(softwareUpdate((List<Map<String, String>>) deviceProfile.get("software"),
                                operation.getDeviceId()));
            }
            if (deviceProfile.containsKey("configuration")) {
                ((List<Map<String, String>>) deviceProfile.get("configuration")).forEach(c -> {
                    currentActionResult.addActionResult(configUpdate(c, operation.getDeviceId()));
                });
            }
            if (currentActionResult.getStatus() == 0) {
                updatedDeviceProfile.put("profileExecuted", true);
                toUpdate.setId(operation.getDeviceId());
                toUpdate.setProperty("c8y_Profile", updatedDeviceProfile);
                inventoryApi.update(toUpdate);
            }
            result = currentActionResult;
        } else {
            result = new ActionResult(1, "", "Operation not supported");
        }
        operation.setProperty("result", result.getResult());
        if (result.getStatus() == 0) {
            operation.setStatus(OperationStatus.SUCCESSFUL.toString());
        } else {
            operation.setStatus(OperationStatus.FAILED.toString());
            operation.setFailureReason(result.getError());
        }
        deviceControlApi.update(operation);
    }

    public Configuration getConfiguration(String deviceId) {
        return configurations.computeIfAbsent(subscriptionsService.getTenant(), t -> new HashMap<>())
                .computeIfAbsent(deviceId, d -> inventoryApi.get(GId.asGId(deviceId)).get(Configuration.class));
    }

    private String getCredentials(String deviceId) {
        OptionRepresentation or = tenantOptionApi
                .getOption(new OptionPK(deviceId, "credentials.pass"));
        return or.getValue();
    }

    public ActionResult run(String cmd, String deviceId) {
        final ActionResult actionResult = new ActionResult();

        Configuration configuration = getConfiguration(deviceId);

        SshClient client = SshClient.setUpDefaultClient();
        // add DSS
        List<NamedFactory<Signature>> signatureFactories = client.getSignatureFactories();
        List<BuiltinSignatures> signatures = new ArrayList<>();
        signatures.add(BuiltinSignatures.dsa);
        signatureFactories.addAll(NamedFactory.setUpBuiltinFactories(false, signatures));
        client.setSignatureFactories(signatureFactories);

        client.start();

        try (ClientSession session = client
                .connect(configuration.getUsername(), configuration.getHost(), configuration.getPort())
                .verify(configuration.getDefaultTimeoutSeconds(), TimeUnit.SECONDS).getSession()) {
            if (configuration.isPasswordProtected()) {
                String credentials = getCredentials(deviceId);
                String trimmedCredentials = credentials.trim();
                if (!credentials.equals(trimmedCredentials)) {
                    log.warn("Password includes blank characters and will be trimmed.");
                }
                session.addPasswordIdentity(trimmedCredentials);
            } else {
                KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();
                Collection<KeyPair> keyPairCollection = loader.loadKeyPairs(null, null, null, getCredentials(deviceId));
                session.addPublicKeyIdentity(keyPairCollection.iterator().next());
            }
            session.auth().verify(configuration.getDefaultTimeoutSeconds(), TimeUnit.SECONDS);

            try (ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
                    ByteArrayOutputStream errorStream = new ByteArrayOutputStream();
                    ClientChannel channel = session.createExecChannel(cmd)) {
                channel.setOut(responseStream);
                channel.setErr(errorStream);
                try {
                    channel.open().verify(configuration.getDefaultTimeoutSeconds(), TimeUnit.SECONDS);
                    channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED),
                            TimeUnit.SECONDS.toMillis(configuration.getDefaultTimeoutSeconds()));
                    actionResult.setResult(new String(responseStream.toByteArray()).trim());
                    actionResult.setError(new String(errorStream.toByteArray()));
                    if (channel.getExitStatus() != null) {
                        actionResult.setStatus(channel.getExitStatus());
                    } else {
                        actionResult.setStatus(0);
                    }
                } finally {
                    channel.close(false);
                }
            }
        } catch (Exception e) {
            log.error("Unable to run command {} on device {}", cmd, deviceId);
            e.printStackTrace();
            actionResult.setStatus(1);
            actionResult.setError(e.getMessage());
        } finally {
            client.stop();
        }
        return actionResult;
    }
}

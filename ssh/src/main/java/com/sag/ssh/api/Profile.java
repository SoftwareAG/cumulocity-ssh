package com.sag.ssh.api;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.svenson.JSONProperty;
import org.svenson.JSONTypeHint;

public class Profile {
    private List<Category> categories;
    private List<Property> properties;
    private List<Measurement> measurements;
    private List<DeviceOperation> actions;

    private Map<String, Property> propertyMap = new HashMap<>();
    private Map<String, Category> categoryMap = new HashMap<>();
    private Map<String, DeviceOperation> actionMap = new HashMap<>();

    @JSONTypeHint(Category.class)
    public List<Category> getCategories() {
        return categories;
    }

    public void setCategories(List<Category> categories) {
        this.categories = categories;
        categories.forEach(p -> categoryMap.put(p.getName(), p));
    }

    @JSONProperty(ignore = true)
    public Category getCategory(String categoryName) {
        return categoryMap.get(categoryName);
    }

    @JSONTypeHint(Property.class)
    public List<Property> getProperties() {
        return properties;
    }

    public void setProperties(List<Property> properties) {
        this.properties = properties;
        properties.forEach(p -> propertyMap.put(p.getName(), p));
    }

    @JSONProperty(ignore = true)
    public Property getProperty(String propertyName) {
        return propertyMap.get(propertyName);
    }

    @JSONTypeHint(Measurement.class)
    public List<Measurement> getMeasurements() {
        return measurements;
    }

    public void setMeasurements(List<Measurement> measurements) {
        this.measurements = measurements;
    }

    @JSONTypeHint(DeviceOperation.class)
    public List<DeviceOperation> getActions() {
        return actions;
    }

    public void setActions(List<DeviceOperation> actions) {
        this.actions = actions;
        actions.forEach(a -> actionMap.put(a.getId(), a));
    }

    @JSONProperty(ignore = true)
    public DeviceOperation getAction(String actionId) {
        return actionMap.get(actionId);
    }
}

package com.sag.ssh.api;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@NoArgsConstructor
@AllArgsConstructor
@With
public class DeviceOperationElement {
    private String id;
    private String name;
    private ParamType type;
    private List<String> values;
    private Boolean repeatable = false;
    private Integer minOccur = 1;
    private Integer maxOccur = 1;
    private Boolean dependsOnParam = false;
    private String dependsOnParamId;
    private String dependsOnParamValue;
    private List<DeviceOperationElement> elements = new ArrayList<>();

    public enum ParamType {
        STRING, INTEGER, FLOAT, BOOL, DATE, ENUM, GROUP, FILE;
    }
}

package com.sag.ssh.api;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@NoArgsConstructor
@AllArgsConstructor
@With
public class UpdateProfile {
    private List<Property> properties;
    private List<Measurement> measurements;
    private List<DeviceOperation> actions;
}

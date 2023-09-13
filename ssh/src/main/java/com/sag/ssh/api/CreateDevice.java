package com.sag.ssh.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@NoArgsConstructor
@AllArgsConstructor
@With
public class CreateDevice {
    private Configuration configuration;
    private String deviceName;
    private String credentials;
    private String profile;
}

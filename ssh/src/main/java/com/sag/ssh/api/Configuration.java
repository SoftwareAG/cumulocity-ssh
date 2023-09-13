package com.sag.ssh.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@NoArgsConstructor
@AllArgsConstructor
@With
public class Configuration {
    private long defaultTimeoutSeconds;
    private String username;
    private boolean passwordProtected;
    private String host;
    private Integer port;
}
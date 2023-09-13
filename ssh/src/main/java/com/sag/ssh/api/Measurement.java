package com.sag.ssh.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@NoArgsConstructor
@AllArgsConstructor
@With
public class Measurement {
    private String name;
    private String series;
    private String unit;
    private String script;
}

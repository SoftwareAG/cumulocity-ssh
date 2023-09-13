package com.sag.ssh;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@NoArgsConstructor
@AllArgsConstructor
@With
public class Action {
    private String actionId;
    private Map<String, Object> parameters;
}

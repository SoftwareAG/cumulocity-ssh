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
public class Property {
    private String name;
    private String label;
    private List<String> categories;
    private String description;
    private String script;
    private Boolean store = true;
    private Boolean canDelete = false;
    private Boolean json = false;
}

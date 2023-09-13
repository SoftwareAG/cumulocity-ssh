package com.sag.ssh.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.With;

@Data
@NoArgsConstructor
@AllArgsConstructor
@With
public class Category {
    private String name;
    private String label;
    private String description;
    private Boolean canDelete = false;
}

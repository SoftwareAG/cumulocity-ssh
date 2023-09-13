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
public class DeviceOperation {
	private String id;
	private String name;
	private List<String> categories;
	private Boolean canDelete = false;
	private boolean tableRowAction;
	private boolean confirmation;
	private String tableName;
	private String icon;
	private String script;
	private String property;
	private List<DeviceOperationElement> elements = new ArrayList<>();
}

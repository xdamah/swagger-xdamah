package com.github.xdamah.config;

import java.util.Map;

public interface IOpenApiValidationConfigOnInitWorkaround {
	public void onInitInOpenApiValidationConfig();

	public Map<String, String> getCustomSchemaImportMapping();
}

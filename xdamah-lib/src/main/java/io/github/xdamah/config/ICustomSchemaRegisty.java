package io.github.xdamah.config;

import java.util.Map;

import io.swagger.v3.oas.models.OpenAPI;

public interface ICustomSchemaRegisty {
	public void onInitRegisterCustomSchemas();

	public Map<String, String> getCustomSchemaImportMapping();
	public void setOpenApi(OpenAPI openApi);
}

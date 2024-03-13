package io.github.xdamah.config;

import java.util.Map;

public interface ICustomSchemaRegisty {
	public void onInitRegisterCustomSchemas();

	public Map<String, String> getCustomSchemaImportMapping();
}

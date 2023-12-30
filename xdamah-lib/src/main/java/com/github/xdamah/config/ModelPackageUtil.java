package com.github.xdamah.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.github.xdamah.constants.Constants;

@Configuration
public class ModelPackageUtil {
	@Value("${xdamah.codegen.model.package}")
	private String modelPackage;
	
	public String fqn(String simpleClassname)
	{
		return modelPackage+"."+simpleClassname;
	}
	
	public String simpleClassNameFromComponentSchemaRef(String ref)
	{
		return ref.substring(Constants.COMPONENTS_SCHEMA_PREFIX_LENGTH);
	}

}

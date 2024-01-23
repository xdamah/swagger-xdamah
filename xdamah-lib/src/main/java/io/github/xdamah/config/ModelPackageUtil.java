package io.github.xdamah.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.github.xdamah.constants.Constants;

@Configuration
public class ModelPackageUtil {
	//if null implies we are using fqn
	@Value("${xdamah.codegen.model.package:#{null}}")
	private String modelPackage;

	public String fqn(String simpleClassname) {
		return modelPackage==null? simpleClassname:modelPackage+ "." + simpleClassname;
	}

	public String simpleClassNameFromComponentSchemaRef(String ref) {
		return ref.substring(Constants.COMPONENTS_SCHEMA_PREFIX_LENGTH);
	}

}

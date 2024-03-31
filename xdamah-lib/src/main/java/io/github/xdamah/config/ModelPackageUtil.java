package io.github.xdamah.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.github.xdamah.constants.Constants;

@Configuration
public class ModelPackageUtil {
	//if null implies we are using fqn
	@Value("${xdamah.codegen.model.package:#{null}}")
	private String modelPackage;

	public String fqn(String classnameIfUnderFqnElseSimpleClassName) {
		return modelPackage==null? classnameIfUnderFqnElseSimpleClassName:modelPackage+ "." + classnameIfUnderFqnElseSimpleClassName;
	}
	
	public String fqnToSimpleClassName(String classnameIfUnderFqnElseSimpleClassName) {
		String ret=classnameIfUnderFqnElseSimpleClassName;
		int lastIndex=classnameIfUnderFqnElseSimpleClassName.lastIndexOf('.');
		if(lastIndex!=-1)
		{
			ret=classnameIfUnderFqnElseSimpleClassName.substring(lastIndex+1);
		}
		
		return ret;
	}

	public String classnameIfUnderFqnElseSimpleClassNameFromComponentSchemaRef(String ref) {
		return ref.substring(Constants.COMPONENTS_SCHEMA_PREFIX_LENGTH);
	}
	
	public boolean isForFqn()
	{
		return (modelPackage==null);
	}

}

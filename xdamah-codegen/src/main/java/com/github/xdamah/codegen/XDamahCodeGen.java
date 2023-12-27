package com.github.xdamah.codegen;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import io.swagger.codegen.v3.CodegenConstants;
import io.swagger.codegen.v3.CodegenOperation;
import io.swagger.codegen.v3.generators.java.SpringCodegen;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;

public class XDamahCodeGen extends SpringCodegen {

public XDamahCodeGen() {
		super();
		 //additionalProperties.remove("jackson");
	}

	//CodegenOperation codegenOperation = config.fromOperation(resourcePath, 
//	httpMethod, operation, schemas, openAPI);
	@Override
	public CodegenOperation fromOperation(String path, String httpMethod, 
			Operation operation,
			Map<String, Schema> schemas, OpenAPI openAPI) {

		CodegenOperation fromOperation = super.fromOperation(path, httpMethod, operation, schemas, openAPI);
		return fromOperation;
	}

	@Override
	public void processOpenAPI(OpenAPI openAPI) {
		
		super.processOpenAPI(openAPI);
	}

	@Override
	public void processOpts() {
		
		super.processOpts();
		
		System.out.println("this.useBeanValidation="+this.useBeanValidation);
		System.out.println("from.additionalProperties="+describeModels(additionalProperties));
	}
	
	protected String describeModels(Map<String, Object> models) {
    	String ret="";
    	ret+= dscribeInternal(models, "useBeanValidation");
    	ret+= dscribeInternal(models, "jakarta");
		return ret;
	}

	private String dscribeInternal(Map<String, Object> models, String key) {
		String ret="";
		Object object = models.get(key);
	
		if(object!=null)
		{
			ret+=">"+key+".class=";
			ret+=object.getClass().getName();
			if(object instanceof Boolean)
			{
				Boolean booleanObj=(Boolean) object;
				ret+=",booleanValue="+booleanObj.booleanValue();
			}
			else
			{
				
				ret+=",toString="+object.toString();
			}
		}
		return ret;
	}


	
	

}

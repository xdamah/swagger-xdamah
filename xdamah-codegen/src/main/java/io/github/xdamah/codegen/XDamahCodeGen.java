package io.github.xdamah.codegen;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.codegen.v3.CliOption;
import io.swagger.codegen.v3.CodegenConstants;
import io.swagger.codegen.v3.CodegenOperation;
import io.swagger.codegen.v3.generators.java.SpringCodegen;
import io.swagger.codegen.v3.templates.MustacheTemplateEngine;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Schema;

public class XDamahCodeGen extends SpringCodegen {
	protected boolean fqn = false;
	public boolean isFqn() {
		return fqn;
	}

	public void setFqn(boolean fqn) {
	
		this.fqn = fqn;
	}

	private static final Logger logger = LoggerFactory.getLogger(XDamahCodeGen.class);
public XDamahCodeGen() {
	
		super();
		 //additionalProperties.remove("jackson");
		 cliOptions.add(new CliOption("fqn", "FQNs for models"));
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
		logger.debug("this.fqn="+this.fqn);
		if(this.fqn)
		{
			this.modelPackage=null;
			logger.info("Since fqn is true setting modelPackage to null");
		}
		logger.debug("this.useBeanValidation="+this.useBeanValidation);
		logger.debug("from.additionalProperties="+describeModels(additionalProperties));
	}
	
	
	
	@Override
	public String modelFileFolder() {
		return fqn?outputFolder + "/" + sourceFolder + "/" :super.modelFileFolder();
	}

	@Override
	public String toModelFilename(String name) {
		return fqn?name.replace('.', '/'):super.toModelFilename(name);
	}

	@Override
	public String toModelName(String name) {
		
		return fqn?name:super.toModelName(name);
	}
	
	@Override
	protected void setTemplateEngine() {
		if(fqn)
		{
			 String templateEngineKey = additionalProperties.get(CodegenConstants.TEMPLATE_ENGINE) != null ? additionalProperties.get(CodegenConstants.TEMPLATE_ENGINE).toString() : null;

		        if (templateEngineKey == null) {
		            templateEngine = new XdamahFqnHandlebarTemplateEngine(this);
		        } else {
		            if (CodegenConstants.HANDLEBARS_TEMPLATE_ENGINE.equalsIgnoreCase(templateEngineKey)) {
		                templateEngine = new XdamahFqnHandlebarTemplateEngine(this);
		            } else {
		                templateEngine = new MustacheTemplateEngine(this);
		            }
		        }
		}
		else
		{
			super.setTemplateEngine();
		}
	}

	@Override
	public Map<String, Object> postProcessAllModels(Map<String, Object> objs) {
		objs=super.postProcessAllModels(objs);
		if(fqn)
		{
			Set<String> keySet = objs.keySet();
			for (String key : keySet) {
			
				Object object = objs.get(key);
				
				if(object!=null)
				{
					
					if(object instanceof Map)
					{
						
						Map map=(Map) object;
						String classname = (String) map.get("classname");
						System.out.println("got classname="+classname);
						String[] split = key.split(Pattern.quote("."));
						StringBuilder pacageName=new StringBuilder();
						for (int i = 0; i < split.length-1; i++) {
							String string = split[i];
							pacageName.append(string);
							if(i!=split.length-2)
							{
								pacageName.append(".");
							}
						}
						String p = pacageName.toString();
					
						map.put("modelPackage", p);
						map.put("package", p);
						//map.put("classname", toSimpleName(classname));
						map.put("sclassname", toSimpleName(classname));
					
							
						Set keySet2 = map.keySet();
						for (Object key2 : keySet2) {
							Object object2 = map.get(key2);
							String str=null;
							if(object2!=null)
							{
								str=object2.toString();
								if(str.length()>50)
								{
									str=str.substring(0, 50);
								}
							}
							System.out.println(key2+"="+str);
						}
					}
					else
					{
						logger.warn("unexpected got key="+key+", valtype="+object.getClass().getName());
					}
				}
				
				
			}
		}
	
		return objs;
	}
	
	private String toSimpleName(String classname) {
		String ret=null;
		int lastindex=classname.lastIndexOf('.');
		if(lastindex!=-1)
		{
			ret=classname.substring(lastindex+1);
		}
		else
		{
			ret=classname;
		}
		
		return ret;
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

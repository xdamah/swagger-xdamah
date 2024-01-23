package io.github.xdamah.codegen;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;

import io.github.xdamah.constants.DamahExtns;
import io.swagger.codegen.v3.CodegenConfig;
import io.swagger.codegen.v3.CodegenConstants;
import io.swagger.codegen.v3.CodegenOperation;
import io.swagger.codegen.v3.DefaultGenerator;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;

public class XDamahGenerator extends DefaultGenerator {
	private static final Logger logger = LoggerFactory.getLogger(XDamahGenerator.class);

	@Override
	 public Map<String, List<CodegenOperation>> processPaths(Paths paths) {
	
        Map<String, List<CodegenOperation>> ops = new TreeMap<>();
        for (String resourcePath : paths.keySet()) {
            PathItem path = paths.get(resourcePath);
            processOperation(resourcePath, "get", consider(path.getGet()), ops, path);
            processOperation(resourcePath, "head", consider(path.getHead()), ops, path);
            processOperation(resourcePath, "put", consider(path.getPut()), ops, path);
            processOperation(resourcePath, "post", consider(path.getPost()), ops, path);
            processOperation(resourcePath, "delete", consider(path.getDelete()), ops, path);
            processOperation(resourcePath, "patch", consider(path.getPatch()), ops, path);
            processOperation(resourcePath, "options", consider(path.getOptions()), ops, path);
        }
        return ops;
    }
	
	@Override
	public List<File> generate() {
		paramsToTypes(this::createNewTypesForParamsIfNeeded);
		paramsToTypes(validateRefTypesForParamsIfNeeded);
		paramsToTypes(enforceRequestBodyFormsAreNotInline);
		
		
		removeCustomTypeWorkAroundFromGeneratedModelBeforeGeneration();
		 List<File> generated = super.generate();

	
		return generated;
	}

	private Set<String> removeCustomTypeWorkAroundFromGeneratedModelBeforeGeneration() {
		CodegenConfig optsConfig = this.opts.getConfig();
		boolean isFqn=false;
		if(optsConfig instanceof XDamahCodeGen)
		{
			XDamahCodeGen xDamahCodeGen=(XDamahCodeGen) optsConfig;
			isFqn=xDamahCodeGen.fqn;
			
		}
		
		
		Set<String> result = new HashSet<>();
		 result.addAll(this.openAPI.getComponents().getSchemas().keySet());
		 if(isFqn)
		 {
			 result.retainAll( this.opts.getConfig().importMapping().values());
		 }
		 else
		 {
			 result.retainAll(optsConfig.typeMapping().keySet());
			 result.retainAll( optsConfig.importMapping().keySet());
		 }
		 
		for (String string : result) {
			this.openAPI.getComponents().getSchemas().remove(string);
		}
		return result;
	}

	
	private void paramsToTypes(BiConsumer<PathItem, String> biConsumer) {
		if(this.openAPI!=null)
		{
			Paths paths = this.openAPI.getPaths();
			if(paths!=null)
			{
				Set<String> pathsKeySet = paths.keySet();
				for (String path : pathsKeySet) {
					PathItem pathItem = paths.get(path);
					biConsumer.accept(pathItem, path);
					
				}
			}
			
			Map<String, PathItem> webhooks = this.openAPI.getWebhooks();
			if(webhooks!=null)
			{
				Set<String> webHooksKeySet = webhooks.keySet();
				for (String path : webHooksKeySet) {
					PathItem pathItem = webhooks.get(path);
					biConsumer.accept(pathItem, path);
				}
			}
		}
		else
		{
			throw new RuntimeException("Is the openApi specs file valid");
		}
		
	}
	
	/*private void paramsToTypesOld() {
		Paths paths = this.openAPI.getPaths();
		if(paths!=null)
		{
			Set<String> pathsKeySet = paths.keySet();
			for (String path : pathsKeySet) {
				PathItem pathItem = paths.get(path);
				createNewTypesForParamsIfNeeded(pathItem, path );
			}
		}
		
		Map<String, PathItem> webhooks = this.openAPI.getWebhooks();
		if(webhooks!=null)
		{
			Set<String> webHooksKeySet = webhooks.keySet();
			for (String path : webHooksKeySet) {
				PathItem pathItem = webhooks.get(path);
				createNewTypesForParamsIfNeeded(pathItem, path);
			}
		}
	}*/
	private Map<String, List<Parameter>> xDamahParamMap= new HashMap<>();
	private  void createNewTypesForParamsIfNeeded  (PathItem pathItem, String path){
		
		//do we need this parameters
		//how to check
		List<Parameter> parameters = pathItem.getParameters();
		Map<HttpMethod, Operation> readOperationsMap = pathItem.readOperationsMap();
		Set<HttpMethod> methodKeySet = readOperationsMap.keySet();
		for (HttpMethod method : methodKeySet) {
			Operation operation = readOperationsMap.get(method);
			String operationId = operation.getOperationId();
			List<Parameter> parameters2 = operation.getParameters();
			if(parameters2!=null && parameters2.size()>1)//only then we create wrappers
			{
				Map<String, Object> extensions = operation.getExtensions();
				Object xDamahParamRef = extensions.get(DamahExtns.X_DAMAH_PARAM_REF);//just the type name
				if(xDamahParamRef!=null && xDamahParamRef instanceof String)
				{
					//since this is to be a ref we dont need to create
					//it will only be in schemas/
					
				}
				else
				{
					//what to name as type
					//if methodKeyset size is 1 need not use method name in typeName
					//else typeName can be methodName+path/operationId+"Param"
					//build the type looping through the params and add to schema
					ObjectSchema objectSchema = new ObjectSchema();
					objectSchema.addExtension(DamahExtns.X_DAMAH_CREATED, true);
					String use=null;
					
					boolean nameSpecified=false;
					if(extensions!=null)
					{
						
						Object xDamahParamType = extensions.get(DamahExtns.X_DAMAH_PARAM_TYPE);
						if(xDamahParamType!=null && xDamahParamType instanceof String)
						{
							use=(String) xDamahParamType;
							nameSpecified=true;
							xDamahParamMap.put(use, parameters2);
							
						}
						
					}
					if(use==null && operationId!=null)
					{
						String  operationIdTrimmed=operationId.trim();
						int operationIdTrimmedLength = operationIdTrimmed.length();
						if(operationIdTrimmedLength>0)
						{
							use=String.valueOf(Character.toUpperCase(operationIdTrimmed.charAt(0)));
							if(operationIdTrimmedLength>1)
							{
								use+=operationIdTrimmed.substring(1);
							}
						}
						
					}
					if(use==null)
					{
						use=pathToType(path, method);
					}
					if(!nameSpecified)
					{
						use="Params"+use;
					}
					Schema existingIfAny = this.openAPI.getComponents().getSchemas().get(use);
					if(existingIfAny==null)
					{
						
						this.openAPI.getComponents().addSchemas(use, objectSchema);
						for (Parameter parameter : parameters2) {
							
							Schema schema = parameter.getSchema();
							
							objectSchema.addProperty(parameter.getName(), schema);
						}
					}
					else
					{
						throw new RuntimeException("path= "+path+", method="+method.name()+" is trying to create already in use type of "+use);
					}
					
				}
				
			}
			else
			{
				//we assume no params or of size 1
				//even for size 1 we dont need new wrapper types
			}
		}
	};
	
private BiConsumer<PathItem, String> validateRefTypesForParamsIfNeeded=  (PathItem pathItem, String path)-> {
		
		//do we need this parameters
		//how to check
	
		Map<HttpMethod, Operation> readOperationsMap = pathItem.readOperationsMap();
		Set<HttpMethod> methodKeySet = readOperationsMap.keySet();
		for (HttpMethod method : methodKeySet) {
			Operation operation = readOperationsMap.get(method);
			
			List<Parameter> parameters2 = operation.getParameters();
			if(parameters2!=null && parameters2.size()>1)//only then we have to consider parameters as wrappers
			{
				Map<String, Object> extensions = operation.getExtensions();
				Object xDamahParamRef = extensions.get(DamahExtns.X_DAMAH_PARAM_REF);
				if(xDamahParamRef!=null && xDamahParamRef instanceof String)
				{
					//since this is to be a ref we didnt need to create earlier
					//must validate here
					String xDamahParamRefStr=(String) xDamahParamRef;
					Schema o = this.openAPI.getComponents().getSchemas().get(xDamahParamRefStr);
					if(o !=null)
					{
						if(o instanceof ObjectSchema)
						{
							ObjectSchema objectSchema=(ObjectSchema) o;
							//we dont bother about additional properties
							//we not considering maps
							if(objectSchema.getProperties().size()==parameters2.size())
							{
								Map<String, Schema> properties = objectSchema.getProperties();
								Set<String> propertiesKeySet = properties.keySet();
								
								for (Parameter parameter : parameters2) {
									
									Schema schema = parameter.getSchema();
									Schema schema2 = objectSchema.getProperties().get(parameter.getName());
									//these two must be equal
									//lets trust equals //for now
									if(!schema.equals(schema2))
									{
										//during code generation better to break and protest
										throw new RuntimeException("path= "+path+", method="+method.name()+" specified x-damah-param-ref="+xDamahParamRefStr+" but actual parameters dont match the properties. They should be same in all aspects.");
									}
								}
							}
							else
							{
								Map<String, Schema> properties = objectSchema.getProperties();
								
								throw new RuntimeException("path= "+path+", method="+method.name()+" specified x-damah-param-ref="+xDamahParamRefStr+" but actual parameters dont match the properties in size");
							}
							
							
						}
					}
					else
					{
						throw new RuntimeException("path= "+path+", method="+method.name()+" specified x-damah-param-ref="+xDamahParamRefStr+" which is not defined");
					}
					
				}
					
			
				
			}
			else if(parameters2==null || parameters2.size()==0)//
			{
				Map<String, Object> extensions = operation.getExtensions();
				Object xDamahParamRef = extensions.get(DamahExtns.X_DAMAH_PARAM_REF);
				if(xDamahParamRef!=null && xDamahParamRef instanceof String)
				{
					String xDamahParamRefStr=(String) xDamahParamRef;
					//since this is to be a ref we didnt need to create earlier
					//must copy parametrs

					
					final List<Parameter> list = xDamahParamMap.get(xDamahParamRefStr);
					operation.setParameters(list);
					
				}
			}
			
		}
	};
	
	
private BiConsumer<PathItem, String> enforceRequestBodyFormsAreNotInline=  (PathItem pathItem, String path)-> {
		
		//do we need this parameters
		//how to check
	
		Map<HttpMethod, Operation> readOperationsMap = pathItem.readOperationsMap();
		Set<HttpMethod> methodKeySet = readOperationsMap.keySet();
		for (HttpMethod method : methodKeySet) {
			Operation operation = readOperationsMap.get(method);
			Map<String, Object> extensions = operation.getExtensions();
			boolean mustCheck=false;
			if(extensions!=null)
			{
				Object xDamahObj = extensions.get(DamahExtns.X_DAMAH);
				if(xDamahObj!=null && xDamahObj instanceof Boolean && ((Boolean)xDamahObj).booleanValue())
				{
					mustCheck=true;
					
				}
				else if(xDamahObj!=null && xDamahObj instanceof Boolean && (!((Boolean)xDamahObj).booleanValue()))
				{
					mustCheck=false;//but no intention to geenerate the api so it must exist
					
				}
				else
				{
					Object xDamahParamType = extensions.get(DamahExtns.X_DAMAH_PARAM_TYPE);
					if(xDamahParamType!=null && xDamahParamType instanceof String)
					{
						mustCheck=true;
					}
					else
					{
						Object xDamahParamTypeRef = extensions.get(DamahExtns.X_DAMAH_PARAM_REF);
						if(xDamahParamTypeRef!=null && xDamahParamTypeRef instanceof String)
						{
							mustCheck=true;
						}
					}
					Object xDamahService = extensions.get(DamahExtns.X_DAMAH_SERVICE);
					if(xDamahService!=null && xDamahService instanceof String)
					{
						mustCheck=true;
					}
					//x-damah-service
				}
			}
			
			if(mustCheck)
			{
				RequestBody requestBody = operation.getRequestBody();
				if(requestBody!=null)
				{
					Content requestBodyContent = requestBody.getContent();
					if(requestBodyContent!=null)
					{
						Set<Entry<String, MediaType>> contentEntrySet = requestBodyContent.entrySet();
						if(contentEntrySet!=null)
						{
							for (Entry<String, MediaType> contentEntry : contentEntrySet) {
								String contentType = contentEntry.getKey();
								
								boolean mustcheckForThisContentType=false;
								//application/x-www-form-urlencoded
								if(contentType.toLowerCase().equals("application/x-www-form-urlencoded"))
								{
									mustcheckForThisContentType=true;
								}
								//TODO
								//add check for multi part also
								if(mustcheckForThisContentType)
								{
									MediaType value = contentEntry.getValue();
									//io.swagger.v3.oas.models.media.Schema
									Schema schema = value.getSchema();
									if(schema!=null)
									{
										String get$ref = schema.get$ref();
										String type=schema.getType();
										logger.debug("--------type="+type);
										Map<String, Schema> properties=schema.getProperties();
										if(properties!=null)
										{
											logger.debug("------properties.size()="+properties.size());
										}
										if(get$ref==null)
										{
											throw new RuntimeException("path:"+path+",method:"+method+"requestBody-contentType:"+contentType+". Its schema does not use a ref. With xdamah must use ref");
										}
										
										
									}
									else
									{
										throw new RuntimeException("path:"+path+",method:"+method+"requestBody-contentType:"+contentType+" does not specify a schema");
									}
								}
							}
						}
					}
				}
				
			}
			
			
			
		}
	};

	private String pathToType(String path, HttpMethod method) {
		StringBuilder sb= new StringBuilder();
		for (int i = 0, size=path.length(); i < size; i++) {
			char c=path.charAt(i);
			if(Character.isLetterOrDigit(c))
			{
				sb.append(c);
			}
		}
		sb.append(method.name());
		return sb.toString();
	}

	protected Operation consider(Operation operation)
	{
		Operation ret=operation;
		if(operation!=null)
		{
			Map<String, Object> extensions = operation.getExtensions();
			
			if(extensions!=null)
			{
				Object xDamahObj = extensions.get(DamahExtns.X_DAMAH);
				if(xDamahObj!=null && xDamahObj instanceof Boolean && ((Boolean)xDamahObj).booleanValue())
				{
					ret=null;
					
				}
				else if(xDamahObj!=null && xDamahObj instanceof Boolean && (!((Boolean)xDamahObj).booleanValue()))
				{
					ret=null;//but no intention to geenerate the api so it must exist
					
				}
				else
				{
					Object xDamahParamType = extensions.get(DamahExtns.X_DAMAH_PARAM_TYPE);
					if(xDamahParamType!=null && xDamahParamType instanceof String)
					{
						ret=null;
					}
					else
					{
						Object xDamahParamTypeRef = extensions.get(DamahExtns.X_DAMAH_PARAM_REF);
						if(xDamahParamTypeRef!=null && xDamahParamTypeRef instanceof String)
						{
							ret=null;
						}
					}
					Object xDamahService = extensions.get(DamahExtns.X_DAMAH_SERVICE);
					if(xDamahService!=null && xDamahService instanceof String)
					{
						ret=null;
					}
					//x-damah-service
				}
				
				
			}
		}
		return ret;
	}
	//didnt need below methods but had no choice since they were private
	private void processOperation(String resourcePath, String httpMethod, Operation operation, Map<String, List<CodegenOperation>> operations, PathItem path) {
        if (operation == null) {
            return;
        }
        if (System.getProperty("debugOperations") != null) {
            LOGGER.info("processOperation: resourcePath= " + resourcePath + "\t;" + httpMethod + " " + operation + "\n");
        }
        List<Tag> tags = new ArrayList<>();

        List<String> tagNames = operation.getTags();
        List<Tag> swaggerTags = this.openAPI.getTags();
        if (tagNames != null) {
            if (swaggerTags == null) {
                for (String tagName : tagNames) {
                    tags.add(new Tag().name(tagName));
                }
            } else {
                for (String tagName : tagNames) {
                    boolean foundTag = false;
                    for (Tag tag : swaggerTags) {
                        if (tag.getName().equals(tagName)) {
                            tags.add(tag);
                            foundTag = true;
                            break;
                        }
                    }

                    if (!foundTag) {
                        tags.add(new Tag().name(tagName));
                    }
                }
            }
        }

        if (tags.isEmpty()) {
            tags.add(new Tag().name("default"));
        }

        /*
         build up a set of parameter "ids" defined at the operation level
         per the swagger 2.0 spec "A unique parameter is defined by a combination of a name and location"
          i'm assuming "location" == "in"
        */
        Set<String> operationParameters = new HashSet<>();
        if (operation.getParameters() != null) {
            for (Parameter parameter : operation.getParameters()) {
                operationParameters.add(generateParameterId(parameter));
            }
        }

        //need to propagate path level down to the operation
        if (path.getParameters() != null) {
            for (Parameter parameter : path.getParameters()) {
                //skip propagation if a parameter with the same name is already defined at the operation level
                if (!operationParameters.contains(generateParameterId(parameter)) && operation.getParameters() != null) {
                    operation.getParameters().add(parameter);
                }
            }
        }

        final Map<String, Schema> schemas = openAPI.getComponents() != null ? openAPI.getComponents().getSchemas() : null;
        final Map<String, SecurityScheme> securitySchemes = openAPI.getComponents() != null ? openAPI.getComponents().getSecuritySchemes() : null;
        final List<SecurityRequirement> globalSecurities = openAPI.getSecurity();
        for (Tag tag : tags) {
            try {
                CodegenOperation codegenOperation = config.fromOperation(resourcePath, httpMethod, operation, schemas, openAPI);
                codegenOperation.tags = new ArrayList<>(tags);
                config.addOperationToGroup(config.sanitizeTag(tag.getName()), resourcePath, operation, codegenOperation, operations);

                List<SecurityRequirement> securities = operation.getSecurity();
                if (securities != null && securities.isEmpty()) {
                    continue;
                }
                Map<String, SecurityScheme> authMethods = getAuthMethods(securities, securitySchemes);
                if (authMethods == null || authMethods.isEmpty()) {
                    authMethods = getAuthMethods(globalSecurities, securitySchemes);
                }

                if (authMethods != null && !authMethods.isEmpty()) {
                    codegenOperation.authMethods = config.fromSecurity(authMethods);
                    codegenOperation.getVendorExtensions().put(CodegenConstants.HAS_AUTH_METHODS_EXT_NAME, Boolean.TRUE);
                }
            } catch (Exception ex) {
                String msg = "Could not process operation:\n" //
                        + "  Tag: " + tag + "\n"//
                        + "  Operation: " + operation.getOperationId() + "\n" //
                        + "  Resource: " + httpMethod + " " + resourcePath + "\n"//
                       // + "  Definitions: " + swagger.getDefinitions() + "\n"  //
                        + "  Exception: " + ex.getMessage();
                throw new RuntimeException(msg, ex);
            }
        }

    }
	private Map<String, SecurityScheme> getAuthMethods(List<SecurityRequirement> securities, Map<String, SecurityScheme> securitySchemes) {
        if (securities == null || (securitySchemes == null || securitySchemes.isEmpty())) {
            return null;
        }
        final Map<String, SecurityScheme> authMethods = new HashMap<>();
        for (SecurityRequirement requirement : securities) {
            for (String key : requirement.keySet()) {
                SecurityScheme securityScheme = securitySchemes.get(key);
                if (securityScheme != null) {
                    authMethods.put(key, securityScheme);
                }
            }
        }
        return authMethods;
    }
	 private static String generateParameterId(Parameter parameter) {
	        return parameter.getName() + ":" + parameter.getIn();
	    }



}

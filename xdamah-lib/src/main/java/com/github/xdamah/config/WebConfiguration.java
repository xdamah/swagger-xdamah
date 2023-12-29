package com.github.xdamah.config;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.interaction.request.CustomRequestValidator;
import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.report.ValidationReport.Message;
import com.atlassian.oai.validator.report.ValidationReport.MessageContext;
import com.atlassian.oai.validator.report.ValidationReport.MessageContext.Pointers;
import com.atlassian.oai.validator.springmvc.OpenApiValidationInterceptor;
import com.atlassian.oai.validator.springmvc.SpringMVCLevelResolverFactory;
import com.atlassian.oai.validator.whitelist.ValidationErrorsWhitelist;
import com.atlassian.oai.validator.whitelist.rule.WhitelistRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jackson.NodeType;

import io.swagger.v3.oas.models.OpenAPI;

@Configuration
public class WebConfiguration implements WebMvcConfigurer{
	private static final Logger logger = LoggerFactory.getLogger(WebConfiguration.class);
	@Value("${validator.enabled:true}")
	boolean validatorEnabled=true;
	@Autowired
	OpenAPI openApi;
	@Autowired
	ObjectMapper mapper;
	@Autowired
	CustomRequestValidator customRequestValidator;
	
	

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		if(validatorEnabled)
        {
			 ValidationErrorsWhitelist whitelist = ValidationErrorsWhitelist.create();
			 
			  //whitelist = allowCustomTypes(whitelist);
			 whitelist= reqBodySchemaOneOfAnother(whitelist);
			 whitelist= reqBodySchemaOneOf(whitelist);
			  whitelist = additionalProperties(whitelist);
			  whitelist=allowStringIntegerFormFieldType(whitelist);
			  whitelist=allowSchemaUnknownXml(whitelist);
			  OpenApiInteractionValidator validator = OpenApiInteractionValidator
						 .createFor(openApi)
					    
					      .withCustomRequestValidation(customRequestValidator)
					      .withLevelResolver(SpringMVCLevelResolverFactory.create())
					      .withWhitelist(whitelist)
					      .build();
			
			final OpenApiValidationInterceptor openApiValidationInterceptor = new OpenApiValidationInterceptor(validator);
    		  registry.addInterceptor(openApiValidationInterceptor);
        }
	}
	
	private ValidationErrorsWhitelist allowSchemaUnknownXml(ValidationErrorsWhitelist whitelist) {
		whitelist=whitelist.withRule("SchemaUnknownXml", new WhitelistRule() {
			
			@Override
			public boolean matches(Message message, ApiOperation operation, Request request, Response response) {
				
				boolean matched=false;
				if(message.getKey().equals("validation.request.body.schema.unknownError"))
				{
					String theMessage = message.getMessage();
					String lookFor = "An error occurred during schema validation - unhandled token type NOT_AVAILABLE.";
					if(theMessage!=null && theMessage.equals(lookFor))
					{
						Optional<MessageContext> context = message.getContext();
						if(context!=null && context.isPresent())
						{
							MessageContext messageContext = context.get();
							if(messageContext!=null)
							{
								Optional<String> apiRequestContentTypeOpt = messageContext.getApiRequestContentType();
								if(apiRequestContentTypeOpt.isPresent())
								{
									String apiRequestContentType = apiRequestContentTypeOpt.get();
									if(apiRequestContentType!=null && apiRequestContentType.equals("application/xml"))
									{
										matched=true;
									}
								}
							}
						}
					}
					
					
				
					
					
				}
				return matched;
			}

			

			
		});
		return whitelist;
	}
	
	private ValidationErrorsWhitelist allowStringIntegerFormFieldType(ValidationErrorsWhitelist whitelist) {
		whitelist=whitelist.withRule("stringintegerformfieldtype", new WhitelistRule() {
			
			@Override
			public boolean matches(Message message, ApiOperation operation, Request request, Response response) {
				
				boolean matched=false;
				if(message.getKey().equals("validation.request.body.schema.type"))
				{
					String theMessage = message.getMessage();
					Optional<MessageContext> context = message.getContext();
					if(context!=null && context.isPresent())
					{
						MessageContext messageContext = context.get();
						if(messageContext!=null)
						{
							Optional<String> apiRequestContentTypeOpt = messageContext.getApiRequestContentType();
							if(apiRequestContentTypeOpt.isPresent())
							{
								String apiRequestContentType = apiRequestContentTypeOpt.get();
								if(apiRequestContentType!=null && apiRequestContentType.equals("application/x-www-form-urlencoded"))
								{
									Optional<Pointers> pointersOpt = messageContext.getPointers();
									if(pointersOpt.isPresent())
									{
										Pointers pointers = pointersOpt.get();
										if(pointers!=null)
										{
											String instance = pointers.getInstance();
											if(instance!=null)
											{
												String template = "[Path '%s'] Instance type (integer) does not match any allowed primitive type (allowed: [\"string\"])";
												 String s
										            = String.format(template, instance);
												 System.out.println("s="+s);
												 if(theMessage.equals(s))
												 {
													 matched=true;
												 }
											}
										}
									}
								}
							}
						}
					}
					
				
					
					
				}
				return matched;
			}

			

			
		});
		return whitelist;
	}
	
	private ValidationErrorsWhitelist reqBodySchemaOneOfAnother(ValidationErrorsWhitelist whitelist) {
		whitelist=whitelist.withRule("reqBodySchemaOneOfAnother", new WhitelistRule() {
			
			@Override
			public boolean matches(Message message, ApiOperation operation, Request request, Response response) {
				
				boolean matched=false;
				if(message.getKey().equals("validation.request.body.schema.oneOf"))
									
				{
					String theMessage = message.getMessage();
					
					String lookFor = "Instance failed to match exactly one schema (matched 0 out of ";
					if(theMessage!=null && theMessage.contains(lookFor))
					{
						List<String> additionalInfos = message.getAdditionalInfo();
						if(additionalInfos!=null && additionalInfos.size()>0)
						{
							boolean oneOf1=true;
							for (int i = 0; i < additionalInfos.size(); i++) {
								String additionalInfo = additionalInfos.get(i);
								if(!(additionalInfo.startsWith("/components/schemas/") && additionalInfo.contains(": Instance failed to match all required schemas (matched only 0 out of ")))
								{
									oneOf1=false;
								}
							}
							if(oneOf1)
							{
								//later add more checks
								matched=true;
							}
						}
						
						
						
					}
					
				}
				return matched;
			}

			

			
		});
		return whitelist;
	}
	
	private ValidationErrorsWhitelist reqBodySchemaOneOf(ValidationErrorsWhitelist whitelist) {
		whitelist=whitelist.withRule("reqBodySchemaOneOf", new WhitelistRule() {
			
			@Override
			public boolean matches(Message message, ApiOperation operation, Request request, Response response) {
				
				boolean matched=false;
				if(message.getKey().equals("validation.request.body.schema.oneOf"))
				{
					String theMessage = message.getMessage();
					
					String lookFor = "Instance failed to match exactly one schema (matched 0 out of ";
					if(theMessage!=null && theMessage.startsWith(lookFor))
					{
						List<String> additionalInfos = message.getAdditionalInfo();
						if(additionalInfos!=null && additionalInfos.size()>0)
						{
							boolean oneOf1=true;
							for (int i = 0; i < additionalInfos.size(); i++) {
								String additionalInfo = additionalInfos.get(i);
								if(!additionalInfo.startsWith("/oneOf/"+i+": Instance failed to match all required schemas (matched only 0 out of "))
								{
									oneOf1=false;
								}
							}
							if(oneOf1)
							{
								List<Message> nestedMessages = message.getNestedMessages();
								if(nestedMessages!=null && nestedMessages.size()>0)
								{
									boolean oneOf2=true;
									for (int i = 0; i < nestedMessages.size(); i++) {
										Message nestedMessage = nestedMessages.get(i);
										String nestdMessageKey = nestedMessage.getKey();
										if(!(nestdMessageKey!=null && nestdMessageKey.equals("validation.request.body.schema.allOf")))
										{
											oneOf2=false;
										}
									}
									boolean oneOf3=true;
									for (int i = 0; i < nestedMessages.size(); i++) {
										Message nestedMessage = nestedMessages.get(i);
										String nestedMessageMessage = nestedMessage.getMessage();
										if(!(nestedMessageMessage.startsWith("Instance failed to match all required schemas (matched only 0 out of ")))
										{
											oneOf3=false;
										}
									}
									if(oneOf2 && oneOf3)
									{
										//if needed can check for more
										matched=true;
									}
								}
							}
						}
						
						
						
					}
					
				}
				return matched;
			}

			

			
		});
		return whitelist;
	}
	
	private ValidationErrorsWhitelist additionalProperties(ValidationErrorsWhitelist whitelist) {
		whitelist=whitelist.withRule("additionalProperties", new WhitelistRule() {
			
			@Override
			public boolean matches(Message message, ApiOperation operation, Request request, Response response) {
				
				boolean matched=false;
				if(message.getKey().equals("validation.request.body.schema.additionalProperties"))
				{
					String theMessage = message.getMessage();
					
					String lookFor = "Object instance has properties which are not allowed by the schema:";
					if(theMessage!=null && theMessage.startsWith(lookFor))
					{
						Optional<MessageContext> context = message.getContext();
						if(context!=null && context.isPresent())
						{
							MessageContext messageContext = context.get();
							if(messageContext!=null)
							{
								Optional<String> apiRequestContentType = messageContext.getApiRequestContentType();
								if(apiRequestContentType.isPresent())
								{
									String arct = apiRequestContentType.get();
									if(arct.equals("application/x-www-form-urlencoded"))
									{
										matched=true;
									}
								}
							}
						}
						
						
					}
					
				}
				return matched;
			}

			

			
		});
		return whitelist;
	}

	private ValidationErrorsWhitelist allowCustomTypes(ValidationErrorsWhitelist whitelist) {
		whitelist=whitelist.withRule("allowcustomtypes", new WhitelistRule() {
			
			@Override
			public boolean matches(Message message, ApiOperation operation, Request request, Response response) {
				
				boolean matched=false;
				if(message.getKey().equals("validation.request.body.schema.processingError"))
				{
					String theMessage = message.getMessage();
					
					String lookFor = "Invalid JSON Schema, cannot continue\nSyntax errors:";
					if(theMessage!=null && theMessage.startsWith(lookFor))
					{
						String errors=theMessage.substring(lookFor.length());
						
						try {
							ArrayNode array=mapper.readValue(errors, ArrayNode.class);
							for (int i = 0,size=array.size(); i < size; i++) {
								JsonNode jsonNode = array.get(i);
								String keyword=getTextAttribute(jsonNode, "keyword");
								String domain=getTextAttribute(jsonNode, "domain");
								
								if(keyword!=null && keyword.equals("type") && domain!=null && domain.equals("syntax"))
								{
									ArrayNode valid=getArrayAttribute(jsonNode, "valid");
									if(valid!=null)
									{
										//TODO reimplement without using toStrings
										String validToString=valid.toString().replaceAll("\"", "");
										String check=Arrays.toString(NodeType.values()).replaceAll(" ", "");
										if(validToString.equals(check))
										{
											String found=getTextAttribute(jsonNode, "found");
											if(found!=null)
											{
												String actualSubMessage = getTextAttribute(jsonNode, "message");
												if(actualSubMessage!=null && actualSubMessage.equals("\""+found+"\" is not a valid primitive type (valid values are: [array, boolean, integer, null, number, object, string])"))
												{
													matched=true;
												}
											}
											
													
											
										}
									}
									
									
								}
								
								
							}
						} catch (JsonProcessingException e) {
							logger.error("json processing exception", e);
						}
					}
					
				}
				return matched;
			}

			

			
		});
		return whitelist;
	}
	
	private String getTextAttribute(JsonNode jsonNode, String attrName) {
		String attrValue=null;
		JsonNode attrNode = jsonNode.get(attrName);
		if(attrNode!=null && attrNode instanceof TextNode)
		{
			TextNode attrTextNode=(TextNode) attrNode;
			attrValue = attrTextNode.asText();
			
		}
		return attrValue;
	}
  
  private ArrayNode getArrayAttribute(JsonNode jsonNode, String attrName) {
	  ArrayNode arrayNode=null;
		JsonNode attrNode = jsonNode.get(attrName);
		if(attrNode!=null && attrNode instanceof ArrayNode)
		{
			arrayNode=(ArrayNode) attrNode;
			
			
		}
		return arrayNode;
	}

}

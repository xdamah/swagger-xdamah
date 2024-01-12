package com.github.xdamah.config;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.atlassian.oai.validator.interaction.request.CustomRequestValidator;
import com.atlassian.oai.validator.springmvc.OpenApiValidationFilter;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.github.xdamah.custom.ConversionServiceBasedDeserializer;
import com.github.xdamah.custom.ConversionServiceBasedSerializer;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;

@Configuration
public class OpenApiValidationConfig {
	private static final Logger logger = LoggerFactory.getLogger(OpenApiValidationConfig.class);
	@Value("${xdamah.validator.enabled:true}")
	boolean validatorEnabled = true;
	@Autowired
	OpenAPI openApi;
	@Autowired
	ObjectMapper mapper;
	@Autowired
	CustomRequestValidator customRequestValidator;
	@Autowired
	GenericApplicationContext context;
	@Autowired
	private ConversionService conversionService;

	@Bean
	public Filter validationFilter() {
		return new OpenApiValidationFilter(validatorEnabled, // enable request validation
				false // enable response validation
		);
	}

	@PostConstruct
	void init() {
		if (customRequestValidator instanceof IOpenApiValidationConfigOnInitWorkaround) {
			IOpenApiValidationConfigOnInitWorkaround initTarget = (IOpenApiValidationConfigOnInitWorkaround) customRequestValidator;
			initTarget.onInitInOpenApiValidationConfig();

			Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer = new Jackson2ObjectMapperBuilderCustomizer() {

				@Override
				public void customize(Jackson2ObjectMapperBuilder jacksonObjectMapperBuilder) {
					List<SimpleModule> modules = new ArrayList<>();
					Map<String, String> customSchemaImportMapping = initTarget.getCustomSchemaImportMapping();
					for (Entry<String, String> customSchemaImportMappingEntry : customSchemaImportMapping.entrySet()) {
						String value = customSchemaImportMappingEntry.getValue();
						try {
							Class c = Class.forName(value);
							boolean used = false;
							SimpleModule module = new SimpleModule(c.getName() + "Module");
							String[] beanNamesForType = context.getBeanNamesForType(Converter.class);
							boolean toStringFromCustomFound = false;
							boolean toCustomFromStringFound = false;
							for (String beanName : beanNamesForType) {
								logger.debug("---beanName=" + beanName);
								Object bean = context.getBean(beanName);
								Class<? extends Object> beanClass = bean.getClass();
								Class<?>[] interfaces = beanClass.getInterfaces();

								Type[] genericInterfaces = beanClass.getGenericInterfaces();

								for (Type genericInterface : genericInterfaces) {

									if (genericInterface instanceof ParameterizedType) {
										ParameterizedType parameterizedType = (ParameterizedType) genericInterface;
										Type rawType = parameterizedType.getRawType();
										if (rawType == Converter.class) {
											logger.debug("rawType=" + rawType.getTypeName() + ",.class="
													+ rawType.getClass().getName());
											Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
											if (actualTypeArguments != null && actualTypeArguments.length == 2) {
												if (actualTypeArguments[0] == c
														&& actualTypeArguments[1] == String.class) {
													toStringFromCustomFound = true;
												} else if (actualTypeArguments[0] == String.class
														&& actualTypeArguments[1] == c) {
													toCustomFromStringFound = true;
												}
											}

										}

									}

									logger.debug("genericInterface=" + genericInterface.getTypeName() + ".class="
											+ genericInterface.getClass().getName());
								}

								if (toStringFromCustomFound && toCustomFromStringFound) {
									break;
								}

							}
							if (!toStringFromCustomFound) {
								logger.error("Missing converter for " + c.getName() + " to String.class");
							}
							if (!toCustomFromStringFound) {
								logger.error("Missing converter for String.class to " + c.getName());
							}

							if (!conversionService.canConvert(String.class, c)) {
								logger.error("missing converter for String.class to " + c.getName());
							} else {
								module.addDeserializer(c, new ConversionServiceBasedDeserializer(c, conversionService));
								used = true;
							}
							logger.debug("-------conversionService=" + conversionService.getClass().getName());

							if (!conversionService.canConvert(c, String.class)) {
								logger.error("missing converter for " + c.getName() + " to String.class");
							} else {
								module.addSerializer(c, new ConversionServiceBasedSerializer(c, conversionService));
								used = true;
							}
							if (used) {
								modules.add(module);
							}
						} catch (ClassNotFoundException e) {
							logger.error("class not found", e);
						}
					}
					Module[] modulesArr = new Module[modules.size()];
					modules.toArray(modulesArr);
					jacksonObjectMapperBuilder.modulesToInstall(modulesArr);

					mapper.setSerializationInclusion(Include.NON_NULL);

					mapper.registerModules(modulesArr);

				}
			};

			context.registerBean(Jackson2ObjectMapperBuilderCustomizer.class,
					() -> jackson2ObjectMapperBuilderCustomizer);
			logger.debug("!!!!context=" + context.getClass().getName());

			Map<String, String> customSchemaImportMapping = initTarget.getCustomSchemaImportMapping();
			for (Entry<String, String> customSchemaImportMappingEntry : customSchemaImportMapping.entrySet()) {

				Schema schema = openApi.getComponents().getSchemas().get(customSchemaImportMappingEntry.getKey());
				if (schema != null) {
					schema.setType("string");
					Map<String, Object> extensions = schema.getExtensions();
					if (extensions == null) {
						extensions = new LinkedHashMap<String, Object>();
						schema.setExtensions(extensions);
					}
					extensions.put("x-imported-type", customSchemaImportMappingEntry.getValue());
				}

			}

		}
	}

	/*
	 * @Bean public WebMvcConfigurer addOpenApiValidationInterceptor() throws
	 * IOException {
	 * 
	 * 
	 * ValidationErrorsWhitelist whitelist = ValidationErrorsWhitelist.create();
	 * 
	 * //whitelist = allowCustomTypes(whitelist); whitelist =
	 * additionalProperties(whitelist);
	 * 
	 * 
	 * 
	 * OpenApiInteractionValidator validator = OpenApiInteractionValidator
	 * .createFor(openApi)
	 * 
	 * .withCustomRequestValidation(customRequestValidator)
	 * .withLevelResolver(SpringMVCLevelResolverFactory.create())
	 * .withWhitelist(whitelist) .build();
	 * 
	 * final OpenApiValidationInterceptor openApiValidationInterceptor = new
	 * OpenApiValidationInterceptor(validator);
	 * 
	 * return new WebMvcConfigurer() {
	 * 
	 * @Override public void addInterceptors(final InterceptorRegistry registry) {
	 * if(validatorEnabled) { registry.addInterceptor(openApiValidationInterceptor);
	 * }
	 * 
	 * } }; }
	 */

	/*
	 * private ValidationErrorsWhitelist
	 * additionalProperties(ValidationErrorsWhitelist whitelist) {
	 * whitelist=whitelist.withRule("additionalProperties", new WhitelistRule() {
	 * 
	 * @Override public boolean matches(Message message, ApiOperation operation,
	 * Request request, Response response) {
	 * 
	 * boolean matched=false; if(message.getKey().equals(
	 * "validation.request.body.schema.additionalProperties")) { String theMessage =
	 * message.getMessage();
	 * 
	 * String lookFor =
	 * "Object instance has properties which are not allowed by the schema:";
	 * if(theMessage!=null && theMessage.startsWith(lookFor)) {
	 * Optional<MessageContext> context = message.getContext(); if(context!=null &&
	 * context.isPresent()) { MessageContext messageContext = context.get();
	 * if(messageContext!=null) { Optional<String> apiRequestContentType =
	 * messageContext.getApiRequestContentType();
	 * if(apiRequestContentType.isPresent()) { String arct =
	 * apiRequestContentType.get();
	 * if(arct.equals("application/x-www-form-urlencoded")) { matched=true; } } } }
	 * 
	 * 
	 * }
	 * 
	 * } return matched; }
	 * 
	 * 
	 * 
	 * 
	 * }); return whitelist; }
	 * 
	 * private ValidationErrorsWhitelist allowCustomTypes(ValidationErrorsWhitelist
	 * whitelist) { whitelist=whitelist.withRule("allowcustomtypes", new
	 * WhitelistRule() {
	 * 
	 * @Override public boolean matches(Message message, ApiOperation operation,
	 * Request request, Response response) {
	 * 
	 * boolean matched=false;
	 * if(message.getKey().equals("validation.request.body.schema.processingError"))
	 * { String theMessage = message.getMessage();
	 * 
	 * String lookFor = "Invalid JSON Schema, cannot continue\nSyntax errors:";
	 * if(theMessage!=null && theMessage.startsWith(lookFor)) { String
	 * errors=theMessage.substring(lookFor.length());
	 * 
	 * try { ArrayNode array=mapper.readValue(errors, ArrayNode.class); for (int i =
	 * 0,size=array.size(); i < size; i++) { JsonNode jsonNode = array.get(i);
	 * String keyword=getTextAttribute(jsonNode, "keyword"); String
	 * domain=getTextAttribute(jsonNode, "domain");
	 * 
	 * if(keyword!=null && keyword.equals("type") && domain!=null &&
	 * domain.equals("syntax")) { ArrayNode valid=getArrayAttribute(jsonNode,
	 * "valid"); if(valid!=null) { //TODO reimplement without using toStrings String
	 * validToString=valid.toString().replaceAll("\"", ""); String
	 * check=Arrays.toString(NodeType.values()).replaceAll(" ", "");
	 * if(validToString.equals(check)) { String found=getTextAttribute(jsonNode,
	 * "found"); if(found!=null) { String actualSubMessage =
	 * getTextAttribute(jsonNode, "message"); if(actualSubMessage!=null &&
	 * actualSubMessage.equals("\""+
	 * found+"\" is not a valid primitive type (valid values are: [array, boolean, integer, null, number, object, string])"
	 * )) { matched=true; } }
	 * 
	 * 
	 * 
	 * } }
	 * 
	 * 
	 * }
	 * 
	 * 
	 * } } catch (JsonProcessingException e) {
	 * logger.error("json processing exception", e); } }
	 * 
	 * } return matched; }
	 * 
	 * 
	 * 
	 * 
	 * }); return whitelist; }
	 */

	/*
	 * 
	 * private String getTextAttribute(JsonNode jsonNode, String attrName) { String
	 * attrValue=null; JsonNode attrNode = jsonNode.get(attrName); if(attrNode!=null
	 * && attrNode instanceof TextNode) { TextNode attrTextNode=(TextNode) attrNode;
	 * attrValue = attrTextNode.asText();
	 * 
	 * } return attrValue; }
	 * 
	 * private ArrayNode getArrayAttribute(JsonNode jsonNode, String attrName) {
	 * ArrayNode arrayNode=null; JsonNode attrNode = jsonNode.get(attrName);
	 * if(attrNode!=null && attrNode instanceof ArrayNode) { arrayNode=(ArrayNode)
	 * attrNode;
	 * 
	 * 
	 * } return arrayNode; }
	 */
}

package io.github.xdamah.config;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.FileUtils;
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
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.atlassian.oai.validator.interaction.request.CustomRequestValidator;
import com.atlassian.oai.validator.springmvc.OpenApiValidationFilter;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ContainerNode;

import io.github.xdamah.custom.ConversionServiceBasedDeserializer;
import io.github.xdamah.custom.ConversionServiceBasedSerializer;
import io.github.xdamah.swagger.SwaggerController;
import io.github.xdamah.util.ContainerNodeCommonModifier;
import io.github.xdamah.util.ContainerNodeModifier;
import io.github.xdamah.util.ContainerNodeReaderPathBuilder;
import io.github.xdamah.validatorextn.DoNothingValidatorExtension;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.ObjectMapperFactory;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension;
import io.swagger.v3.parser.core.models.AuthorizationValue;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.Filter;

@Configuration

public class OpenApiConfig  {
	private static final Logger logger = LoggerFactory.getLogger(OpenApiConfig.class);
	

	@Autowired
	SwaggerController swaggerController;

	@Autowired
	ResourceLoader resourceLoader;

	private ObjectMapper jsonMapper;
	
	@Autowired
	ObjectMapper mapper;
	
	@Autowired
	private ModelPackageUtil modelPackageUtil;
	
	@Autowired(required = false)
	CustomRequestValidator customRequestValidator;
	@Autowired
	DoNothingValidatorExtension doNothingValidatorExtension;
	
	@PostConstruct
	void init()
	{
		if(customRequestValidator==null)
		{
			customRequestValidator=doNothingValidatorExtension;
		}
		logger.info("customRequestValidator="+customRequestValidator.getClass().getName());
	}
	
	
	@Autowired
	GenericApplicationContext context;
	
	@Autowired
	private ConversionService conversionService;

	private ICustomSchemaRegisty customSchemaRegistry;
	
	@Value("${xdamah.validator.enabled:true}")
	boolean validatorEnabled = true;
	
	@Value("${xdamah.actualschemas.show:false}")
	boolean showActualSchemas = false;


	@Bean

	OpenAPI openApi() throws IOException {

		
		logger.debug("invoked openApi()");
		if (customRequestValidator instanceof ICustomSchemaRegisty) {
			customSchemaRegistry = register();
		}
		OpenAPIV3Parser openAPIV3Parser = new OpenAPIV3Parser();
		final ParseOptions options = new ParseOptions();
		options.setResolve(true);

		// options.setResolveFully(true);
		options.setValidateExternalRefs(true);
		jsonMapper = ObjectMapperFactory.createJson();
		JsonNode firstTree = jsonMapper.readTree(new File("api-docs.json"));
		// doingmodifid this because was unable to save the openApi object where the
		// json is in same order as original
		ContainerNodeReaderPathBuilder pathBuilder = new ContainerNodeReaderPathBuilder(modelPackageUtil, customSchemaRegistry);
		
		
		
		pathBuilder.buildPathsAndXdamahModels((ContainerNode) firstTree, "");
		ContainerNodeCommonModifier firstModifier = new ContainerNodeCommonModifier(
				pathBuilder.getPathContainerNodeMap(), pathBuilder.getParametersMap() , 
				resourceLoader, jsonMapper);
		firstModifier.modify((ContainerNode) firstTree, "");

		// firstTree ready for parsing

		JsonNode secondTree = firstTree.deepCopy();
		pathBuilder = new ContainerNodeReaderPathBuilder(modelPackageUtil, customSchemaRegistry);
		pathBuilder.buildPathsAndXdamahModels((ContainerNode) secondTree, "");
		
		ContainerNodeModifier modifier = new ContainerNodeModifier(
				pathBuilder.getPathContainerNodeMap(),
				pathBuilder.getParametersMap() ,
				resourceLoader, jsonMapper);
		modifier.modify((ContainerNode) secondTree, "");
		
		byte[] modified = jsonMapper.writeValueAsBytes(secondTree);
		
		swaggerController.setModifiedJson(modified);
		// String modified = jsonMapper.writeValueAsString(readTree);
		if(showActualSchemas)
		{
			jsonMapper.writerWithDefaultPrettyPrinter().writeValue(new File("api-docs-check.json"), secondTree);
		}
		String swaggerContent = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(firstTree);
		if(showActualSchemas)
		{
			FileUtils.write(new File("api-docs-firstTree.json"), swaggerContent);
		}
		// OpenAPI openApi = openAPIV3Parser.read("api-docs.json", null, options);
		OpenAPI openApi = this.read(swaggerContent, null, options, openAPIV3Parser);
		// saveUsingParserJustToSeeDifference(openApi);
		paramsToTypes(openApi);
		customSchemaRegistry.setOpenApi(openApi);
		
		setCustomSchemaTypeToString(openApi);
		
		return openApi;

	}

	private void setCustomSchemaTypeToString(OpenAPI openApi) {
		if (customRequestValidator instanceof ICustomSchemaRegisty) {
					
					Map<String, String> customSchemaImportMapping = customSchemaRegistry.getCustomSchemaImportMapping();
					
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
	
	@Bean
	public Filter validationFilter() {
		return new OpenApiValidationFilter(validatorEnabled, // enable request validation
				false // enable response validation
		);
	}
	
	

	public OpenAPI read(String swaggerAsString, List<AuthorizationValue> auths, ParseOptions resolve,
			OpenAPIV3Parser openAPIV3Parser) {
		if (swaggerAsString == null) {
			return null;
		}

		final List<SwaggerParserExtension> parserExtensions = openAPIV3Parser.getExtensions();
		SwaggerParseResult parsed;
		for (SwaggerParserExtension extension : parserExtensions) {
			parsed = extension.readContents(swaggerAsString, auths, resolve);
			// parsed = extension.readLocation(location, auths, resolve);
			for (String message : parsed.getMessages()) {
				// LOGGER.info("{}: {}", extension, message);
				logger.debug(extension + ": " + message);
			}
			final OpenAPI result = parsed.getOpenAPI();
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	private void saveUsingParserJustToSeeDifference(OpenAPI openApi)
			throws IOException, StreamWriteException, DatabindException {
		File file = new File("result-api-docs.json");
		logger.debug("saved in " + file.getAbsolutePath());
		Json.mapper().writeValue(file, openApi);
	}

	boolean isIndexPath(String path) {
		return path.endsWith("]");
	}

	private static void paramsToTypes(OpenAPI openApi) {
		if (openApi != null) {
			Paths paths = openApi.getPaths();
			if (paths != null) {
				Set<String> pathsKeySet = paths.keySet();
				for (String path : pathsKeySet) {
					PathItem pathItem = paths.get(path);
					examinePath(openApi, path, pathItem);

				}
			}

			Map<String, PathItem> webhooks = openApi.getWebhooks();
			if (webhooks != null) {
				Set<String> webHooksKeySet = webhooks.keySet();
				for (String path : webHooksKeySet) {
					PathItem pathItem = webhooks.get(path);
					examinePath(openApi, path, pathItem);
				}
			}
		} else {
			throw new RuntimeException("Is the openApi specs file valid");
		}

	}

	private static void examinePath(OpenAPI openApi, String path, PathItem pathItem) {

		Map<HttpMethod, Operation> readOperationsMap = pathItem.readOperationsMap();
		if (readOperationsMap != null) {
			Set<HttpMethod> methodKeySet = readOperationsMap.keySet();
			if (methodKeySet != null) {
				for (HttpMethod method : methodKeySet) {
					if (method != null) {
						logger.debug("***Path=" + path + ",method=" + method.name());
						Operation operation = readOperationsMap.get(method);
						if (operation != null) {
							RequestBody requestBody = operation.getRequestBody();
							if (requestBody != null) {
								Content content = requestBody.getContent();
								if (content != null) {

									Set<Entry<String, MediaType>> entrySet = content.entrySet();
									if (entrySet != null) {
										for (Entry<String, MediaType> entry : entrySet) {

											String key = entry.getKey();
											MediaType value = entry.getValue();
											if (value != null) {
												Map<String, Example> examples = value.getExamples();
												if (examples != null) {
													Set<Entry<String, Example>> examplesEntrySet = examples.entrySet();
													if (examplesEntrySet != null) {
														for (Entry<String, Example> examplesEntry : examplesEntrySet) {
															String examplesKey = examplesEntry.getKey();
															Example example = examplesEntry.getValue();
															if (example != null) {
																String externalValue = example.getExternalValue();
																if (externalValue != null) {
																	logger.debug("externalValue=" + externalValue);
																}
															}

														}
													}

												}

											}

										}
									}

								}

							}

						}

					}

				}
			}

		}

	}
	
	private ICustomSchemaRegisty register() {
		ICustomSchemaRegisty initTarget = (ICustomSchemaRegisty) customRequestValidator;
		initTarget.onInitRegisterCustomSchemas();

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
		return initTarget;
	}

	
	//web configuration
	

}

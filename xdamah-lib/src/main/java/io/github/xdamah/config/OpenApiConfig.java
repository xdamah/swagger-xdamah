package io.github.xdamah.config;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.*;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.BiConsumer;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;

import io.github.xdamah.swagger.SwaggerController;
import io.github.xdamah.util.*;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.parser.ObjectMapperFactory;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.extensions.SwaggerParserExtension;
import io.swagger.v3.parser.core.models.AuthorizationValue;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import jakarta.servlet.ServletContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamWriteException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.*;

@Configuration
public class OpenApiConfig {
	private static final Logger logger = LoggerFactory.getLogger(OpenApiConfig.class);

	@Autowired
	SwaggerController swaggerController;

	@Autowired
	ResourceLoader resourceLoader;

	private ObjectMapper jsonMapper;
	
	@Autowired
	private ModelPackageUtil modelPackageUtil;

	@Bean
	OpenAPI openApi() throws IOException {

		logger.debug("invoked openApi()");
		OpenAPIV3Parser openAPIV3Parser = new OpenAPIV3Parser();
		final ParseOptions options = new ParseOptions();
		options.setResolve(true);

		// options.setResolveFully(true);
		options.setValidateExternalRefs(true);
		jsonMapper = ObjectMapperFactory.createJson();
		JsonNode firstTree = jsonMapper.readTree(new File("api-docs.json"));
		// doingmodifid this because was unable to save the openApi object where the
		// json is in same order as original
		ContainerNodeReaderPathBuilder pathBuilder = new ContainerNodeReaderPathBuilder(modelPackageUtil);
		pathBuilder.buildModels((ContainerNode) firstTree);
		pathBuilder.buildPaths((ContainerNode) firstTree, "");
		ContainerNodeCommonModifier firstModifier = new ContainerNodeCommonModifier(
				pathBuilder.getPathContainerNodeMap(), pathBuilder.getParametersMap() , 
				resourceLoader, jsonMapper);
		firstModifier.modify((ContainerNode) firstTree, "");

		// firstTree ready for parsing

		JsonNode secondTree = firstTree.deepCopy();
		pathBuilder = new ContainerNodeReaderPathBuilder(modelPackageUtil);
		pathBuilder.buildPaths((ContainerNode) secondTree, "");
		
		ContainerNodeModifier modifier = new ContainerNodeModifier(
				pathBuilder.getPathContainerNodeMap(),
				pathBuilder.getParametersMap() ,
				resourceLoader, jsonMapper);
		modifier.modify((ContainerNode) secondTree, "");
		
		byte[] modified = jsonMapper.writeValueAsBytes(secondTree);
		
		swaggerController.setModifiedJson(modified);
		// String modified = jsonMapper.writeValueAsString(readTree);
		jsonMapper.writeValue(new File("api-docs-check.json"), secondTree);
		String swaggerContent = jsonMapper.writeValueAsString(firstTree);
		FileUtils.write(new File("api-docs-firstTree.json"), swaggerContent);
		// OpenAPI openApi = openAPIV3Parser.read("api-docs.json", null, options);
		OpenAPI openApi = this.read(swaggerContent, null, options, openAPIV3Parser);
		// saveUsingParserJustToSeeDifference(openApi);
		paramsToTypes(openApi);

		return openApi;

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

}

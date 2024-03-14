package io.github.xdamah.validatorextn;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpHeaders;

import com.atlassian.oai.validator.interaction.request.CustomRequestValidator;
import com.atlassian.oai.validator.model.ApiOperation;
import com.atlassian.oai.validator.model.Body;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Request.Method;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.report.ValidationReport.Message;
import com.atlassian.oai.validator.report.ValidationReport.MessageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.xdamah.config.ICustomSchemaRegisty;
import io.github.xdamah.config.ModelPackageUtil;
import io.github.xdamah.config.NonSpringHolder;
import io.github.xdamah.constants.Constants;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import jakarta.annotation.Nonnull;

//this is just a democustome validator
//its verysimple only for demo purposes
//can be enhanced later 
//when its base class is taking care of all  extension validation requirements can shift the base class to the core library

//TODO
/*
 * 5. In custome validators map them to swagger types and custom types so that we cannot apply them to anything but what they get mapped to
eg: type:string, format:, importedType:
eg. type:array, item-type:
e.g type:object
add some content type checking before applying custom validation
also add guess contentType

Right now only targetting TextNode.
Let it be smarter
 */

public abstract class BaseValidatorExtension
		implements CustomRequestValidator, ICustomSchemaRegisty {
	private static final Logger logger = LoggerFactory.getLogger(BaseValidatorExtension.class);
	private Map<String, String> customSchemasImportMapping = new HashMap<>();
	@Autowired
	private OpenAPI openApi;
	@Autowired
	ModelPackageUtil modelPackageUtil;

	private String[] watchedExtensions = watchedExtensions();

	private Map<String, IValidator> mappedValidators = mapValidators();

	protected abstract String[] watchedExtensions();

	protected abstract HashMap<String, IValidator> mapValidators();

	@Override
	public ValidationReport validate(@Nonnull Request request, @Nonnull ApiOperation apiOperation) {
		String typeName = null;
		List<Message> messages = new ArrayList<>();
		// Map<String, List<String>> watchedExtensionPropertyNames= new HashMap<>();
		// this validation is not to check for required and madatory properties /
		// content being present
		// that we assume is taken care of prior
		Optional<Body> requestBody = request.getRequestBody();

		if (requestBody != null && requestBody.isPresent()) {

			Body body = requestBody.get();
			if (body != null) {
				inOperation(request, apiOperation, messages, body);
			}
		}

		// secondStep(request, typeName, watchedExtensionPropertyNames, messages);
		if (messages.size() > 0) {
			return ValidationReport.from(messages);
		}

		return ValidationReport.empty();
	}

	private void inOperation(Request request, ApiOperation apiOperation, List<Message> messages, Body body) {

		Operation operation = apiOperation.getOperation();
		if (operation != null) {
			RequestBody operationRequestBody = operation.getRequestBody();
			if (operationRequestBody != null) {
				Content operationReqBodyContent = operationRequestBody.getContent();
				if (operationReqBodyContent != null) {
					Optional<String> incomingContentType = request.getHeaderValue(HttpHeaders.CONTENT_TYPE);
					if (incomingContentType.isPresent()) {
						String actualContentType = incomingContentType.get();
						if (actualContentType != null) {
							actualContentType = actualContentType.toLowerCase();
							if (actualContentType.equals(org.springframework.http.MediaType.APPLICATION_JSON_VALUE)) {
								onJson(request, messages, operationReqBodyContent, actualContentType, body);
							} else if (actualContentType
									.equals(org.springframework.http.MediaType.APPLICATION_XML_VALUE)) {
								onXml(request, messages, body, operationReqBodyContent, actualContentType);
								
							} 
							else if (actualContentType.startsWith(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)) {
								actualContentType = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
								onJson(request, messages, operationReqBodyContent, actualContentType, body);
							}
							else if (actualContentType.startsWith(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
								actualContentType = org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE;
								onJson(request, messages, operationReqBodyContent, actualContentType, body);
							}
							else {

							}

						}

					}
				}

			}

		}
	}

	private void onXml(Request request, List<Message> messages, Body body, Content operationReqBodyContent,
			String actualContentType) {
		String xml = null;
		try {
			xml = body.toString(Charset.defaultCharset());
		} catch (IOException e) {
			logger.error("Unable to extract xml", e);
		}

		JsonNode readValue = NonSpringHolder.INSTANCE.xmlToJsonNode(operationReqBodyContent, actualContentType, xml);
		processJsonNode(request, messages, operationReqBodyContent, actualContentType, readValue);
	}

	private void onJson(Request request, List<Message> messages, Content operationReqBodyContent,
			String actualContentType, Body body) {
		Optional<JsonNode> jsonNodeBodyOptional = tryToReturn(body);
		if (jsonNodeBodyOptional.isPresent()) {
			JsonNode actualJsonNodeBody = jsonNodeBodyOptional.get();
			processJsonNode(request, messages, operationReqBodyContent, actualContentType, actualJsonNodeBody);
		}

	}

	private void processJsonNode(Request request, List<Message> messages, Content operationReqBodyContent,
			String actualContentType, JsonNode actualJsonNodeBody) {
		if (actualJsonNodeBody != null) {
			String typeName;
			MediaType mediaType = operationReqBodyContent.get(actualContentType);
			if (mediaType != null) {
				Schema schema = mediaType.getSchema();
				if (schema != null) {
					String get$ref = schema.get$ref();
					if (get$ref != null) {
						if (get$ref.startsWith(Constants.COMPONENTS_SCHEMA_PREFIX)) {
							// get the schema
							// and drill in to the properties
							// does any have "x-credit-card"
							// return name of the property
							typeName = modelPackageUtil.classnameIfUnderFqnElseSimpleClassNameFromComponentSchemaRef(get$ref);
							Schema theSchema = this.openApi.getComponents().getSchemas().get(typeName);
							processJsonSchema(request, messages, actualJsonNodeBody, typeName, theSchema, "");
						}
					}
				}
			}
		}

	}

	private void processJsonSchema(Request request, List<Message> messages, JsonNode actualJsonNodeBody,
			String typeNameOfActualJsonNodeBodySchema, Schema actualJsonNodeBodySchema, String base) {
		if (actualJsonNodeBodySchema != null) {
			Map<String, Schema> properties = actualJsonNodeBodySchema.getProperties();
			if (properties != null) {
				Set<Entry<String, Schema>> entrySet = properties.entrySet();
				if (entrySet != null) {
					for (Entry<String, Schema> entry : entrySet) {
						String propertyName = entry.getKey();
						Schema propertySchema = entry.getValue();
						Map<String, Object> extensions = propertySchema.getExtensions();
						if (extensions != null) {
							for (String watchedExtensionKey : watchedExtensions) {

								Object watchedExtension = extensions.get(watchedExtensionKey);
								if (watchedExtension != null && watchedExtension instanceof Boolean) {
									Boolean watchedExtensionBool = (Boolean) watchedExtension;
									if (watchedExtensionBool.booleanValue()) {

										onEachwatchedExtensionForAProperty(messages, actualJsonNodeBody,
												typeNameOfActualJsonNodeBodySchema, propertyName, watchedExtensionKey,
												request.getPath(), request.getMethod(), base);

									}
								}
							}

						}

						String get$ref2 = propertySchema.get$ref();
						if (get$ref2 != null) {
							String childTypeName = modelPackageUtil.classnameIfUnderFqnElseSimpleClassNameFromComponentSchemaRef(get$ref2);
							Schema childSchema = this.openApi.getComponents().getSchemas().get(childTypeName);
							JsonNode actualChildjsonBody = actualJsonNodeBody.get(propertyName);
							logger.debug("childTypeName=" + childTypeName + ",childSchema=" + childSchema
									+ ",actualChildjsonBody=" + actualChildjsonBody);
							if (actualChildjsonBody != null) {
								processJsonSchema(request, messages, actualChildjsonBody, childTypeName, childSchema,
										base + "/" + propertyName);
							}
						}
						String type = propertySchema.getType();
						if (type != null) {
							if (type.equals("array")) {
								Schema items = propertySchema.getItems();
								if (items != null) {
									String get$ref = items.get$ref();
									if (get$ref != null) {
										String childArrayTypeName = modelPackageUtil
												.classnameIfUnderFqnElseSimpleClassNameFromComponentSchemaRef(get$ref);
										Schema childArraySchema = this.openApi.getComponents().getSchemas()
												.get(childArrayTypeName);
										JsonNode jsonNode = actualJsonNodeBody.get(propertyName);
										if (jsonNode != null && !(jsonNode instanceof NullNode)) {
											logger.debug("found" + jsonNode.getClass().getName());
											ArrayNode arrayNode = (ArrayNode) jsonNode;
											if (arrayNode != null) {
												for (int i = 0, size = arrayNode.size(); i < size; i++) {
													JsonNode childArrayJsonNode = arrayNode.get(i);
													if (childArrayJsonNode != null) {
														processJsonSchema(request, messages, childArrayJsonNode,
																childArrayTypeName, childArraySchema,
																base + "/" + propertyName + "/" + i);
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

	private Optional<JsonNode> tryToReturn(Body body) {
		Optional<JsonNode> ret;
		try {
			return ret = Optional.of(body.toJsonNode());
		} catch (IOException e) {
			logger.warn("unable to extract jsonnode", e);
			ret = Optional.empty();
		}
		return ret;
	}

	private void onEachwatchedExtensionForAProperty(List<Message> messages, JsonNode jsonNodeBody, String typeName,
			String propertyName, String watchedExtensionKey, String requestPath, Method reqMethod, String base) {
		IValidator iValidator = mappedValidators.get(watchedExtensionKey);
		JsonNode jsonNode = jsonNodeBody.get(propertyName);
		if (jsonNode != null) {
			// this is just a one level deep custome validator
			// its verysimple only for demo purposes
			// can be enhanced later for non string types and container nodes- array node,
			// object node
			if (jsonNode instanceof TextNode) {
				String check = jsonNode.asText();
				ValidationResult result = iValidator.isValid(check);
				if (!result.isValid()) {
					final MessageContext context = MessageContext.create().withRequestPath(requestPath)
							.withRequestMethod(reqMethod).withPointers(base + "/" + propertyName,
									Constants.COMPONENTS_SCHEMA_PREFIX1 + typeName + "/" + propertyName)
							.build();
					Message message = Message.create(watchedExtensionKey, "Property " + propertyName + " is not  valid")
							.withContext(context).build();
					messages.add(message);
				}
			}

		}
	}

	@Override
	public Map<String, String> getCustomSchemaImportMapping() {
		return customSchemasImportMapping;
	}

	public <E> void registerCustomSchema(String customSchema, String importedTypeName) {
		customSchemasImportMapping.put(customSchema, importedTypeName);
	}

	

}

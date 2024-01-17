package io.github.xdamah.controller;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.github.xdamah.config.ModelPackageUtil;
import io.github.xdamah.controller.DamahController.StreamReaderToTarget;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

public class RequestBodyBuilder {
	private static final Logger logger = LoggerFactory.getLogger(RequestBodyBuilder.class);

	public RequestBodyBuilder(Operation operation, ModelPackageUtil modelPackageUtil, ObjectMapper objectMapper,
			MappingJackson2XmlHttpMessageConverter mappingJackson2XmlHttpMessageConverter, OpenAPI openApi,
			ConversionService conversionService) {
		super();
		this.operation = operation;
		this.modelPackageUtil = modelPackageUtil;
		this.objectMapper = objectMapper;
		this.mappingJackson2XmlHttpMessageConverter = mappingJackson2XmlHttpMessageConverter;
		this.openApi = openApi;
		this.conversionService = conversionService;
	}

	private final Operation operation;
	private final ModelPackageUtil modelPackageUtil;
	private final ObjectMapper objectMapper;
	private final MappingJackson2XmlHttpMessageConverter mappingJackson2XmlHttpMessageConverter;
	private final OpenAPI openApi;
	private final ConversionService conversionService;

	private RequestTypeInfo requestTypeInfo = new RequestTypeInfo();

	public void prepareRequestBodyTargetType(String contentType) throws ClassNotFoundException {
		Class<?> targetType = null;
		if (contentType != null) {
			if (contentType.startsWith(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)) {
				contentType = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
			}
			RequestBody requestBody = operation.getRequestBody();

			if (requestBody != null) {
				Content content = requestBody.getContent();
				if (content != null) {
					MediaType mediaType = content.get(contentType);
					if (mediaType != null) {
						Schema schema = mediaType.getSchema();
						if (schema != null) {
							String get$ref = schema.get$ref();
							String type = schema.getType();
							String format = schema.getFormat();
							List<Schema> oneOf = schema.getOneOf();
							if (get$ref != null) {
								logger.debug("get$ref=" + get$ref);

								String classname = modelPackageUtil.simpleClassNameFromComponentSchemaRef(get$ref);

								String fqn = modelPackageUtil.fqn(classname);

								targetType = Class.forName(fqn);

							} else if (type != null) {
								if (type.equals("string")) {
									if (format != null) {
										if (format.equals("byte")) {
											targetType = byte[].class;
										}
									} else {
										targetType = String.class;
									}
								}
							} else if (oneOf != null && oneOf.size() > 0) {
								List<String> refs = new ArrayList<>();
								for (Schema oneOfSchema : oneOf) {
									String oneOfRef = oneOfSchema.get$ref();
									refs.add(oneOfRef);
								}
								this.requestTypeInfo.setOneOfRefs(refs);
								Discriminator discriminator = schema.getDiscriminator();
								if (discriminator != null) {
									String discriminatorPropertyName = discriminator.getPropertyName();
									if (discriminatorPropertyName != null) {
										this.requestTypeInfo.setDiscriminatorPropertyName(discriminatorPropertyName);
									} else {

									}
								} else {
									// must cause
								}

							}
						}
					}
				}
			}
			this.requestTypeInfo.setRequesType(targetType);
		} else {
			// maybe get etc with just parameters and no request body
		}

	}

	public Class<?> getTargetType() {
		Class<?> targetType = null;

		if (this.requestTypeInfo.getRequesType() != null) {
			targetType = this.requestTypeInfo.getRequesType();
		} else {

		}

		return targetType;
	}

	public Object buildRequestBody(HttpServletRequest request, String contentType)
			throws IOException, StreamReadException, DatabindException, AssertionError, ServletException {
		Object reqBody = null;
		if (contentType != null) {
			Class<?> targetType = this.requestTypeInfo.getRequesType();

			final String discriminatorPropertyName = this.requestTypeInfo.getDiscriminatorPropertyName();
			if (targetType != null
					|| (discriminatorPropertyName != null && this.requestTypeInfo.getOneOfRefs() != null)) {
				if (contentType.equals(org.springframework.http.MediaType.APPLICATION_JSON_VALUE)) {
					try (InputStreamReader isr = new InputStreamReader(request.getInputStream());) {
						if (targetType == null) {
							ObjectNode objectNode = objectMapper.readValue(isr, ObjectNode.class);

							JsonNode discriminatorNode = objectNode.get(discriminatorPropertyName);
							if (discriminatorNode != null && discriminatorNode instanceof TextNode) {
								TextNode discriminatorNodeText = (TextNode) discriminatorNode;
								String discriminator = discriminatorNodeText.asText();
								String classSimpleNmae = null;
								for (String ref : this.requestTypeInfo.getOneOfRefs()) {
									if (ref.startsWith(io.github.xdamah.constants.Constants.COMPONENTS_SCHEMA_PREFIX)
											|| ref.startsWith(
													io.github.xdamah.constants.Constants.COMPONENTS_SCHEMA_PREFIX1)) {
										String simpleClassName = modelPackageUtil
												.simpleClassNameFromComponentSchemaRef(ref);
										if (simpleClassName.equals(discriminator)) {
											classSimpleNmae = simpleClassName;
											break;
										}
									}
								}
								if (classSimpleNmae != null) {
									String fqn = modelPackageUtil.fqn(classSimpleNmae);
									try {
										targetType = Class.forName(fqn);
										this.requestTypeInfo.setRequesType(targetType);
									} catch (ClassNotFoundException e) {
										logger.error("class not found", e);
									}
									if (targetType != null) {
										String jsonAsString = objectMapper.writeValueAsString(objectNode);
										reqBody = objectMapper.readValue(jsonAsString, targetType);

										try {
											BeanUtils.setProperty(reqBody, discriminatorPropertyName, discriminator);
										} catch (IllegalAccessException | InvocationTargetException e) {
											logger.error("unable to set property " + discriminatorPropertyName, e);
										}
									}
								}
							}
						} else {
							reqBody = objectMapper.readValue(isr, targetType);
						}

						logger.debug("reqBody=" + reqBody);
					}

				} else if (contentType.equals(org.springframework.http.MediaType.APPLICATION_XML_VALUE)) {
					reqBody = ifStringElse(request, targetType,
							mappingJackson2XmlHttpMessageConverter.getObjectMapper()::readValue);

				} else if (contentType.equals(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE)) {
					Map<String, Schema> schemas = this.openApi.getComponents().getSchemas();
					Schema schema = schemas.get(targetType.getSimpleName());
					FormProcessor formProcessor = new FormProcessor(request, this.modelPackageUtil, this.openApi,
							this.conversionService);
					reqBody = formProcessor.buildForForm(targetType, schema, "");

				} else if (contentType.startsWith(org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)) {
					Map<String, Schema> schemas = this.openApi.getComponents().getSchemas();
					Schema schema = schemas.get(targetType.getSimpleName());
					MultiPartFormProcessor formProcessor = new MultiPartFormProcessor(request, this.modelPackageUtil,
							this.openApi, this.conversionService);
					reqBody = formProcessor.buildForMultiPartForm(targetType, schema, "");

				} else if (contentType.equals(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)) {
					if (targetType != null) {
						if (targetType == byte[].class) {
							try (InputStream inputStream = request.getInputStream();) {
								reqBody = IOUtils.toByteArray(inputStream);
							}

						}
					}

				}

			}
			logger.debug("reqBody=" + reqBody);
		}

		return reqBody;
	}

	private Object ifStringElse(HttpServletRequest request, Class<?> targetType, StreamReaderToTarget f)
			throws IOException, StreamReadException, DatabindException {
		Object reqBody;
		if (targetType != null && targetType == String.class) {
			try (InputStreamReader isr = new InputStreamReader(request.getInputStream());) {
				reqBody = IOUtils.toString(isr);
				logger.debug("reqBody=" + reqBody);
			}

		} else {
			try (InputStreamReader isr = new InputStreamReader(request.getInputStream());) {

				// reqBody
				// =mappingJackson2XmlHttpMessageConverter.getObjectMapper().readValue(isr,
				// targetType);
				reqBody = f.read(isr, targetType);
				logger.debug("reqBody=" + reqBody);
			}
		}
		return reqBody;
	}

}

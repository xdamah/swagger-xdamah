package com.github.xdamah.config;

import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.xdamah.constants.Constants;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NonSpringHolder {
	private static final Logger logger = LoggerFactory.getLogger(NonSpringHolder.class);

	public static final NonSpringHolder INSTANCE = new NonSpringHolder();

	private ObjectMapper objectMapper;
	private ModelPackageUtil modelPackageUtil;
	private MappingJackson2XmlHttpMessageConverter mappingJackson2XmlHttpMessageConverter;
	private OpenAPI openApi;

	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public MappingJackson2XmlHttpMessageConverter getMappingJackson2XmlHttpMessageConverter() {
		return mappingJackson2XmlHttpMessageConverter;
	}

	public void setMappingJackson2XmlHttpMessageConverter(
			MappingJackson2XmlHttpMessageConverter mappingJackson2XmlHttpMessageConverter) {
		this.mappingJackson2XmlHttpMessageConverter = mappingJackson2XmlHttpMessageConverter;
	}

	public ModelPackageUtil getModelPackageUtil() {
		return modelPackageUtil;
	}

	public void setModelPackageUtil(ModelPackageUtil modelPackageUtil) {
		this.modelPackageUtil = modelPackageUtil;
	}

	public JsonNode xmlToJsonNode(final RequestBody apiRequestBodyDefinition, 
			String contentType, String xml) {
		JsonNode readValue = null;
		if(xml!=null)
		{
			if (apiRequestBodyDefinition != null) {
				Content content = apiRequestBodyDefinition.getContent();
				readValue = xmlToJsonNode(content, contentType, xml);

			}

		}
		
		return readValue;
	}
	
	/*
	
	For now using the java model than the opeanapi component schema 
	because of time constraints.
	Will later reimplement using the opeanapi component schema
	We must use the schema or the java model as otherwise there wont be enough 
	information to decide if a property is array or a object
	
	 * 
	 */
	
	public JsonNode xmlToJsonNode(Content content, String contentType, String xml) {
		JsonNode readValue = null;
		if (content != null) {
			if (xml != null) {
				MediaType mediaType = content.get(contentType);
				if (mediaType != null) {
					Schema schema = mediaType.getSchema();
					if (schema != null) {
						String get$ref = schema.get$ref();
						String type = schema.getType();
						
						if(get$ref!=null)
						{
							ModelPackageUtil modelPackageUtil = this.getModelPackageUtil();
							String classname = modelPackageUtil.simpleClassNameFromComponentSchemaRef(get$ref);

							String fqn = modelPackageUtil.fqn(classname);

							try {
								Class targetType = Class.forName(fqn);
								Object target = this.getMappingJackson2XmlHttpMessageConverter()
										.getObjectMapper().readValue(xml, targetType);
								String json = this.getObjectMapper().writeValueAsString(target);
								readValue = this.getObjectMapper().readValue(json, JsonNode.class);

							} catch (ClassNotFoundException | JsonProcessingException e) {
								logger.error("Could not convert xml to json", e);
							}
						}
						else if(type!=null)
						{
							if(type.equals("string"))
							{
								//no idea about schema
								//so whats the best we can do?
							}
							else
							{
								//must investigate
							}
						}
						

					}
				}
			} 

		}
		return readValue;
	}

	public void setOpenApi(OpenAPI openApi) {
		this.openApi = openApi;
	}

}

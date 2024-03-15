package io.github.xdamah.util;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.github.xdamah.config.ICustomSchemaRegisty;
import io.github.xdamah.config.ModelPackageUtil;
import io.github.xdamah.constants.DamahExtns;
import io.github.xdamah.modelconverter.ByteArrayPropertyConverter;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.Json31;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.SpecVersion;
import io.swagger.v3.oas.models.media.Schema;

public class ContainerNodeReaderPathBuilder {
	private static final Logger logger = LoggerFactory.getLogger(ContainerNodeReaderPathBuilder.class);
	private Map<String, ContainerNode> pathContainerNodeMap = new LinkedHashMap<>();
	private Map<String, ArrayNode> parametersMap = new LinkedHashMap<>();

	
	private ModelPackageUtil modelPackageUtil;
	private ICustomSchemaRegisty customSchemaRegistry;

	public ContainerNodeReaderPathBuilder(ModelPackageUtil modelPackageUtil, ICustomSchemaRegisty customSchemaRegistry) {
		super();
		this.modelPackageUtil = modelPackageUtil;
		this.customSchemaRegistry= customSchemaRegistry;
	}

	public Map<String, ArrayNode> getParametersMap() {
		return parametersMap;
	}

	public Map<String, ContainerNode> getPathContainerNodeMap() {
		return pathContainerNodeMap;
	}

	ObjectMapper mapper = Json31.mapper();
	private  void buildModelSchemas(String type, ObjectNode containerNode)
	{
		
		String fqn=modelPackageUtil.fqn(type);
		try {
			Class<?> forName = Class.forName(fqn);
			
			ResolvedSchema resolveAsResolvedSchema = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(forName));
			Map<String, Schema> schemaMap=resolveAsResolvedSchema.referencedSchemas;
			Set<String> keySet = schemaMap.keySet();
			for (String key : keySet) {
				
				boolean forNameIsIncustomSchemaImportMapping=false;
				Map<String, String> customSchemaImportMapping = this.customSchemaRegistry.getCustomSchemaImportMapping();
				if(modelPackageUtil.isForFqn())
				{
					throw new RuntimeException("Implement soon");
				}
				else
				{
					for (String importMappingKey : customSchemaImportMapping.keySet()) {
						if(importMappingKey.equals(key))
						{
							forNameIsIncustomSchemaImportMapping=true;
							break;
						}
					}
				}
				
				Schema schema = schemaMap.get(key);

				try {
					String s = forNameIsIncustomSchemaImportMapping?"{\"type\":\"object\"}":mapper.writerFor(Schema.class).writeValueAsString(schema);
					ObjectNode readValue = mapper.readerFor(ObjectNode.class).readValue(s);
					JsonNode jsonNode = containerNode.get(key);
					//here we ensure that we dont overwrite what is there in the json
					
					if(jsonNode==null)
					{
						containerNode.put(key, readValue);
					}
					
				} catch (JsonProcessingException e) {
					logger.error("JsonProcessingException "+fqn, e);
				}
			}

			
			
		} catch (ClassNotFoundException e) {
			logger.error("ClassNotFound for "+fqn, e);
		}
		
		
	}

	public void buildPathsAndXdamahModels(ContainerNode containerNode, String path) throws IOException {
		
		this.buildModels(containerNode);//dont worry about calling second time this is of use only in first cal

		pathContainerNodeMap.put(path, containerNode);
		if (containerNode instanceof ObjectNode) {

			
			if (containerNode.has(DamahExtns.X_DAMAH_PARAM_TYPE)) {
				final JsonNode jsonNodeParamType = containerNode.get(DamahExtns.X_DAMAH_PARAM_TYPE);
				if (jsonNodeParamType != null && jsonNodeParamType instanceof TextNode) {
					String paramType = jsonNodeParamType.asText();

					final JsonNode parameters = containerNode.get("parameters");
					if(parameters instanceof ArrayNode)
					{
						ArrayNode parametersArr=(ArrayNode) parameters;
						//all good so far
						parametersMap.put(paramType, parametersArr);
					}
				}
				
				
			}

			Iterator<String> fieldNames = containerNode.fieldNames();
			while (fieldNames.hasNext()) {
				String fieldName = fieldNames.next();
				

				JsonNode jsonNode = containerNode.get(fieldName);
				if (jsonNode instanceof ContainerNode) {
					buildPathsAndXdamahModels((ContainerNode) jsonNode, path + "/" + fieldName);
				}
			}

		} else if (containerNode instanceof ArrayNode) {
			ArrayNode arrayNode = (ArrayNode) containerNode;
			int size = arrayNode.size();
			for (int i = 0; i < size; i++) {
				JsonNode jsonNode = arrayNode.get(i);
				if (jsonNode instanceof ContainerNode) {
					buildPathsAndXdamahModels((ContainerNode) jsonNode, path + "[" + i + "]");
				}
			}

		}

	}

	public void buildModels(ContainerNode containerNode) {
		if (containerNode.has(DamahExtns.X_DAMAH_MODELS)) {
			
			
			
			JsonNode jsonNode = containerNode.get(DamahExtns.X_DAMAH_MODELS);
			if(jsonNode !=null)
			{
				if( jsonNode instanceof ArrayNode)
				{
					ArrayNode array=(ArrayNode) jsonNode;
					int size = array.size();
					for (int i = 0; i < size; i++) {
						JsonNode jsonNode2 = array.get(i);
						if(jsonNode2 instanceof TextNode)
						{
							TextNode t=(TextNode) jsonNode2;
							String asText = t.asText();
							if(asText!=null)
							{
								asText=asText.trim();
								buildModelSchemas(asText, (ObjectNode) containerNode);
							}
						}
						else
						{
							
						}
					}
					
					
				}
				else if( jsonNode instanceof TextNode)
				{
					TextNode t=(TextNode) jsonNode;
					String asText = t.asText();
					if(asText!=null)
					{
						asText=asText.trim();
						buildModelSchemas(asText, (ObjectNode) containerNode);
					}


					
				}
				else
				{
					
				}
				
			
			}
			
			((ObjectNode) containerNode).remove(DamahExtns.X_DAMAH_MODELS);
		}
	}
}

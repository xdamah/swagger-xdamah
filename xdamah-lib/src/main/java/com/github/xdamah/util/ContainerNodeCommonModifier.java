package com.github.xdamah.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.xdamah.constants.DamahExtns;

import io.swagger.v3.oas.models.PathItem.HttpMethod;

public class ContainerNodeCommonModifier {

	private Map<String, ContainerNode> pathContainerNodeMap;
	private Map<String, ArrayNode> parametersMap;
	private ResourceLoader resourceLoader;
	private ObjectMapper jsonMapper;

	public ContainerNodeCommonModifier(Map<String, ContainerNode> pathContainerNodeMap, 
			Map<String, ArrayNode> parametersMap, ResourceLoader resourceLoader,
			ObjectMapper jsonMapper) {
		super();
		this.pathContainerNodeMap = pathContainerNodeMap;
		this.parametersMap= parametersMap;
		this.resourceLoader = resourceLoader;
		this.jsonMapper = jsonMapper;
	}

	public void modify(ContainerNode containerNode, String path) throws IOException {

		if (containerNode instanceof ObjectNode) {
			
			Iterator<String> fieldNames = containerNode.fieldNames();
			while (fieldNames.hasNext()) {
				String fieldName = fieldNames.next();

				JsonNode jsonNode = containerNode.get(fieldName);
				if (jsonNode instanceof ContainerNode) {
					modify((ContainerNode) jsonNode, path + "/" + fieldName);
				}
			}
			
			/*
			 * this logic is mirrored at code gen.
			 */
			
			if (containerNode.has(DamahExtns.X_DAMAH_PARAM_REF)) {
				boolean thereAreNoParametrs=false;
				final boolean hasParameters = containerNode.has("parameters");
				if(hasParameters)
				{
					final JsonNode jsonNode = containerNode.get("parameters");
					if(jsonNode!=null)
					{
						if(jsonNode instanceof ArrayNode)
						{
							ArrayNode arrayNode=(ArrayNode) jsonNode;
							if(arrayNode.size()==0)
							{
								//no parameters defined in teh ref
								thereAreNoParametrs=true;
							}
						}
					}
					else
					{
						///?? anyways we cant do this then
					}
				}
				else
				{
					thereAreNoParametrs=true;
				}
				
				
				if (thereAreNoParametrs)
				{
					final JsonNode jsonNodeParamType = containerNode.get(DamahExtns.X_DAMAH_PARAM_REF);
					if (jsonNodeParamType != null && jsonNodeParamType instanceof TextNode) 
					{
						
						String paramType = jsonNodeParamType.asText();
						final ArrayNode arrayNode = parametersMap.get(paramType);
						
						((ObjectNode) containerNode).replace("parameters", arrayNode);
					}
				}
			}

		} else if (containerNode instanceof ArrayNode) {
			ArrayNode arrayNode = (ArrayNode) containerNode;
			int size = arrayNode.size();
			for (int i = 0; i < size; i++) {
				JsonNode jsonNode = arrayNode.get(i);
				if (jsonNode instanceof ContainerNode) {
					modify((ContainerNode) jsonNode, path + "[" + i + "]");
				}
			}

		}

	}

	

	private String isPossiblyAnOperation(String path) {
		String operationMethodType = null;
		if (path.startsWith("/paths/")) {
			HttpMethod[] methods = HttpMethod.values();

			for (HttpMethod httpMethod : methods) {
				String methodNmae = httpMethod.name().toLowerCase();
				if (path.toLowerCase().endsWith(methodNmae)) {
					operationMethodType = methodNmae;
					break;
				}
			}
		}
		return operationMethodType;
	}

	String up(String path) {
		String ret = null;
		if (isNonIndexPath(path)) {
			int index = path.lastIndexOf("/");
			if (index != -1) {
				ret = path.substring(0, index);
			}
		} else {
			int index = path.lastIndexOf("[");
			if (index != -1) {
				ret = path.substring(0, index);
			}
		}
		return ret;
	}

	boolean isNonIndexPath(String path) {
		return !path.endsWith("]");
	}

}

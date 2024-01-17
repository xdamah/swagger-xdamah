package io.github.xdamah.util;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import io.github.xdamah.constants.DamahExtns;

public class ContainerNodeReaderPathBuilder {
	private Map<String, ContainerNode> pathContainerNodeMap = new LinkedHashMap<>();
	private Map<String, ArrayNode> parametersMap = new LinkedHashMap<>();

	public Map<String, ArrayNode> getParametersMap() {
		return parametersMap;
	}

	public Map<String, ContainerNode> getPathContainerNodeMap() {
		return pathContainerNodeMap;
	}

	public void buildPaths(ContainerNode containerNode, String path) throws IOException {

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
					buildPaths((ContainerNode) jsonNode, path + "/" + fieldName);
				}
			}

		} else if (containerNode instanceof ArrayNode) {
			ArrayNode arrayNode = (ArrayNode) containerNode;
			int size = arrayNode.size();
			for (int i = 0; i < size; i++) {
				JsonNode jsonNode = arrayNode.get(i);
				if (jsonNode instanceof ContainerNode) {
					buildPaths((ContainerNode) jsonNode, path + "[" + i + "]");
				}
			}

		}

	}
}

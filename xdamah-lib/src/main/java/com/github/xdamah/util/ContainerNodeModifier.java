package com.github.xdamah.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

public class ContainerNodeModifier {
	private static final Logger logger = LoggerFactory.getLogger(ContainerNodeModifier.class);
	private Map<String, ContainerNode> pathContainerNodeMap;
	private Map<String, ArrayNode> parametersMap;
	private ResourceLoader resourceLoader;
	private ObjectMapper jsonMapper;

	public ContainerNodeModifier(Map<String, ContainerNode> pathContainerNodeMap, 
			Map<String, ArrayNode> parametersMap,
			ResourceLoader resourceLoader,
			ObjectMapper jsonMapper) {
		super();
		this.pathContainerNodeMap = pathContainerNodeMap;
		this.parametersMap=parametersMap;
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
			 * this logic will have to be mirrored at code generation.
			 */
			if (containerNode.has(DamahExtns.X_DAMAH_PARAM_REF)) {
				if (!containerNode.has("parameters"))
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
			if (containerNode.has(DamahExtns.X_DAMAH_SERVICE)) {
				((ObjectNode) containerNode).put(DamahExtns.X_DAMAH_SERVICE, "hidden");
				// ((ObjectNode) containerNode).remove(DamahExtns.X_DAMAH_PARAM_SERVICE);
			} else if (containerNode.has("externalValue") && isNonIndexPath(path)) {
				String up = up(path);
				if (up != null) {

					if (up.endsWith("/examples")) {
						boolean typeIsString = false;
						String up1 = up(up);// the mediatype
						ContainerNode mediaTypecontainerNode = this.pathContainerNodeMap.get(up1);
						if (mediaTypecontainerNode != null) {
							JsonNode schemaNode = mediaTypecontainerNode.get("schema");
							if (schemaNode != null && schemaNode instanceof ContainerNode) {
								ContainerNode schemaContainerNode = (ContainerNode) schemaNode;
								JsonNode typeNode = schemaContainerNode.get("type");
								if (typeNode != null) {
									String type = typeNode.asText();
									if (type.equals("string")) {
										typeIsString = true;

									}
								}
							}
						}
						JsonNode externalValue = containerNode.get("externalValue");
						if (externalValue != null && externalValue instanceof TextNode) {
							String urlText = ((TextNode) externalValue).asText();
							Resource resource = resourceLoader.getResource(urlText);
							if (resource != null) {
								try (InputStream inputStream = resource.getInputStream();) {
									urlText = urlText.toLowerCase();
									if (urlText.endsWith(".json") || urlText.endsWith(".xml")) {
										String text = IOUtils.toString(inputStream, Charset.defaultCharset());
										logger.debug("typeIsString=" + typeIsString);
										if (typeIsString) {
											// tried changing the text conditionally didnt work to any benefit
										}

										((ObjectNode) containerNode).put("value", text);
										((ObjectNode) containerNode).remove("externalValue");
									} else {
										// must base64
										// txt is probably not base64 probably blongs with .json and .xml
										if (urlText.endsWith(".txt")) {
											// can read as text
										} else {
											// just astream
										}

									}
								}
							}

						}
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

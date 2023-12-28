package com.github.xdamah.util;

import java.io.IOException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class ContainerNodeReaderPathBuilder {
	private Map<String, ContainerNode> pathContainerNodeMap= new LinkedHashMap<>();
public Map<String, ContainerNode> getPathContainerNodeMap() {
		return pathContainerNodeMap;
	}
public void buildPaths(ContainerNode containerNode, String path) throws IOException {
		
		pathContainerNodeMap.put(path, containerNode);
		if(containerNode instanceof ObjectNode)
		{

			Iterator<String> fieldNames = containerNode.fieldNames();
			while(fieldNames.hasNext())
			{
				String fieldName = fieldNames.next();
				
			
				JsonNode jsonNode = containerNode.get(fieldName);
				if(jsonNode instanceof ContainerNode)
				{
					buildPaths((ContainerNode) jsonNode, path+"/"+fieldName);
				}
			}
			
			
			
		}
		else if(containerNode instanceof ArrayNode)
		{
			ArrayNode arrayNode=(ArrayNode) containerNode;
			int size = arrayNode.size();
			for (int i = 0; i < size; i++) {
				JsonNode jsonNode = arrayNode.get(i);
				if(jsonNode instanceof ContainerNode)
				{
					buildPaths((ContainerNode) jsonNode, path+"["+i+"]");
				}
			}
			
		}
		
		
	}
}

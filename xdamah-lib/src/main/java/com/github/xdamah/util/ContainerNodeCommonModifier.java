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

import io.swagger.v3.oas.models.PathItem.HttpMethod;

public class ContainerNodeCommonModifier {
	
	private Map<String, ContainerNode> pathContainerNodeMap;
	private ResourceLoader resourceLoader;
	private ObjectMapper jsonMapper;
	public ContainerNodeCommonModifier(Map<String, ContainerNode> pathContainerNodeMap, 
			ResourceLoader resourceLoader,
			 ObjectMapper jsonMapper) {
		super();
		this.pathContainerNodeMap = pathContainerNodeMap;
		
		
		this.resourceLoader = resourceLoader;
		this.jsonMapper=jsonMapper;
	}


	

	public void modify(ContainerNode containerNode, String path) throws IOException {
		
		
		if(containerNode instanceof ObjectNode)
		{
			containerNode=replaceRefOperation(containerNode, path);
			Iterator<String> fieldNames = containerNode.fieldNames();
			while(fieldNames.hasNext())
			{
				String fieldName = fieldNames.next();
				
			
				JsonNode jsonNode = containerNode.get(fieldName);
				if(jsonNode instanceof ContainerNode)
				{
					modify((ContainerNode) jsonNode, path+"/"+fieldName);
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
					modify((ContainerNode) jsonNode, path+"["+i+"]");
				}
			}
			
		}
		
		
	}




	private ContainerNode replaceRefOperation(ContainerNode containerNode, String path) throws IOException {
		ContainerNode ret=containerNode;
		String pathMethodNmae = isPossiblyAnOperation(path);
		
		if(pathMethodNmae!=null)
		{
			if(containerNode.has("operationId")
					&& (containerNode.has("x-magic")||
							containerNode.has("x-magic-param-ref")||
							containerNode.has("x-magic-param-type")||
							containerNode.has("x-magic-service")))
			{
				//defintely an operation and subject to our rules
				if(containerNode.has("$ref"))
				{
					JsonNode jsonNode = containerNode.get("$ref");
					if(jsonNode!=null && jsonNode instanceof TextNode)
					{
						String theRefTarget = jsonNode.asText();
						if(theRefTarget.startsWith("#"))//for now not trying to use other possible targets
						{
							theRefTarget=theRefTarget.substring(1);
							if(isPossiblyAnOperation(theRefTarget)!=null)
							{
								theRefTarget=theRefTarget.replace("~0", "~");
								theRefTarget=theRefTarget.replace("~1", "/");
								//System.out.println("using.theRefTarget="+theRefTarget);
								ContainerNode theTarget=pathContainerNodeMap.get(theRefTarget);
								
								if(theTarget!=null)
								{
								theTarget=theTarget.deepCopy();
								((ObjectNode) theTarget).remove("x-magic-param-type");
								((ObjectNode) containerNode).remove("$ref");
								ContainerNode replacement = new NodeMerger(jsonMapper).merge(containerNode, theTarget);
								if(containerNode.has("x-magic-param-ref"))
								{
									((ObjectNode) theTarget).set("x-magic-param-ref", containerNode.get("x-magic-param-ref"));
								}
								String up = up(path);
								ContainerNode parent = pathContainerNodeMap.get(up);
								if(parent!=null && parent instanceof ObjectNode)//just being safe
								{
									ObjectNode parentObj=(ObjectNode) parent;
									parentObj.set(pathMethodNmae, replacement);
									ret=replacement;
								}
								}
								
							}
						}
					}
					
				}
			}
		}
		return ret;
	}
	
	private String isPossiblyAnOperation(String path) {
		String operationMethodType=null;
		if(path.startsWith("/paths/"))
		{
			HttpMethod[] methods = HttpMethod.values();
			
			for (HttpMethod httpMethod : methods) {
				String methodNmae = httpMethod.name().toLowerCase();
				if(path.toLowerCase().endsWith(methodNmae))
				{
					operationMethodType=methodNmae;
					break;
				}
			}
		}
		return operationMethodType;
	}
	
	String up(String path)
	{
		String ret=null;
		if(isNonIndexPath(path))
		{
			int index=path.lastIndexOf("/");
			if(index!=-1)
			{
				ret=path.substring(0, index);
			}
		}
		else
		{
			int index=path.lastIndexOf("[");
			if(index!=-1)
			{
				ret=path.substring(0, index);
			}
		}
		return ret;
	}
	
	
	boolean isNonIndexPath(String path)
	{
		return !path.endsWith("]");
	}

}

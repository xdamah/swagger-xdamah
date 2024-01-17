package io.github.xdamah.util;

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ContainerNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class NodeMerger {

	private ObjectMapper jsonMapper;

	public NodeMerger(ObjectMapper jsonMapper) {
		super();
		this.jsonMapper = jsonMapper;
	}

	public ContainerNode merge(ContainerNode src, ContainerNode target) throws IOException {
		ObjectReader objectReader = jsonMapper.readerForUpdating(target);
		Object updated = objectReader.readValue(src);

		return (ContainerNode) updated;
	}

}

package io.github.xdamah.util;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.node.ContainerNode;

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

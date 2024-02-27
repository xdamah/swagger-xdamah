package com.atlassian.oai.validator.springmvc;

import java.io.IOException;
import java.nio.charset.Charset;

import com.atlassian.oai.validator.model.Body;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class XdamahBody implements Body {
	private Object reqBody;
	private ObjectMapper objectMapper;

	public XdamahBody(Object reqBody, ObjectMapper objectMapper) {
		super();
		this.reqBody = reqBody;
		this.objectMapper=objectMapper;
	}

	@Override
	public boolean hasBody() {
		return reqBody!=null;
	}

	@Override
	public JsonNode toJsonNode() throws IOException {
		String str=objectMapper.writeValueAsString(reqBody);
		return objectMapper.readValue(str, JsonNode.class);
	}

	@Override
	public String toString(Charset encoding) throws IOException {
		String str=objectMapper.writeValueAsString(reqBody);
		return str;
	}

}

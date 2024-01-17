package io.github.xdamah.controller;

import java.util.List;

public class RequestTypeInfo {

	private Class<?> requesType;
	private List<String> oneOfRefs;
	private String discriminatorPropertyName;

	public List<String> getOneOfRefs() {
		return oneOfRefs;
	}

	public void setOneOfRefs(List<String> oneOfRefs) {
		this.oneOfRefs = oneOfRefs;
	}

	public String getDiscriminatorPropertyName() {
		return discriminatorPropertyName;
	}

	public void setDiscriminatorPropertyName(String discriminatorPropertyName) {
		this.discriminatorPropertyName = discriminatorPropertyName;
	}

	public Class<?> getRequesType() {
		return requesType;
	}

	public void setRequesType(Class<?> requesType) {
		this.requesType = requesType;
	}

	public RequestTypeInfo() {
		super();

	}

}

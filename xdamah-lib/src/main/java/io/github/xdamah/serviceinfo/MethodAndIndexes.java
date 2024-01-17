package io.github.xdamah.serviceinfo;

import java.lang.reflect.Method;

public class MethodAndIndexes {
	private Method method;
	private int reqBodyIndex = -1;
	private int paramIndex = -1;
	private int argArrayLength = -1;
	private Object serviceBean;
	private Class singleParameterTargetType;

	public Class getSingleParameterTargetType() {
		return singleParameterTargetType;
	}

	public void setSingleParameterTargetType(Class singleParameterTargetType) {
		this.singleParameterTargetType = singleParameterTargetType;
	}

	public Object getServiceBean() {
		return serviceBean;
	}

	public void setServiceBean(Object serviceBean) {
		this.serviceBean = serviceBean;
	}

	public int getArgArrayLength() {
		return argArrayLength;
	}

	public void setArgArrayLength(int argArrayLength) {
		this.argArrayLength = argArrayLength;
	}

	public Method getMethod() {
		return method;
	}

	public void setMethod(Method method) {
		this.method = method;
	}

	public int getReqBodyIndex() {
		return reqBodyIndex;
	}

	public void setReqBodyIndex(int reqBodyIndex) {
		this.reqBodyIndex = reqBodyIndex;
	}

	public int getParamIndex() {
		return paramIndex;
	}

	public void setParamIndex(int paramIndex) {
		this.paramIndex = paramIndex;
	}

}

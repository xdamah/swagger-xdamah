package io.github.xdamah.codegen;

import java.lang.reflect.Method;

public class BooleanRet {
	private boolean isBoolean;
	public boolean isBoolean() {
		return isBoolean;
	}
	public void setBoolean(boolean isBoolean) {
		this.isBoolean = isBoolean;
	}
	public Method getBooleanSetterMethod() {
		return booleanSetterMethod;
	}
	public void setBooleanSetterMethod(Method booleanSetterMethod) {
		this.booleanSetterMethod = booleanSetterMethod;
	}
	private Method booleanSetterMethod;

}

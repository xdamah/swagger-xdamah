package io.github.xdamah.param;

import org.springframework.util.ClassUtils;

public class ClassOfSingleParam {

	public static Class<?> getClassOfSingleParamType(String clazzname) throws ClassNotFoundException {
		Class ret = null;
		ret = ClassUtils.forName(clazzname, null);
		return ret;
	}

}

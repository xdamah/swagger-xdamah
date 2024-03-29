package io.github.xdamah.param;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

import io.github.xdamah.controller.DamahController;

public class ParameterProcessorForStrings extends BaseParameterProcessor {

	public ParameterProcessorForStrings(String path, DamahController damahController) {
		super(path, damahController);
	}

	protected Object returnAndUse(String operationParameterName, String src)
			throws IllegalAccessException, InvocationTargetException {

		return src;
	}

	protected List<Object> returnAndUse(String operationParameterName, List<Object> list)
			throws IllegalAccessException, InvocationTargetException {
		return list;
	}

}

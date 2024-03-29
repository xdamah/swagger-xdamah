package io.github.xdamah.param;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.beanutils.BeanUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;

import io.github.xdamah.controller.DamahController;
import io.github.xdamah.util.MyPropertyUtils;

public class ParameterProcessorForWrapperBean extends BaseParameterProcessor {
	private static final Logger logger = LoggerFactory.getLogger(ParameterProcessorForWrapperBean.class);

	private Object paramWrapperBean;
	private Map<String, Type> propertyTypesMap;
	private ConversionService conversionService;

	public ParameterProcessorForWrapperBean(String path, DamahController damahController, Object paramWrapperBean,
			ConversionService conversionService, Map<String, Type> propertyTypesMap) {
		super(path, damahController);
		this.paramWrapperBean = paramWrapperBean;
		this.conversionService = conversionService;
		this.propertyTypesMap = propertyTypesMap;
	}

	protected Object returnAndUse(String operationParameterName, String src)
			throws IllegalAccessException, InvocationTargetException {
		if (operationParameterName.equals("x")) {
			logger.debug("here");
		}
		Object ret = null;
		if (paramWrapperBean != null && src != null) {
			Type propertyType = propertyTypesMap.get(operationParameterName);
			if (propertyType != null) {
				ret = this.conversionService.convert(src, (Class<?>) propertyType);
				BeanUtils.setProperty(paramWrapperBean, operationParameterName, ret);
			}

		}

		return ret;
	}

	protected List<Object> returnAndUse(String operationParameterName, List<Object> list)
			throws IllegalAccessException, InvocationTargetException {
		List<Object> ret = null;

		if (paramWrapperBean != null && list != null) {
			Type propertyType = propertyTypesMap.get(operationParameterName);
			if (propertyType != null) {
				Class rawType = MyPropertyUtils.getRawType(propertyType);
				// List is what we are currently getting
				if (List.class.isAssignableFrom(rawType)) {
					Class listItemType = MyPropertyUtils.getListItemType(propertyType);
					if (listItemType != null) {
						List<Object> parameterValsList = new ArrayList<>();
						for (Object parameterVal : list) {
							Object converted = this.conversionService.convert((String) parameterVal, listItemType);
							parameterValsList.add(converted);
						}
						BeanUtils.setProperty(paramWrapperBean, operationParameterName, parameterValsList);
						ret = parameterValsList;
					}
				}
			}

		}
		return ret;
	}

}

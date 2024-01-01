package com.github.xdamah.param;

import java.util.List;

import org.springframework.core.convert.ConversionService;

import io.swagger.v3.oas.models.parameters.Parameter;

/*
 * //paramWrapperBean must be a string or list of strings
				//at this stage can convert using swagger metadata or just depend on service arg type
				//and give no meaning to swagger metadata
				//lets convert to targettype
				 //but also warn if type is different from wghats in swagger specs
				 might be useful to refr to target type also
 */
public class SingleParamConverter {

	private ConversionService conversionService;

	public void setConversionService(ConversionService conversionService) {
		this.conversionService = conversionService;
	}

	public Object convertToTypeDForSingleParam(String data, Parameter refOperationParameterIfThereIsASingleParameter,
			Class singleParameterTargetType) {

		return conversionService.convert(data, singleParameterTargetType);
	}

	public Object convertToTypeDForSingleParam(List<String> data,
			Parameter refOperationParameterIfThereIsASingleParameter, Class singleParameterTargetType) {
		return conversionService.convert(data, singleParameterTargetType);
	}

}

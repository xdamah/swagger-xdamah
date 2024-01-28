package io.github.xdamah.controller;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;

import io.github.xdamah.config.ModelPackageUtil;
import io.github.xdamah.util.MyPropertyUtils;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.*;

public class FormProcessor {
	private static final Logger logger = LoggerFactory.getLogger(FormProcessor.class);

	private final ModelPackageUtil modelPackageUtil;
	private final OpenAPI openApi;
	private final ConversionService conversionService;
	private final HttpServletRequest request;

	public FormProcessor(HttpServletRequest request, ModelPackageUtil modelPackageUtil, OpenAPI openApi,
			ConversionService conversionService) {
		super();
		this.request = request;
		this.modelPackageUtil = modelPackageUtil;
		this.openApi = openApi;
		this.conversionService = conversionService;
	}

	public Object buildForForm(Class<?> targetType, Schema schema, String path) throws AssertionError {

		Object reqBody = null;
		try {
			reqBody = targetType.getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			logger.error("Unable to instantiate", e);
		}
		if (reqBody != null) {
			Map<String, Type> propertyTypesMap = MyPropertyUtils.buildPropertyTypes(reqBody.getClass());
			Map<String, Schema> properties = schema.getProperties();
			Set<String> keySet = properties.keySet();
			for (String propertyName : keySet) {
				Schema schema2 = properties.get(propertyName);
				String type = schema2.getType();
				String format = schema2.getFormat();
				String itemsType = null;
				String itemsFormat = null;
				boolean isArray = false;
				if (type != null) {
					if (type.equals("array")) {
						isArray = true;
						Schema items = schema2.getItems();
						if (items != null) {
							itemsType = items.getType();
							itemsFormat = items.getFormat();
						}
					}

				} else

				{
					String get$ref = schema2.get$ref();
					if (get$ref != null) {
						boolean entrChildProperty = false;
						Enumeration<String> parameterNames = request.getParameterNames();
						while (parameterNames.hasMoreElements()) {
							String parameterName = parameterNames.nextElement();
							if (parameterName.startsWith(path + propertyName + ".")) {
								entrChildProperty = true;
							}
						}
						if (entrChildProperty) {
							String childTypeName = modelPackageUtil.classnameIfUnderFqnElseSimpleClassNameFromComponentSchemaRef(get$ref);
							Schema childSchema = this.openApi.getComponents().getSchemas().get(childTypeName);
							String fqn = modelPackageUtil.fqn(childTypeName);

							try {
								Class childTargetType = Class.forName(fqn);

								Object child = buildForForm(childTargetType, schema, path + propertyName + ".");
								if (child != null) {
									BeanUtils.setProperty(reqBody, propertyName, child);

								}

							} catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException e) {
								logger.error("unable to set property " + propertyName, e);
							}
						}

					}
				}
				if (isArray) {
					String usesArraItemRefNotType = null;
					if (schema2 instanceof ArraySchema) {
						ArraySchema arraySchema = (ArraySchema) schema2;
						Schema<?> items = arraySchema.getItems();
						if (items != null) {
							if (items.getType() == null) {
								String get$ref = items.get$ref();
								if (get$ref != null) {
									usesArraItemRefNotType = get$ref;
								}
							}
						}
					}
					if (usesArraItemRefNotType != null) {
						Enumeration<String> parameterNames = request.getParameterNames();
						int maxIndex = -1;
						while (parameterNames.hasMoreElements()) {
							String parameterName = parameterNames.nextElement();
							String check = path + propertyName + "[";
							if (parameterName.startsWith(check)) {
								int indexOfFirstRightSquareBcket = parameterName.indexOf("].", check.length());
								if (indexOfFirstRightSquareBcket != -1) {
									String bracketContent = parameterName.substring(check.length(),
											indexOfFirstRightSquareBcket);
									int index = -1;
									try {
										index = Integer.parseInt(bracketContent);
									} catch (NumberFormatException e) {
										// not a valid index
									}
									if (index != -1) {
										maxIndex = Math.max(maxIndex, index);
									}
								}
							}
						}
						if (maxIndex != -1) {
							String childTypeName = modelPackageUtil
									.classnameIfUnderFqnElseSimpleClassNameFromComponentSchemaRef(usesArraItemRefNotType);
							Schema childSchema = this.openApi.getComponents().getSchemas().get(childTypeName);
							String fqn = modelPackageUtil.fqn(childTypeName);
							Class childTargetType = null;
							try {
								childTargetType = Class.forName(fqn);
							} catch (ClassNotFoundException e) {
								logger.error("class not found " + fqn, e);
							}
							if (childTargetType != null) {
								Object array = Array.newInstance(childTargetType, maxIndex + 1);

								for (int i = 0; i <= maxIndex; i++) {
									String childPath = path + propertyName + "[" + i + "].";
									Object child = buildForForm(childTargetType, schema, childPath);

									Array.set(array, i, child);
								}
								try {
									List list= new ArrayList<>();
									for (int i = 0; i <= maxIndex; i++) {
										list.add(Array.get(array, i));
									}
									
									BeanUtils.setProperty(reqBody, propertyName, list);
								} catch (IllegalAccessException | InvocationTargetException e) {
									logger.error("unable to set property " + propertyName, e);
								}
							}

						}
					} else {
						String[] parameterVals = request.getParameterValues(path + propertyName);

						if (parameterVals != null) {
							Type propertyType = propertyTypesMap.get(propertyName);
							if (propertyType != null) {
								Class rawType = MyPropertyUtils.getRawType(propertyType);
								// List is what we are currently getting
								if (List.class.isAssignableFrom(rawType)) {
									Class listItemType = MyPropertyUtils.getListItemType(propertyType);
									if (listItemType != null) {
										List<Object> parameterValsList = new ArrayList<>();
										for (String parameterVal : parameterVals) {
											Object converted = null;
											if (itemsType != null && itemsType.equals("string") && itemsFormat != null
													&& itemsFormat.equals("byte")) {
												byte[] byteArray = Base64.getDecoder().decode(parameterVal);
												converted = byteArray;
											} else if (itemsType != null && itemsType.equals("string")
													&& itemsFormat != null && itemsFormat.equals("binary")) {
												byte[] byteArray = Base64.getDecoder().decode(parameterVal);
												Resource res = new ByteArrayResource(byteArray);
												converted = res;
											} else {
												converted = this.conversionService.convert(parameterVal, listItemType);
											}

											parameterValsList.add(converted);
										}
										try {
											BeanUtils.setProperty(reqBody, propertyName, parameterValsList);

										} catch (IllegalAccessException | InvocationTargetException e) {
											logger.error("unable to set property " + propertyName, e);
										}
									}

								} else {
									throw new AssertionError("Unexpected else");
								}

							}

						}
					}

				} else {
					String parameterVal = request.getParameter(path + propertyName);
					if (parameterVal != null) {

						Type propertyType = propertyTypesMap.get(propertyName);
						if (propertyType != null) {
							Object converted = null;
							if (type != null && type.equals("string") && format != null && format.equals("byte")) {
								byte[] byteArray = Base64.getDecoder().decode(parameterVal);
								converted = byteArray;
							} else if (type != null && type.equals("string") && format != null
									&& format.equals("binary")) {
								byte[] byteArray = Base64.getDecoder().decode(parameterVal);
								Resource res = new ByteArrayResource(byteArray);
								converted = res;
							} else {
								converted = this.conversionService.convert(parameterVal, (Class<?>) propertyType);
							}

							try {
								BeanUtils.setProperty(reqBody, propertyName, converted);

							} catch (IllegalAccessException | InvocationTargetException e) {
								logger.error("unable to set property " + propertyName, e);
							}

						}

					}

				}

				logger.debug("---------propertyName=" + propertyName + ",type=" + type);
			}
		}

		return reqBody;
	}

}

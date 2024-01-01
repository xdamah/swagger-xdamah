package com.github.xdamah.controller;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.*;
import java.util.Set;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.ConversionService;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Schema;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import com.github.xdamah.config.ModelPackageUtil;
import com.github.xdamah.util.MyPropertyUtils;

public class MultiPartFormProcessor {
	private static final Logger logger = LoggerFactory.getLogger(MultiPartFormProcessor.class);

	private final ModelPackageUtil modelPackageUtil;
	private final OpenAPI openApi;
	private final ConversionService conversionService;
	private final HttpServletRequest request;
	private final Map<String, List<Part>> requestPartsMap = new LinkedHashMap<>();
	private final Set<String> parameterNames;

	public MultiPartFormProcessor(HttpServletRequest request, ModelPackageUtil modelPackageUtil, OpenAPI openApi,
			ConversionService conversionService) throws IOException, ServletException {
		super();
		this.request = request;
		this.modelPackageUtil = modelPackageUtil;
		this.openApi = openApi;
		this.conversionService = conversionService;
		Collection<Part> parts = request.getParts();
		for (Part part : parts) {
			String parameterName = part.getName();
			List<Part> list = requestPartsMap.get(parameterName);
			if (list == null) {
				list = new ArrayList<Part>();
				requestPartsMap.put(parameterName, list);
			}
			list.add(part);
		}
		this.parameterNames = requestPartsMap.keySet();
	}

	public Object buildForMultiPartForm(Class<?> targetType, Schema schema, String path)
			throws AssertionError, IOException, ServletException {

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

						for (String parameterName : parameterNames) {

							if (parameterName.startsWith(path + propertyName + ".")) {
								entrChildProperty = true;
							}
						}

						if (entrChildProperty) {
							String childTypeName = modelPackageUtil.simpleClassNameFromComponentSchemaRef(get$ref);
							Schema childSchema = this.openApi.getComponents().getSchemas().get(childTypeName);
							String fqn = modelPackageUtil.fqn(childTypeName);

							try {
								Class childTargetType = Class.forName(fqn);

								Object child = buildForMultiPartForm(childTargetType, schema,
										path + propertyName + ".");
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

						int maxIndex = -1;
						for (String parameterName : parameterNames) {

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
									.simpleClassNameFromComponentSchemaRef(usesArraItemRefNotType);
							Schema childSchema = this.openApi.getComponents().getSchemas().get(childTypeName);
							String fqn = modelPackageUtil.fqn(childTypeName);
							Class childTargetType = null;
							try {
								childTargetType = Class.forName(fqn);
							} catch (ClassNotFoundException e) {
								logger.error("class not found", e);
							}
							if (childTargetType != null) {
								Object array = Array.newInstance(childTargetType, maxIndex + 1);

								for (int i = 0; i <= maxIndex; i++) {
									String childPath = path + propertyName + "[" + i + "].";
									Object child = buildForMultiPartForm(childTargetType, schema, childPath);

									Array.set(array, i, child);
								}
								try {
									BeanUtils.setProperty(reqBody, propertyName, Arrays.asList(array));
								} catch (IllegalAccessException | InvocationTargetException e) {
									logger.error("unable to set property " + propertyName, e);
								}
							}

						}
					} else {
						Object[] parameterVals = getRequestParameterValues(path, propertyName, itemsFormat);

						if (parameterVals != null) {
							Type propertyType = propertyTypesMap.get(propertyName);
							if (propertyType != null) {
								Class rawType = MyPropertyUtils.getRawType(propertyType);
								// List is what we are currently getting
								if (List.class.isAssignableFrom(rawType)) {
									Class listItemType = MyPropertyUtils.getListItemType(propertyType);
									if (listItemType != null) {
										List<Object> parameterValsList = new ArrayList<>();
										for (Object parameterVal : parameterVals) {
											if (itemsType != null && itemsType.equals("string") && itemsFormat != null
													&& (itemsFormat.equals("byte") || itemsFormat.equals("binary"))) {
												// byte[] parameterValAsBytearray=(byte[])parameterVal;//not needed but
												// conveys
												parameterValsList.add(parameterVal);
											} else {
												Object converted = this.conversionService.convert(parameterVal,
														listItemType);
												parameterValsList.add(converted);
											}

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
					if (type != null && type.equals("string") && format != null
							&& (format.equals("byte") || format.equals("binary"))) {
						Object parameterVal = getRequestParamterValue(path, propertyName, format);
						if (parameterVal != null) {

							Type propertyType = propertyTypesMap.get(propertyName);
							if (propertyType != null) {

								try {
									BeanUtils.setProperty(reqBody, propertyName, parameterVal);

								} catch (IllegalAccessException | InvocationTargetException e) {
									logger.error("unable to set property " + propertyName, e);
								}

							}

						}
					}

					else {
						String parameterVal = (String) getRequestParamterValue(path, propertyName, null);
						if (parameterVal != null) {

							Type propertyType = propertyTypesMap.get(propertyName);
							if (propertyType != null) {
								Object converted = this.conversionService.convert(parameterVal,
										(Class<?>) propertyType);

								try {
									BeanUtils.setProperty(reqBody, propertyName, converted);

								} catch (IllegalAccessException | InvocationTargetException e) {
									logger.error("unable to set property " + propertyName, e);
								}

							}

						}
					}

				}

				logger.debug("---------propertyName=" + propertyName + ",type=" + type);
			}
		}

		return reqBody;
	}

	/**
	 * if contentType was found in the part this method will return byte[] else
	 * string
	 * 
	 * @param path
	 * @param propertyName
	 * @return
	 * @throws IOException
	 */
	private Object getRequestParamterValue(String path, String propertyName, String format) throws IOException {
		Object ret = null;
		String parameterName = path + propertyName;
		List<Part> partsList = requestPartsMap.get(parameterName);
		if (partsList != null && partsList.size() != 0) {
			Part part = partsList.get(0);
			if (part != null) {
				String contentType = part.getContentType();
				if (contentType != null) {

					InputStream inputStream = part.getInputStream();
					if (format.equals("byte")) {
						ret = IOUtils.toByteArray(inputStream);
					} else if (format.equals("binary")) {

						Resource r = new InputStreamResource(inputStream) {

							@Override
							public String getFilename() {

								return part.getSubmittedFileName();
							}

						};

						ret = r;
					} else {
						// unexpected
					}

				} else {
					if (format != null) {
						// unexpected
					}
					ret = request.getParameter(parameterName);
				}
			}

		}
		return ret;
	}

	private Object[] getRequestParameterValues(String path, String propertyName, String format) throws IOException {
		Object[] ret = null;
		String parameterName = path + propertyName;
		List<Part> partsList = requestPartsMap.get(parameterName);
		List<Integer> unsedIndexes = new ArrayList<>();
		if (partsList != null) {
			int partListsize = partsList.size();
			if (partListsize > 0) {
				ret = new Object[partListsize];
				for (int i = 0, size = partListsize; i < size; i++) {
					Part part = partsList.get(i);
					if (part != null) {
						String contentType = part.getContentType();
						if (contentType != null) {
							InputStream inputStream = part.getInputStream();
							if (format.equals("byte")) {
								byte[] byteArray = IOUtils.toByteArray(inputStream);
								ret[i] = byteArray;
							} else if (format.equals("binary")) {
								Resource r = new InputStreamResource(inputStream) {

									@Override
									public String getFilename() {

										return part.getSubmittedFileName();
									}

								};

								ret[i] = r;
							}

						} else {
							unsedIndexes.add(i);
						}
					}
				}
			}

		}
		if (unsedIndexes.size() > 0) {
			String[] parameterValues = request.getParameterValues(parameterName);
			for (int i = 0, size = unsedIndexes.size(); i < size; i++) {
				Integer index = unsedIndexes.get(i);
				// not expecting below to go wrong but then just in case
				if (i < parameterValues.length) {
					ret[index] = parameterValues[i];
				}

			}
		}
		return ret;
	}

}

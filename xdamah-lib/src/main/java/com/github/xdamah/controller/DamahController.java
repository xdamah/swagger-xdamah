package com.github.xdamah.controller;

import java.beans.PropertyDescriptor;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URLConnection;
import java.util.*;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.beanutils.PropertyUtilsBean;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.function.ServerRequest.Headers;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Discriminator;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.github.xdamah.config.ModelPackageUtil;
import com.github.xdamah.constants.Constants;
import com.github.xdamah.constants.DamahExtns;
import com.github.xdamah.param.ClassOfSingleParam;
import com.github.xdamah.param.ParameterProcessorForStrings;
import com.github.xdamah.param.ParameterProcessorForWrapperBean;
import com.github.xdamah.param.SingleParamConverter;
import com.github.xdamah.serviceinfo.MethodAndIndexes;
import com.github.xdamah.util.MyPropertyUtils;

@RestController
public class DamahController {
	private static final Logger logger = LoggerFactory.getLogger(DamahController.class);
	private final SingleParamConverter singleParamConverter = new SingleParamConverter();
	private ModelPackageUtil modelPackageUtil;

	private ConversionService conversionService;
	private ApplicationContext context;

	private MappingJackson2XmlHttpMessageConverter mappingJackson2XmlHttpMessageConverter;
	private ObjectMapper objectMapper;
	private String path;
	private OpenAPI openApi;

	public void setOpenApi(OpenAPI openApi) {
		this.openApi = openApi;
	}

	private HttpMethod httpMethod;
	private PathItem pathItem;
	private Operation operation;
	private boolean isWebHook;

	// might need to disable caching if we need this to work with polymorphism later

	public ResponseEntity handleRequest(HttpServletRequest request, HttpServletResponse response) throws IOException,
			ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException,
			InvocationTargetException, NoSuchMethodException, SecurityException, AssertionError, ServletException {
		logger.debug("debug handling for path=" + path);

		String contentType = request.getContentType();

		RequestBodyBuilder requestBodyBuilder = new RequestBodyBuilder(operation, modelPackageUtil, objectMapper,
				mappingJackson2XmlHttpMessageConverter, openApi, conversionService);

		requestBodyBuilder.prepareRequestBodyTargetType(contentType);
		Object reqBody = requestBodyBuilder.buildRequestBody(request, contentType);

		Object paramWrapperBean = null;
		Parameter refOperationParameterIfThereIsASingleParameter = null;

		List<Parameter> operationParameters = operation.getParameters();
		Class paramClass = null;
		Boolean isArraySingleParam = null;
		if (operationParameters != null && operationParameters.size() > 1) {
			String typename = getTypeName();

			String classname = modelPackageUtil.fqn(typename);
			Class paramwrapperClass = Class.forName(classname);
			paramClass = paramwrapperClass;
			// must cache this wrapper class also
			paramWrapperBean = paramwrapperClass.getDeclaredConstructor().newInstance();
			if (typename != null && paramWrapperBean != null) {
				Map<String, Type> propertyTypesMap = MyPropertyUtils.buildPropertyTypes(paramwrapperClass);
				for (Parameter operationParameter : operationParameters) {
					// we are here not interested in whatWasSet
					Object whatWasSet = new ParameterProcessorForWrapperBean(this.path, this, paramWrapperBean,
							this.conversionService, propertyTypesMap).process(request, operationParameter);
				}

			} else {
				logger.error("Unexpected cannotr determine the warpper type of parameters");
			}
		} else if (operationParameters != null && operationParameters.size() == 1) {
			// lets handle this little later
			// only one parameter no warrper tpe exists
			// more work when its not a bean
			Parameter parameter = operationParameters.get(0);

			isArraySingleParam = isArray(parameter);
			paramClass = isArraySingleParam ? List.class : String.class;

			//
			refOperationParameterIfThereIsASingleParameter = parameter;
			paramWrapperBean = new ParameterProcessorForStrings(this.path, this).process(request, parameter);

		}
		ApiResponses responses = operation.getResponses();
		Set<String> responseKeySet = responses.keySet();
		ApiResponse apiResponseFor200Or201 = null;
		String statusCodeOf200Or201 = null;
		for (String responseStatusCode : responseKeySet) {
			ApiResponse apiResponse = responses.get(responseStatusCode);
			if (responseStatusCode.equals("200") || responseStatusCode.equals("201")) {
				statusCodeOf200Or201 = responseStatusCode;
				apiResponseFor200Or201 = apiResponse;
				break;
			}

			// logger.debug("responseStatusCode="+responseStatusCode+apiResponse);
		}

		logger.debug("********Invoked with reqBody=" + reqBody + ", paramWrapperBean=" + paramWrapperBean
				+ ",refOperationParameterIfThereIsASingleParameter=" + refOperationParameterIfThereIsASingleParameter);
		if (methodAndIndexes == null) {
			methodAndIndexes = getMethod(requestBodyBuilder.getTargetType(),
					refOperationParameterIfThereIsASingleParameter, paramClass);
		}
		Object ret = null;
		if (methodAndIndexes != null) {
			Object[] args = new Object[methodAndIndexes.getArgArrayLength()];
			if (methodAndIndexes.getReqBodyIndex() != -1) {
				args[methodAndIndexes.getReqBodyIndex()] = reqBody;
			}
			if (refOperationParameterIfThereIsASingleParameter != null) {
				// must convert here
				if (methodAndIndexes.getParamIndex() != -1) {
					Class singleParameterTargetType = methodAndIndexes.getSingleParameterTargetType();
					// paramWrapperBean must be a string or list of strings
					// at this stage can convert using swagger metadata or just depend on service
					// arg type
					// and give no meaning to swagger metadata
					// no lets first convert to swagger type
					if (paramWrapperBean != null) {
						if (isArraySingleParam != null) {
							Object converted = null;
							if (isArraySingleParam) {
								converted = singleParamConverter.convertToTypeDForSingleParam(
										(List<String>) paramWrapperBean, refOperationParameterIfThereIsASingleParameter,
										singleParameterTargetType);
							} else {
								converted = singleParamConverter.convertToTypeDForSingleParam((String) paramWrapperBean,
										refOperationParameterIfThereIsASingleParameter, singleParameterTargetType);
							}
							args[methodAndIndexes.getParamIndex()] = converted;
						}

					}

				}
			} else {
				if (methodAndIndexes.getParamIndex() != -1) {
					args[methodAndIndexes.getParamIndex()] = paramWrapperBean;
				}
			}
			Method method = methodAndIndexes.getMethod();
			
			if (method != null) {

				try {
					ret = method.invoke(methodAndIndexes.getServiceBean(), args);

				} catch (Exception e) {
					throw e;
				}

			}
		}


		String contentTypeTouse = null;
		if (apiResponseFor200Or201 != null) {

			Set<String> acceptHeaders = acceptHeadersAsSet(request);

			contentTypeTouse = getResponseContentTypeToUse(apiResponseFor200Or201, ret, acceptHeaders);

			if (contentTypeTouse != null && contentTypeTouse.equals("*/*")) {
				// instead lets guess
				if (ret instanceof Resource) {
					Resource res = (Resource) ret;
					InputStream inputStream = res.getInputStream();
					// we have a stream but not yet read it
					if (inputStream.markSupported()) {
						contentTypeTouse = URLConnection.guessContentTypeFromStream(inputStream);
					} else {
						// if we read the stream cant be read again
						// can conert to say a byteaarraystream and a byteArrayResource
						// but if contentType was so important declare it properly
						// in the swagger
					}

				} else {
					// should we check first
					contentTypeTouse = org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
				}

			}
		}
		if (contentTypeTouse == null) {
			// why default
			// contentTypeTouse=org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
		}
		ResponseEntity responseEntity = null;
		HttpHeaders headers = new HttpHeaders();
		if (contentTypeTouse != null) {
			headers.set(HttpHeaders.CONTENT_TYPE, contentTypeTouse);
		}

		if (ret == null) {

			responseEntity = new ResponseEntity<>(headers, HttpStatus.valueOf(Integer.parseInt(statusCodeOf200Or201)));
		} else {
			responseEntity = new ResponseEntity<>(ret, headers,
					HttpStatus.valueOf(Integer.parseInt(statusCodeOf200Or201)));
		}
		return responseEntity;

	}

	private Set<String> acceptHeadersAsSet(HttpServletRequest request) {
		Enumeration<String> headers = request.getHeaders(HttpHeaders.ACCEPT);

		Set<String> headersSet = new LinkedHashSet<>();
		while (headers.hasMoreElements()) {
			headersSet.add(headers.nextElement());
		}
		return headersSet;
	}

	private String getResponseContentTypeToUse(ApiResponse apiResponseFor200Or201, Object ret,
			Set<String> acceptHeaders) throws IOException {
		String contentTypeTouse = null;
		Content content = apiResponseFor200Or201.getContent();
		if (content != null) {
			Set<String> contentKeySet = content.keySet();
			if (contentKeySet != null) {
				if (contentKeySet.size() == 1)// only 1 is specified
				{
					contentTypeTouse = contentKeySet.iterator().next();
					// no need to look elsewhere
					// but should we check if this is correct or not
					// must test by trying with wrong types to see what happens
				} else {
					boolean allAreFiles = allAreFiles(content, contentKeySet);
					if (allAreFiles) {
						if (ret instanceof Resource) {
							Resource res = (Resource) ret;
							InputStream inputStream = res.getInputStream();
							// we have a stream but not yet read it
							if (inputStream.markSupported()) {
								contentTypeTouse = URLConnection.guessContentTypeFromStream(inputStream);
							}
							if (contentTypeTouse != null) {
								if (!contentKeySet.contains(contentTypeTouse)) {
									// lets try even though its not in listed
									// if thats not ok we must set i to null again here
								}

							} else {
								// could not detect
								// lets not bother
							}

						} else {
							// raise warnings
							// also inform what was got for ret
						}
					} else {
						// below is if you must match with muliple and find a schema
						// but must also match with accepts
						for (String responseContentType : contentKeySet) {

							String responseRefType = buildResponseRefType(content, responseContentType);
							if (responseRefType != null && ret != null) {
								if (responseRefType
										.equals(Constants.COMPONENTS_SCHEMA_PREFIX + ret.getClass().getSimpleName())) {
									if (matchAcceptsHeadersWithRespCntentType(acceptHeaders, responseContentType)) {
										contentTypeTouse = responseContentType;
										break;
									}

								}

							}

						}
						if (contentTypeTouse == null)// no match so far
						{
							// same logic as previous for loop
							// just ignoring accept
							for (String responseContentType : contentKeySet) {

								String responseRefType = buildResponseRefType(content, responseContentType);
								if (responseRefType != null && ret != null) {
									if (responseRefType.equals(
											Constants.COMPONENTS_SCHEMA_PREFIX + ret.getClass().getSimpleName())) {

										contentTypeTouse = responseContentType;
										break;

									}

								}

							}
						}
					}

				}

			}

		}
		return contentTypeTouse;
	}

	private boolean matchAcceptsHeadersWithRespCntentType(Set<String> acceptHeaders, String responseContentType) {
		boolean ret = false;
		if (acceptHeaders.contains("*/*")) {
			ret = true;
		} else {
			ret = acceptHeaders.contains(responseContentType);
		}
		return ret;
	}

	private boolean allAreFiles(Content content, Set<String> contentKeySet) {
		boolean allAreFiles = false;
		int oks = 0;
		for (String responseContentType : contentKeySet) {
			MediaType mediaType = content.get(responseContentType);
			if (mediaType != null) {
				Schema schema = mediaType.getSchema();
				if (schema != null) {
					String type = schema.getType();
					String format = schema.getFormat();
					if (type != null && type.equals("string") && format != null
							&& (format.equals("binary") || format.equals("byte"))) {
						oks++;
					}
				}
			}

		}
		if (oks == contentKeySet.size()) {
			allAreFiles = true;
		}
		return allAreFiles;
	}

	private String buildResponseRefType(Content content, String responseContentType) {
		String responseRefType = null;
		MediaType mediaType = content.get(responseContentType);
		if (mediaType != null) {
			Schema schema = mediaType.getSchema();
			if (schema != null) {
				String get$ref = schema.get$ref();
				if (get$ref != null) {
					responseRefType = get$ref;
					// logger.debug("get$ref="+get$ref);
				}

			}
		}
		return responseRefType;
	}

	private MethodAndIndexes getMethod(Class<?> requestBodyTargetType,
			Parameter refOperationParameterIfThereIsASingleParameter, Class paramClass)
			throws ClassNotFoundException, NoSuchMethodException {
		MethodAndIndexes methodAndIndexes = new MethodAndIndexes();
		Map<String, Object> extensions = operation.getExtensions();

		if (extensions != null) {
			String serviceInfo = (String) extensions.get(DamahExtns.X_DAMAH_SERVICE);
			if (serviceInfo != null) {
				serviceInfo = serviceInfo.trim();
				if (serviceInfo.endsWith(")")) {
					int openBraceIndex = serviceInfo.indexOf("(");
					// servicePlusMethodNmae doesnt include the openBrace
					String servicePlusMethodNmae = serviceInfo.substring(0, openBraceIndex);
					int lastIndexOfDot = servicePlusMethodNmae.lastIndexOf(".");
					String serviceClassBeanName = servicePlusMethodNmae.substring(0, lastIndexOfDot);
					Object serviceBean = null;
					try
					{
						serviceBean=context.getBean(serviceClassBeanName);
					}
					catch(NoSuchBeanDefinitionException e)
					{
						logger.error("Unexpected configuration- bean: "+serviceClassBeanName+" not found", e);
						methodAndIndexes=null;
						return methodAndIndexes;
					}
					if(serviceBean!=null)
					{
						String methodName = servicePlusMethodNmae.substring(lastIndexOfDot + 1);
						String argTypesSection = serviceInfo.substring(openBraceIndex + 1, serviceInfo.length() - 1);// we
																														// dont
																														// want
																														// closing
																														// brace
																														// or
																														// openingbrace
						argTypesSection = argTypesSection.trim();
						String[] argTypeNames = argTypesSection.split(",");
						for (int i = 0; i < argTypeNames.length; i++) {
							argTypeNames[i] = argTypeNames[i].trim();
						}
						if (argTypeNames.length <= 2) {
							methodAndIndexes.setArgArrayLength(argTypeNames.length);
							Class[] argTypes = new Class[argTypeNames.length];
							int bodyArgIndex = -1;
							if (argTypeNames.length == 2) {
								// OneOfTripRequestsItems
								// FlightRequest

								if (isActualTargetAssignableToArgType(argTypeNames[0], requestBodyTargetType)) {
									bodyArgIndex = 0;
									methodAndIndexes.setReqBodyIndex(0);
									methodAndIndexes.setParamIndex(1);
									argTypes[0] = requestBodyTargetType;
									// [1] must be param
									if (refOperationParameterIfThereIsASingleParameter == null) {
										argTypes[1] = paramClass;
									} else {
										String clazzname = argTypeNames[1];
										argTypes[1] = ClassOfSingleParam.getClassOfSingleParamType(clazzname);
										methodAndIndexes.setSingleParameterTargetType(argTypes[1]);
									}

								} else if (isActualTargetAssignableToArgType(argTypeNames[1], requestBodyTargetType))

								{
									bodyArgIndex = 1;
									methodAndIndexes.setReqBodyIndex(1);
									methodAndIndexes.setParamIndex(0);
									argTypes[1] = requestBodyTargetType;
									// [0] must be param
									if (refOperationParameterIfThereIsASingleParameter == null) {
										argTypes[0] = paramClass;
									} else {
										String clazzname = argTypeNames[0];
										argTypes[0] = ClassOfSingleParam.getClassOfSingleParamType(clazzname);
										methodAndIndexes.setSingleParameterTargetType(argTypes[0]);
									}
								} else {
									// how can this be
									// log warning
								}

							} else if (argTypeNames.length == 1) {
								if (requestBodyTargetType != null && paramClass == null) {
									bodyArgIndex = 0;
									argTypes[0] = requestBodyTargetType;
									methodAndIndexes.setReqBodyIndex(0);
								} else if (requestBodyTargetType == null && paramClass != null) {
									methodAndIndexes.setParamIndex(0);
									if (refOperationParameterIfThereIsASingleParameter == null) {
										argTypes[0] = paramClass;
									} else {
										String clazzname = argTypeNames[0];
										argTypes[0] = ClassOfSingleParam.getClassOfSingleParamType(clazzname);
										methodAndIndexes.setSingleParameterTargetType(argTypes[0]);
									}
								}
							}

							else {
								// no args
							}
//							try {
								Class serviceClass=serviceBean.getClass();
								System.out.println("**********GOT serviceClass="+serviceClass.getName());
								//Class serviceClass = Class.forName(serviceClassName);
								
								Method method = getActualMethod(methodName, argTypes, serviceClass, bodyArgIndex);
								if (method == null) {
									logger.error("Unexpected configuration- method not found ");
								}
								methodAndIndexes.setMethod(method);
								methodAndIndexes.setServiceBean(serviceBean);
//							} catch (ClassNotFoundException e) {
//								logger.error("class not found", e);
//							}

						} else {
							// again no use
							// log
						}
					}
					else
					{
						logger.error("Unexpected configuration- bean: "+serviceClassBeanName+" not found");
					}


				} else {
					// warn about bad definition
				}
			}

		}

		return methodAndIndexes;
	}

	private boolean isActualTargetAssignableToArgType(String argTypeName, Class<?> requestBodyTargetType) {

		Class argType = null;

		try {
			argType = ClassUtils.forName(argTypeName, null);
		} catch (ClassNotFoundException e) {

		}

		if (argType == null) {
			String fqn = modelPackageUtil.fqn(argTypeName);
			try {
				argType = Class.forName(fqn);
			} catch (ClassNotFoundException e) {

			}
		}
		if (argType == null) {
			String fqn = "java.lang."+argTypeName;
			try {
				argType = Class.forName(fqn);
			} catch (ClassNotFoundException e) {

			}
		}
		boolean ret = false;
		if (argType != null) {
			ret = argType.isAssignableFrom(requestBodyTargetType);
		}

		return ret;
	}

	private Method actualMethod = null;

	private Method getActualMethod(String methodName, Class[] argTypes, Class serviceClass, int bodyArgIndex)
			throws NoSuchMethodException {
		if (actualMethod == null) {
			Method method = null;
			if (bodyArgIndex != -1) {
				Method[] declaredMethods = serviceClass.getDeclaredMethods();
				for (Method declaredMethod : declaredMethods) {
					Class<?>[] parameterTypes = declaredMethod.getParameterTypes();
					if (declaredMethod.getName().equals(methodName) && parameterTypes.length == argTypes.length) {
						boolean paramsOk = false;
						if (parameterTypes.length == 2) {
							if (bodyArgIndex == 0) {
								if (parameterTypes[1].isAssignableFrom(argTypes[1])) {
									paramsOk = true;
								}
							} else if (bodyArgIndex == 1) {
								if (parameterTypes[0].isAssignableFrom(argTypes[0])) {
									paramsOk = true;
								}
							}
						} else {
							// no params so ok
							paramsOk = true;
						}
						if (paramsOk) {
							if (parameterTypes[bodyArgIndex].isAssignableFrom(argTypes[bodyArgIndex])) {
								method = declaredMethod;
								break;
							}
						}

					}
				}
			} else {
				method = serviceClass.getDeclaredMethod(methodName, argTypes);
			}
			actualMethod = method;
		}

		return actualMethod;
	}

	private boolean isArray(Parameter parameter) {
		boolean isArray = false;
		String ref = parameter.get$ref();
		if (ref != null) {
			parameter = this.getFromRef(ref);
			if (parameter == null) {
				// already logged warning
				// log again no harm
				// lets just skip

			}
		}
		if (parameter != null) {
			Schema operationParameterSchema = parameter.getSchema();
			if (operationParameterSchema != null) {
				String type = operationParameterSchema.getType();
				if (type != null && type.equals("array")) {
					isArray = true;
				}
			}

		}
		return isArray;
	}

	// see similar code in generator for reference
	private String getTypeName() {
		String typename = null;
		Map<String, Object> extensions = operation.getExtensions();
		if (extensions != null) {
			Object typeNameAsObj = extensions.get(DamahExtns.X_DAMAH_PARAM_REF);
			Object xDamahParamType = extensions.get(DamahExtns.X_DAMAH_PARAM_TYPE);
			if (typeNameAsObj != null) {
				typename = (String) typeNameAsObj;
			} else if (xDamahParamType != null) {
				typename = (String) xDamahParamType;
			} else {
				String operationId = operation.getOperationId();
				String use = null;
				if (operationId != null) {
					String operationIdTrimmed = operationId.trim();
					int operationIdTrimmedLength = operationIdTrimmed.length();
					if (operationIdTrimmedLength > 0) {
						use = String.valueOf(Character.toUpperCase(operationIdTrimmed.charAt(0)));
						if (operationIdTrimmedLength > 1) {
							use += operationIdTrimmed.substring(1);
						}
					}

				}
				if (use == null) {
					use = pathToType(path, httpMethod);
				}

				use = "Params" + use;
				typename = use;
			}
		}
		return typename;
	}

	private String pathToType(String path, HttpMethod method) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0, size = path.length(); i < size; i++) {
			char c = path.charAt(i);
			if (Character.isLetterOrDigit(c)) {
				sb.append(c);
			}
		}
		sb.append(method.name());
		return sb.toString();
	}

	private static final String ParameterRefPrefix = "#/components/parameters/";
	private MethodAndIndexes methodAndIndexes;

	public Parameter getFromRef(String ref) {
		// ref=#/components/parameters/AbcType
		Parameter ret = null;

		if (ref.startsWith(ParameterRefPrefix)) {
			String key = ref.substring(ParameterRefPrefix.length());
			ret = this.openApi.getComponents().getParameters().get(key);
		} else {
			// we dont want to look anywhere else for parameterrefs
			// in controller throwing excption for such things is not desirable
			// lets just log
			logger.error(ref + " does not have a matching type");
		}
		return ret;
	}

	public void setPath(String path) {
		this.path = path;
	}

	public void setHttpMethod(HttpMethod httpMethod) {
		this.httpMethod = httpMethod;
	}

	public void setPathItem(PathItem pathItem) {
		this.pathItem = pathItem;
	}

	public void setOperation(Operation operation) {
		this.operation = operation;
	}

	public void setWebHook(boolean isWebHook) {
		this.isWebHook = isWebHook;
	}

	public void setModelPackageUtil(ModelPackageUtil modelPackageUtil) {
		this.modelPackageUtil = modelPackageUtil;
	}

	public void setContext(ApplicationContext context) {
		this.context = context;
	}

	public void setConversionService(ConversionService conversionService) {
		this.conversionService = conversionService;
		this.singleParamConverter.setConversionService(conversionService);
	}

	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public void setMappingJackson2XmlHttpMessageConverter(
			MappingJackson2XmlHttpMessageConverter mappingJackson2XmlHttpMessageConverter) {
		this.mappingJackson2XmlHttpMessageConverter = mappingJackson2XmlHttpMessageConverter;
	}

	interface StreamReaderToTarget {

		Object read(InputStreamReader isr, Class<?> targetType)
				throws IOException, StreamReadException, DatabindException;

	}

}

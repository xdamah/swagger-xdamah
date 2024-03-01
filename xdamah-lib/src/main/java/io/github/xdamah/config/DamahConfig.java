package io.github.xdamah.config;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.converter.xml.MappingJackson2XmlHttpMessageConverter;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PathPatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo.Builder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPatternParser;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import io.github.xdamah.controller.DamahController;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import jakarta.annotation.PostConstruct;

@Configuration
public class DamahConfig {
	private static final Logger logger = LoggerFactory.getLogger(DamahConfig.class);
	private RequestMappingHandlerMapping requestMappingHandlerMapping;
	private Method handlerMethod;

	@Autowired
	OpenAPI openApi;
	@Autowired
	private ApplicationContext context;

	@Autowired
	private ConversionService conversionService;
	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private MappingJackson2XmlHttpMessageConverter mappingJackson2XmlHttpMessageConverter;
	@Autowired
	private ModelPackageUtil modelPackageUtil;

	@PostConstruct
	void init() {
		NonSpringHolder.INSTANCE.setMappingJackson2XmlHttpMessageConverter(mappingJackson2XmlHttpMessageConverter);
		NonSpringHolder.INSTANCE.setObjectMapper(objectMapper);
		objectMapper.setSerializationInclusion(Include.NON_NULL);
		NonSpringHolder.INSTANCE.setModelPackageUtil(modelPackageUtil);
		NonSpringHolder.INSTANCE.setOpenApi(openApi);
	}

	

	@Autowired
	public void setRequestMappingHandlerMapping(RequestMappingHandlerMapping requestMappingHandlerMapping) {

		this.requestMappingHandlerMapping = requestMappingHandlerMapping;

		Method[] declaredMethods = DamahController.class.getDeclaredMethods();
		for (Method method : declaredMethods) {
			if (method.getName().equals("handleRequest")) {
				this.handlerMethod = method;
			}
		}
		logger.debug("ok" + this.requestMappingHandlerMapping);

		Paths paths = openApi.getPaths();
		Map<String, PathItem> webhooks = openApi.getWebhooks();
		if (webhooks != null) {
			Set<Entry<String, PathItem>> webHooksEntrySet = webhooks.entrySet();
			for (Entry<String, PathItem> webHooksEntry : webHooksEntrySet) {
				readPathEntry(webHooksEntry, true);
			}
		}
		if (paths != null) {
			Set<Entry<String, PathItem>> pathsEntrySet = paths.entrySet();
			for (Entry<String, PathItem> pathEntry : pathsEntrySet) {
				readPathEntry(pathEntry, false);
			} //
		}
	}

	private void readPathEntry(Entry<String, PathItem> pathEntry, boolean isWebHook) {
		String path = pathEntry.getKey();

		PathItem pathItem = pathEntry.getValue();
		Map<HttpMethod, Operation> readOperationsMap = pathItem.readOperationsMap();
		Set<Entry<HttpMethod, Operation>> operationsMapEntrySet = readOperationsMap.entrySet();
		for (Entry<HttpMethod, Operation> operationsMapEntry : operationsMapEntrySet) {
			HttpMethod httpMethod = operationsMapEntry.getKey();
			Operation operation = operationsMapEntry.getValue();
			readOperation(path, httpMethod, pathItem, operation, isWebHook);
		}

	}

	private void readOperation(String path, HttpMethod httpMethod, PathItem pathItem, Operation operation,
			boolean isWebHook) {
		RequestMethod method = buildMethod(httpMethod);
		boolean pathInUse = false;
		boolean methodInUse = false;
		Set<Entry<RequestMappingInfo, HandlerMethod>> requstMappingHandlerEntrySet = requestMappingHandlerMapping
				.getHandlerMethods().entrySet();
		for (Entry<RequestMappingInfo, HandlerMethod> entry : requstMappingHandlerEntrySet) {
			RequestMappingInfo key = entry.getKey();
			RequestMethodsRequestCondition methodsCondition = key.getMethodsCondition();
			Set<RequestMethod> methods = methodsCondition.getMethods();
			if (methods.contains(method)) {
				methodInUse = true;
			}
			PathPatternsRequestCondition pathPatternsCondition = key.getPathPatternsCondition();
			if (pathPatternsCondition != null) {
				Set<String> directPaths = pathPatternsCondition.getDirectPaths();
				if (directPaths.contains(path)) {
					pathInUse = true;
				}

				Set<String> patternValues = pathPatternsCondition.getPatternValues();
				if (patternValues.contains(path)) {
					pathInUse = true;
				}

			}
			PatternsRequestCondition patternsCondition = key.getPatternsCondition();
			if (patternsCondition != null) {
				Set<String> directPaths = patternsCondition.getDirectPaths();
				if (directPaths.contains(path))
					;
				{
					pathInUse = true;
				}
				Set<String> patterns2 = patternsCondition.getPatterns();
				if (patterns2.contains(path)) {
					pathInUse = true;
				}

			}

			HandlerMethod value = entry.getValue();
			logger.debug("value=" + value.getBeanType().getName());

		}
		if (pathInUse && methodInUse) {
			// looks like a controller exists with same path and method
			// so we wont define for this path and method
			return;
		}

		RequestBody requestBody = operation.getRequestBody();
		String[] consumesTypesArray = null;
		if (requestBody != null) {
			Content content = requestBody.getContent();
			consumesTypesArray = buildContentTypes(content);

		}
		ApiResponses apiResponses = operation.getResponses();
		String[] producesTypesArray = null;
		if (apiResponses != null) {
			Set<Entry<String, ApiResponse>> apiResponsesEntrySet = apiResponses.entrySet();
			for (Entry<String, ApiResponse> apiResponsesEntry : apiResponsesEntrySet) {

				ApiResponse value = apiResponsesEntry.getValue();

				Content content = value.getContent();
				producesTypesArray = buildContentTypes(content);
			}
		}

		Builder builder = RequestMappingInfo.paths(path).methods(method);
		if (consumesTypesArray != null) {
			builder = builder.produces(consumesTypesArray);
		}
		if (producesTypesArray != null) {
			builder = builder.produces(producesTypesArray);
		}

		RequestMappingInfo.BuilderConfiguration options = new RequestMappingInfo.BuilderConfiguration();
		options.setPatternParser(new PathPatternParser());

		builder = builder.options(options);

		RequestMappingInfo requestMappingInfo = builder.build();

		DamahController damahController = new DamahController();
		damahController.setOpenApi(this.openApi);
		damahController.setPath(path);
		damahController.setHttpMethod(httpMethod);

		damahController.setOperation(operation);
		damahController.setPathItem(pathItem);
		damahController.setWebHook(isWebHook);
		damahController.setModelPackageUtil(modelPackageUtil);
		damahController.setContext(context);
		damahController.setConversionService(conversionService);
		damahController.setObjectMapper(objectMapper);
		damahController.setMappingJackson2XmlHttpMessageConverter(mappingJackson2XmlHttpMessageConverter);

		requestMappingHandlerMapping.registerMapping(requestMappingInfo, damahController, this.handlerMethod);

	}

	private String[] buildContentTypes(Content content) {
		String[] consumesTypesArray = null;
		if (content != null) {
			Set<String> consumesTypeSet = new LinkedHashSet<>();

			Set<Entry<String, io.swagger.v3.oas.models.media.MediaType>> contentEntrySet = content.entrySet();
			for (Entry<String, io.swagger.v3.oas.models.media.MediaType> contentEntry : contentEntrySet) {
				String consumesType = contentEntry.getKey();
				consumesTypeSet.add(consumesType);

			}
			if (consumesTypeSet.size() > 0) {
				consumesTypesArray = new String[consumesTypeSet.size()];
				consumesTypeSet.toArray(consumesTypesArray);
			}
		}
		return consumesTypesArray;
	}

	private RequestMethod buildMethod(HttpMethod httpMethod) {

		return RequestMethod.resolve(httpMethod.name());
	}

}

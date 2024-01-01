package com.github.xdamah.codegen.mojo;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyAdditionalPropertiesKvp;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyImportMappingsKvp;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyInstantiationTypesKvp;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyLanguageSpecificPrimitivesCsv;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyTypeMappingsKvp;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyReservedWordsMappingsKvp;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyAdditionalPropertiesKvpList;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyImportMappingsKvpList;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyInstantiationTypesKvpList;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyLanguageSpecificPrimitivesCsvList;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyTypeMappingsKvpList;
import static io.swagger.codegen.v3.config.CodegenConfiguratorUtils.applyReservedWordsMappingsKvpList;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.swagger.codegen.v3.CodegenArgument;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.xdamah.codegen.XDamahGenerator;

import io.swagger.codegen.CodegenConfigLoader;
import io.swagger.codegen.v3.CliOption;
import io.swagger.codegen.v3.ClientOptInput;
import io.swagger.codegen.v3.CodegenConfig;
import io.swagger.codegen.v3.CodegenConstants;
import io.swagger.codegen.v3.DefaultGenerator;
import io.swagger.codegen.v3.config.CodegenConfigurator;
import io.swagger.codegen.v3.generators.java.SpringCodegen;

/**
 * Goal which generates client/server code from a swagger json/yaml definition.
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class XDamahCodeGenMojo extends AbstractMojo {
	private static final Logger logger = LoggerFactory.getLogger(XDamahCodeGenMojo.class);
	@Parameter(name = "verbose", required = false, defaultValue = "false")
	private boolean verbose;

	/**
	 * Client language to generate.
	 */
	@Parameter(name = "language", required = true)
	private String language;

	/**
	 * Location of the output directory.
	 */
	@Parameter(name = "output", property = "swagger.codegen.maven.plugin.output", defaultValue = "${project.build.directory}/generated-sources/swagger")
	private File output;

	/**
	 * Location of the swagger spec, as URL or file.
	 */
	@Parameter(name = "inputSpec", required = true)
	private String inputSpec;

	/**
	 * Git user ID, e.g. swagger-api.
	 */
	@Parameter(name = "gitUserId", required = false)
	private String gitUserId;

	/**
	 * Git repo ID, e.g. swagger-codegen.
	 */
	@Parameter(name = "gitRepoId", required = false)
	private String gitRepoId;

	/**
	 * Folder containing the template files.
	 */
	@Parameter(name = "templateDirectory")
	private File templateDirectory;

	/**
	 * Adds authorization headers when fetching the swagger definitions remotely. "
	 * Pass in a URL-encoded string of name:header with a comma separating multiple
	 * values
	 */
	@Parameter(name = "auth")
	private String auth;

	/**
	 * Path to separate json configuration file.
	 */
	@Parameter(name = "configurationFile", required = false)
	private String configurationFile;

	/**
	 * Specifies if the existing files should be overwritten during the generation.
	 */
	@Parameter(name = "skipOverwrite", required = false)
	private Boolean skipOverwrite;

	/**
	 * Specifies if the existing files should be overwritten during the generation.
	 */
	@Parameter(name = "removeOperationIdPrefix", required = false)
	private Boolean removeOperationIdPrefix;

	/**
	 * The package to use for generated api objects/classes
	 */
	@Parameter(name = "apiPackage")
	private String apiPackage;

	/**
	 * The package to use for generated model objects/classes
	 */
	@Parameter(name = "modelPackage")
	private String modelPackage;

	/**
	 * The package to use for the generated invoker objects
	 */
	@Parameter(name = "invokerPackage")
	private String invokerPackage;

	/**
	 * groupId in generated pom.xml
	 */
	@Parameter(name = "groupId")
	private String groupId;

	/**
	 * artifactId in generated pom.xml
	 */
	@Parameter(name = "artifactId")
	private String artifactId;

	/**
	 * artifact version in generated pom.xml
	 */
	@Parameter(name = "artifactVersion")
	private String artifactVersion;

	/**
	 * Sets the library
	 */
	@Parameter(name = "library", required = false)
	private String library;

	/**
	 * Sets the prefix for model enums and classes
	 */
	@Parameter(name = "modelNamePrefix", required = false)
	private String modelNamePrefix;

	/**
	 * Sets the suffix for model enums and classes
	 */
	@Parameter(name = "modelNameSuffix", required = false)
	private String modelNameSuffix;

	/**
	 * Sets an optional ignoreFileOverride path
	 */
	@Parameter(name = "ignoreFileOverride", required = false)
	private String ignoreFileOverride;

	/**
	 * A map of language-specific parameters as passed with the -c option to the
	 * command line
	 */
	@Parameter(name = "configOptions")
	private Map<?, ?> configOptions;

	/**
	 * A map of types and the types they should be instantiated as
	 */
	@Parameter(name = "instantiationTypes")
	private List<String> instantiationTypes;

	/**
	 * A map of classes and the import that should be used for that class
	 */
	@Parameter(name = "importMappings")
	private List<String> importMappings;

	/**
	 * A map of swagger spec types and the generated code types to use for them
	 */
	@Parameter(name = "typeMappings")
	private List<String> typeMappings;

	/**
	 * A map of additional language specific primitive types
	 */
	@Parameter(name = "languageSpecificPrimitives")
	private List<String> languageSpecificPrimitives;

	/**
	 * A map of additional properties that can be referenced by the mustache
	 * templates
	 */
	@Parameter(name = "additionalProperties")
	private List<String> additionalProperties;

	/**
	 * A map of reserved names and how they should be escaped
	 */
	@Parameter(name = "reservedWordsMappings")
	private List<String> reservedWordsMappings;

	/**
	 * Generate the apis
	 */
	@Parameter(name = "generateApis", required = false)
	private Boolean generateApis = true;

	/**
	 * Generate the models
	 */
	@Parameter(name = "generateModels", required = false)
	private Boolean generateModels = true;

	/**
	 * A comma separated list of models to generate. All models is the default.
	 */
	@Parameter(name = "modelsToGenerate", required = false)
	private String modelsToGenerate = "";

	/**
	 * Generate the supporting files
	 */
	@Parameter(name = "generateSupportingFiles", required = false)
	private Boolean generateSupportingFiles = true;

	/**
	 * A comma separated list of models to generate. All models is the default.
	 */
	@Parameter(name = "supportingFilesToGenerate", required = false)
	private String supportingFilesToGenerate = "";

	/**
	 * Generate the model tests
	 */
	@Parameter(name = "generateModelTests", required = false)
	private Boolean generateModelTests = true;

	/**
	 * Generate the model documentation
	 */
	@Parameter(name = "generateModelDocumentation", required = false)
	private Boolean generateModelDocumentation = true;

	/**
	 * Generate the api tests
	 */
	@Parameter(name = "generateApiTests", required = false)
	private Boolean generateApiTests = true;

	/**
	 * Generate the api documentation
	 */
	@Parameter(name = "generateApiDocumentation", required = false)
	private Boolean generateApiDocumentation = true;

	/**
	 * Generate the api documentation
	 */
	@Parameter(name = "withXml", required = false)
	private Boolean withXml = false;

	/**
	 * Skip the execution.
	 */
	@Parameter(name = "skip", property = "codegen.skip", required = false, defaultValue = "false")
	private Boolean skip;

	/**
	 * Skip matches
	 */
	@Parameter(name = "skipInlineModelMatches", required = false)
	private Boolean skipInlineModelMatches = false;

	/**
	 * Add the output directory to the project as a source root, so that the
	 * generated java types are compiled and included in the project artifact.
	 */
	@Parameter(defaultValue = "true")
	private boolean addCompileSourceRoot = true;

	@Parameter
	protected Map<String, String> environmentVariables = new HashMap<String, String>();

	@Parameter
	protected Map<String, String> originalEnvironmentVariables = new HashMap<String, String>();

	@Parameter
	private boolean configHelp = false;

	/**
	 * The project being built.
	 */
	@Parameter(readonly = true, required = true, defaultValue = "${project}")
	private MavenProject project;

	@Override
	public void execute() throws MojoExecutionException {
		// Using the naive approach for achieving thread safety
		synchronized (XDamahCodeGenMojo.class) {
			execute_();
		}
	}

	protected void execute_() throws MojoExecutionException {

		if (skip) {
			getLog().info("Code generation is skipped.");
			// Even when no new sources are generated, the existing ones should
			// still be compiled if needed.
			addCompileSourceRootIfConfigured();
			return;
		}

		// attempt to read from config file
		CodegenConfigurator configurator = CodegenConfigurator.fromFile(configurationFile);

		// if a config file wasn't specified or we were unable to read it
		if (configurator == null) {
			configurator = new CodegenConfigurator();
		}

		configurator.setVerbose(verbose);

		if (skipOverwrite != null) {
			configurator.setSkipOverwrite(skipOverwrite);
		}

		if (removeOperationIdPrefix != null) {
			configurator.setRemoveOperationIdPrefix(removeOperationIdPrefix);
		}

		if (isNotEmpty(inputSpec)) {
			configurator.setInputSpecURL(inputSpec);
		}

		if (isNotEmpty(gitUserId)) {
			configurator.setGitUserId(gitUserId);
		}

		if (isNotEmpty(gitRepoId)) {
			configurator.setGitRepoId(gitRepoId);
		}

		if (isNotEmpty(ignoreFileOverride)) {
			configurator.setIgnoreFileOverride(ignoreFileOverride);
		}

		configurator.setLang(language);

		configurator.setOutputDir(output.getAbsolutePath());

		configurator.setSkipInlineModelMatches(skipInlineModelMatches);

		if (isNotEmpty(auth)) {
			configurator.setAuth(auth);
		}

		if (isNotEmpty(apiPackage)) {
			configurator.setApiPackage(apiPackage);
		}

		if (isNotEmpty(modelPackage)) {
			configurator.setModelPackage(modelPackage);
		}

		if (isNotEmpty(invokerPackage)) {
			configurator.setInvokerPackage(invokerPackage);
		}

		if (isNotEmpty(groupId)) {
			configurator.setGroupId(groupId);
		}

		if (isNotEmpty(artifactId)) {
			configurator.setArtifactId(artifactId);
		}

		if (isNotEmpty(artifactVersion)) {
			configurator.setArtifactVersion(artifactVersion);
		}

		if (isNotEmpty(library)) {
			configurator.setLibrary(library);
		}

		if (isNotEmpty(modelNamePrefix)) {
			configurator.setModelNamePrefix(modelNamePrefix);
		}

		if (isNotEmpty(modelNameSuffix)) {
			configurator.setModelNameSuffix(modelNameSuffix);
		}

		if (null != templateDirectory) {
			configurator.setTemplateDir(templateDirectory.getAbsolutePath());
		}

		// Set generation options
		if (null != generateApis && generateApis) {
			System.setProperty(CodegenConstants.APIS, "");
		} else {
			System.clearProperty(CodegenConstants.APIS);
		}

		if (null != generateModels && generateModels) {
			System.setProperty(CodegenConstants.MODELS, modelsToGenerate);
		} else {
			System.clearProperty(CodegenConstants.MODELS);
		}

		if (null != generateSupportingFiles && generateSupportingFiles) {
			System.setProperty(CodegenConstants.SUPPORTING_FILES, supportingFilesToGenerate);
		} else {
			System.clearProperty(CodegenConstants.SUPPORTING_FILES);
		}

		// do not override config if already present (e.g. config read from file)
		addCodegenArgumentIfAbsent(CodegenConstants.MODEL_TESTS_OPTION, "boolean", generateModelTests.toString(),
				configurator);
		addCodegenArgumentIfAbsent(CodegenConstants.API_TESTS_OPTION, "boolean", generateApiTests.toString(),
				configurator);
		addCodegenArgumentIfAbsent(CodegenConstants.MODEL_DOCS_OPTION, "boolean", generateModelDocumentation.toString(),
				configurator);
		addCodegenArgumentIfAbsent(CodegenConstants.API_DOCS_OPTION, "boolean", generateApiDocumentation.toString(),
				configurator);

		System.setProperty(CodegenConstants.WITH_XML, withXml.toString());

		if (configOptions != null) {
			// Retained for backwards-compataibility with configOptions ->
			// instantiation-types
			if (instantiationTypes == null && configOptions.containsKey("instantiation-types")) {
				applyInstantiationTypesKvp(configOptions.get("instantiation-types").toString(), configurator);
			}

			// Retained for backwards-compataibility with configOptions -> import-mappings
			if (importMappings == null && configOptions.containsKey("import-mappings")) {
				applyImportMappingsKvp(configOptions.get("import-mappings").toString(), configurator);
			}

			// Retained for backwards-compataibility with configOptions -> type-mappings
			if (typeMappings == null && configOptions.containsKey("type-mappings")) {
				applyTypeMappingsKvp(configOptions.get("type-mappings").toString(), configurator);
			}

			// Retained for backwards-compataibility with configOptions ->
			// language-specific-primitives
			if (languageSpecificPrimitives == null && configOptions.containsKey("language-specific-primitives")) {
				applyLanguageSpecificPrimitivesCsv(configOptions.get("language-specific-primitives").toString(),
						configurator);
			}

			// Retained for backwards-compataibility with configOptions ->
			// additional-properties
			if (additionalProperties == null && configOptions.containsKey("additional-properties")) {
				applyAdditionalPropertiesKvp(configOptions.get("additional-properties").toString(), configurator);
			}

			// Retained for backwards-compataibility with configOptions ->
			// reserved-words-mappings
			if (reservedWordsMappings == null && configOptions.containsKey("reserved-words-mappings")) {
				applyReservedWordsMappingsKvp(configOptions.get("reserved-words-mappings").toString(), configurator);
			}
		}

		// Apply Instantiation Types
		if (instantiationTypes != null && !configOptions.containsKey("instantiation-types")) {
			applyInstantiationTypesKvpList(instantiationTypes, configurator);
		}

		// Apply Import Mappings
		if (importMappings != null && !configOptions.containsKey("import-mappings")) {
			applyImportMappingsKvpList(importMappings, configurator);
		}

		// Apply Type Mappings
		if (typeMappings != null && !configOptions.containsKey("type-mappings")) {
			applyTypeMappingsKvpList(typeMappings, configurator);
		}

		// Apply Language Specific Primitives
		if (languageSpecificPrimitives != null && !configOptions.containsKey("language-specific-primitives")) {
			applyLanguageSpecificPrimitivesCsvList(languageSpecificPrimitives, configurator);
		}

		if (withXml != null && withXml) {
			if (additionalProperties == null) {
				additionalProperties = new ArrayList<>();
			}
			additionalProperties.add("withXml=" + withXml.toString());
			if (configOptions == null) {
				configOptions = new HashMap<>();
			}
		}

		// Apply Additional Properties
		if (additionalProperties != null && !configOptions.containsKey("additional-properties")) {
			applyAdditionalPropertiesKvpList(additionalProperties, configurator);
		}

		// Apply Reserved Words Mappings
		if (reservedWordsMappings != null && !configOptions.containsKey("reserved-words-mappings")) {
			applyReservedWordsMappingsKvpList(reservedWordsMappings, configurator);
		}

		if (environmentVariables != null) {

			for (String key : environmentVariables.keySet()) {
				originalEnvironmentVariables.put(key, System.getProperty(key));
				String value = environmentVariables.get(key);
				if (value == null) {
					// don't put null values
					value = "";
				}
				System.setProperty(key, value);
				configurator.addSystemProperty(key, value);
			}
		}
		final ClientOptInput input = configurator.toClientOptInput();
		final CodegenConfig config = input.getConfig();
		Method[] declaredMethods = getDeclaredMethods();
		if (configOptions != null) {
			for (CliOption langCliOption : config.cliOptions()) {
				String key = langCliOption.getOpt();
				if (configOptions.containsKey(key)) {

					Object val = configOptions.get(key);

					boolean isBoolean = isBoolean(declaredMethods, key);
					if (isBoolean) {
						if (val != null) {
							if (val instanceof String) {
								val = ((String) val).toLowerCase();
								val = val.equals("true") ? true : false;
								logger.debug("found key=" + key + " with string value set it to boolean of " + val);
							}
						} else {
							val = false;
							logger.debug("found key=" + key + " without val set it to boolean of false");
						}

					}

					input.getConfig().additionalProperties().put(key, val);
				}
			}
		}

		if (configHelp) {
			for (CliOption langCliOption : config.cliOptions()) {
				System.out.println("\t" + langCliOption.getOpt());
				System.out.println("\t    " + langCliOption.getOptionHelp().replaceAll("\n", "\n\t    "));
				System.out.println();
				logger.debug("\t" + langCliOption.getOpt());
				logger.debug("\t    " + langCliOption.getOptionHelp().replaceAll("\n", "\n\t    "));

			}
			return;
		}
		try {
			new XDamahGenerator().opts(input).generate();
		} catch (Exception e) {
			// Maven logs exceptions thrown by plugins only if invoked with -e
			// I find it annoying to jump through hoops to get basic diagnostic information,
			// so let's log it in any case:
			getLog().error(e);
			throw new MojoExecutionException("Code generation failed. See above for the full exception.");
		}

		addCompileSourceRootIfConfigured();
	}

	private void addCompileSourceRootIfConfigured() {
		if (addCompileSourceRoot) {
			final Object sourceFolderObject = configOptions == null ? null
					: configOptions.get(CodegenConstants.SOURCE_FOLDER);
			final String sourceFolder = sourceFolderObject == null ? "src/main/java" : sourceFolderObject.toString();

			String sourceJavaFolder = output.toString() + "/" + sourceFolder;
			project.addCompileSourceRoot(sourceJavaFolder);
		}

		// Reset all environment variables to their original value. This prevents
		// unexpected
		// behaviour
		// when running the plugin multiple consecutive times with different
		// configurations.
		for (Map.Entry<String, String> entry : originalEnvironmentVariables.entrySet()) {
			if (entry.getValue() == null) {
				System.clearProperty(entry.getKey());
			} else {
				System.setProperty(entry.getKey(), entry.getValue());
			}
		}
	}

	private void addCodegenArgumentIfAbsent(String option, String type, String value,
			CodegenConfigurator configurator) {
		if (configurator.getCodegenArguments().stream()
				.noneMatch(codegenArgument -> option.equals(codegenArgument.getOption()))) {
			configurator.getCodegenArguments().add(new CodegenArgument().option(option).type(type).value(value));
		}
	}

	private Object loadConfig(String language, String version) {
		Object config = null;
		if ("V2".equals(version)) {
			config = CodegenConfigLoader.forName(language);
			io.swagger.codegen.CodegenConfig xu;

		} else {
			config = io.swagger.codegen.v3.CodegenConfigLoader.forName(language);
			io.swagger.codegen.v3.CodegenConfig xv;
		}
		return config;

	}

	private boolean isBoolean(Method[] declaredMethods, String key) {
		boolean ret = false;
		for (Method method : declaredMethods) {
			java.lang.reflect.Parameter[] parameters = method.getParameters();
			String methodName = method.getName();
			// logger.debug("methodNamex="+methodName+",return="+method.getReturnType());
			if (parameters.length == 1 && (methodName.startsWith("set"))
					&& method.getReturnType().getName().equals("void")) {
				Class<?> parameterType = method.getParameterTypes()[0];
				if (parameterType == boolean.class || parameterType == Boolean.class) {
					int methodNameLength = methodName.length();
					if (methodNameLength > 3)// more than "set" eg "setX"
					{
						char firstLetter = Character.toLowerCase(methodName.charAt(3));
						String use = String.valueOf(firstLetter);
						if (methodNameLength > 4) {
							use += methodName.substring(4);
						}
						if (use.equals(key)) {
							ret = true;
							break;
						}
					}
				}
				// logger.debug("methodName="+methodName+",return="+method.getReturnType()+",
				// parameerType="+parameterType.getName()+",key="+methodName.substring("set".length()));
			}
		}
		return ret;
	}

	String describe(Object o) {
		String ret = "";
		if (o != null) {
			if (o instanceof Boolean) {
				Boolean b = (Boolean) o;
				ret += ".boolean=" + b.booleanValue();
			} else {
				ret += ".tostring=" + o.toString();
			}
		}
		return ret;
	}

	private Method[] getDeclaredMethods() {
		io.swagger.codegen.v3.CodegenConfig x = (CodegenConfig) loadConfig(language, "V3");
		logger.debug("x.getClass=" + x.getClass().getName());
		logger.debug("language=" + this.language);
		URL resource = this.getClass().getResource("/META-INF/services/io.swagger.codegen.v3.CodegenConfig");
		logger.debug("resource=" + resource);

		Method[] declaredMethods = x.getClass().getMethods();
		return declaredMethods;
	}

}

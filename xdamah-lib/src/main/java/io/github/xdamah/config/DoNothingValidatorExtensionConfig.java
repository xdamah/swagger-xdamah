package io.github.xdamah.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.xdamah.validatorextn.DoNothingValidatorExtension;

@Configuration
public class DoNothingValidatorExtensionConfig {
	@Bean
	DoNothingValidatorExtension doNothingValidatorExtension()
	{
		return new DoNothingValidatorExtension();
	}
}

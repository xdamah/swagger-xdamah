package io.github.xdamah.modelresolver;

import java.lang.annotation.Annotation;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.oas.models.media.Schema;

//@Configuration
public class CustomOpenApiValidator extends ModelResolver {

	private final Class[] handledValidations = { jakarta.validation.constraints.NotNull.class,
			jakarta.validation.constraints.NotBlank.class,
			jakarta.validation.constraints.NotEmpty.class,
			jakarta.validation.constraints.Min.class,
			jakarta.validation.constraints.Max.class,
			jakarta.validation.constraints.DecimalMin.class,
			jakarta.validation.constraints.DecimalMax.class,
			jakarta.validation.constraints.Pattern.class,
			jakarta.validation.constraints.Size.class };

	private final Package[] allowedPackages = { handledValidations[0].getPackage(),
			org.hibernate.validator.constraints.CreditCardNumber.class.getPackage() };

	public CustomOpenApiValidator(ObjectMapper mapper) {
		super(mapper);
	}
	
	
	@Override
	protected void applyBeanValidatorAnnotations(BeanPropertyDefinition arg0, Schema property, Annotation[] annotations,
			Schema parent, boolean applyNotNullAnnotations) {
		super.applyBeanValidatorAnnotations(arg0, property, annotations, parent, applyNotNullAnnotations);
		applyCustom(property, annotations);
	}

	@Override
	protected void applyBeanValidatorAnnotations(Schema property, Annotation[] annotations, Schema parent, boolean applyNotNullAnnotations) {
		super.applyBeanValidatorAnnotations(property, annotations, parent, applyNotNullAnnotations);
		applyCustom(property, annotations);

	}


	private void applyCustom(Schema property, Annotation[] annotations) {
		if (annotations != null) {
			for (Annotation annotation : annotations) {
				Class<? extends Annotation> annotationType = annotation.annotationType();
				boolean handled = false;
				for (Class check : handledValidations) {
					if (annotationType == check) {
						handled = true;
						break;
					}
				}
				if (!handled) {
					Package annotationPackage = annotationType.getPackage();
					boolean allowed = false;
					for (Package allowedPackage : allowedPackages) {
						if (allowedPackage == annotationPackage) {
							allowed = true;
							break;
						}
					}
					if (allowed) {
						Map extensions = property.getExtensions();
						String extensionKey = "x-" + annotationType.getSimpleName();
						if (!(extensions != null && extensions.containsKey(extensionKey))) {
							Object value = describeAnnotation(annotation, annotationType);
							property.addExtension(extensionKey, value);

						}
					}
				}
			}
		}
	}

	private Object describeAnnotation(Annotation annotation, Class<? extends Annotation> annotationType) {
		Object ret = true;
		
		return ret;
	}

	
}


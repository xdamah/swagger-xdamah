package io.github.xdamah.validatorextn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Validator for credit card numbers Checks validity and returns card type
 * 
 * Using the hibernate validator 
 */
public class XdamahCardValidator implements IValidator {
	private static final Logger logger = LoggerFactory.getLogger(XdamahCardValidator.class);
	private static final XdamahLuhnCheckValidator validator= load();
	private static XdamahLuhnCheckValidator load() {
		XdamahLuhnCheckValidator xdamahLuhnCheckValidator = new XdamahLuhnCheckValidator();
		xdamahLuhnCheckValidator.initialize(0, Integer.MAX_VALUE, -1, false);
		return xdamahLuhnCheckValidator;
	}
	/**
	 * Checks if the field is a valid credit card number.
	 * 
	 * @param card The card number to validate.
	 * @return Whether the card number is valid.
	 */
	public ValidationResult isValid(final String cardIn) {
		boolean result = validator.isValid(cardIn, null);

		return new SimpleValidationResult(result);
	}

	

	/**
	 * Run some tests to show this works
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		String visa = "4444444444444448";
		String master = "5500005555555559";
		String amex = "371449635398431";
		String diners = "36438936438936";
		String discover = "6011016011016011";
		String jcb = "3566003566003566";
		String luhnFail = "1111111111111111";

		String invalid = "abcdabcdabcdabcd";

		printTest(visa);
		printTest(master);
		printTest(amex);
		printTest(diners);
		printTest(discover);
		printTest(jcb);
		printTest(invalid);
		printTest(luhnFail);

	}

	/**
	 * Check a card number and print the result
	 * 
	 * @param cardIn
	 */
	private static void printTest(String cardIn) {
		SimpleValidationResult result = (SimpleValidationResult) new XdamahCardValidator().isValid(cardIn);
		logger.debug(String.valueOf(result.isValid()) );
		System.out.println(String.valueOf(result.isValid()));
	}
}

package ng.appserver.templating.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import ng.appserver.templating.parser.NGDeclarationParser;

public class TestNGDeclarationParser {

	@Test
	public void _removeOldStyleCommentsFromString1() {
		var testString = "/* Hello */Hugi";
		var result = NGDeclarationParser._removeOldStyleCommentsFromString( testString );
		assertEquals( "Hugi", result );
	}

	@Test
	public void _removeOldStyleCommentsFromString2() {
		var testString = "/* Hello\n */Hugi";
		var result = NGDeclarationParser._removeOldStyleCommentsFromString( testString );
		assertEquals( "Hugi", result );
	}

	@Test
	public void _removeOldStyleCommentsFromString3() {
		var testString = "/* Hello\n */Hugi/*How*/ are /* you */";
		var result = NGDeclarationParser._removeOldStyleCommentsFromString( testString );
		assertEquals( "Hugi are ", result );
	}
}
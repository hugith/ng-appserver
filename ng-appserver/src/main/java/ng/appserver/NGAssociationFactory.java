package ng.appserver;

import java.util.Objects;

import ng.appserver.templating.parser.NGDeclaration.NGBindingValue;

public class NGAssociationFactory {

	public static final NGConstantValueAssociation TRUE = new NGConstantValueAssociation( Boolean.TRUE );
	public static final NGConstantValueAssociation FALSE = new NGConstantValueAssociation( Boolean.FALSE );

	public static NGAssociation constantValueAssociationWithValue( Object obj ) {
		return new NGConstantValueAssociation( obj );
	}

	public static NGAssociation associationWithKeyPath( String keyPath ) {

		if( keyPath.charAt( 0 ) == '^' ) {
			return new NGBindingAssociation( keyPath.substring( 1 ) );
		}

		return new NGKeyValueAssociation( keyPath );
	}

	/**
	 * FIXME: This is kind of shitty // Hugi 2024-11-16
	 */
	public static NGAssociation toAssociation( final NGBindingValue bindingValue, final boolean isInline ) {

		if( isInline ) {
			return associationForInlineBindingValue( bindingValue.value() );
		}

		return NGAssociationFactory.associationWithValue( bindingValue.value(), bindingValue.isQuoted() );
	}

	/**
	 * FIXME: This is kind of shitty // Hugi 2024-11-16
	 */
	private static NGAssociation associationForInlineBindingValue( String value ) {
		Objects.requireNonNull( value );

		if( value.startsWith( "\"" ) ) {
			value = value.substring( 1 );

			if( value.endsWith( "\"" ) ) {
				value = value.substring( 0, value.length() - 1 );
			}
			else {
				throw new IllegalArgumentException( value + " starts with quote but does not end with one. The parser should have already failed on this" );
			}

			if( value.startsWith( "$" ) ) {
				value = value.substring( 1 );

				if( value.endsWith( "VALID" ) ) {
					value = value.replaceFirst( "\\s*//\\s*VALID", "" );
				}

				return NGAssociationFactory.associationWithValue( value, false );
			}
			else {
				value = value.replaceAll( "\\\\\\$", "\\$" );
				value = value.replaceAll( "\\\"", "\"" );
				return NGAssociationFactory.associationWithValue( value, true );
			}
		}

		return NGAssociationFactory.associationWithValue( value, false );
	}

	private static NGAssociation associationWithValue( String associationValue, final boolean quoted ) {
		Objects.requireNonNull( associationValue );

		if( quoted ) {
			// MS: WO 5.4 converts \n to an actual newline. I don't know if WO 5.3 does, too, but let's go ahead and be compatible with them as long as nobody is yelling.
			// FIXME: I kind of feel like this doesn't exactly belong here. And we should validate the escape behavior and define it as either/or // Hugi 2024-10-17
			associationValue = applyEscapes( associationValue );
			return NGAssociationFactory.constantValueAssociationWithValue( associationValue );
		}

		if( isNumeric( associationValue ) ) {
			final Number number;

			if( associationValue != null && associationValue.contains( "." ) ) {
				number = Double.valueOf( associationValue );
			}
			else {
				number = Integer.parseInt( associationValue );
			}

			return NGAssociationFactory.constantValueAssociationWithValue( number );
		}

		if( "true".equalsIgnoreCase( associationValue ) || "yes".equalsIgnoreCase( associationValue ) ) {
			return TRUE;
		}

		if( "false".equalsIgnoreCase( associationValue ) || "no".equalsIgnoreCase( associationValue ) || "nil".equalsIgnoreCase( associationValue ) || "null".equalsIgnoreCase( associationValue ) ) {
			return FALSE;
		}

		return NGAssociationFactory.associationWithKeyPath( associationValue );
	}

	/**
	 * @return The given string with escape sequences \r, \n and \t converted to what they represent
	 */
	private static String applyEscapes( String string ) {
		int backslashIndex = string.indexOf( '\\' );

		if( backslashIndex != -1 ) {
			StringBuilder sb = new StringBuilder( string );
			int length = sb.length();

			for( int i = backslashIndex; i < length; i++ ) {
				char ch = sb.charAt( i );
				if( ch == '\\' && i < length ) {
					char nextCh = sb.charAt( i + 1 );
					if( nextCh == 'n' ) {
						sb.replace( i, i + 2, "\n" );
					}
					else if( nextCh == 'r' ) {
						sb.replace( i, i + 2, "\r" );
					}
					else if( nextCh == 't' ) {
						sb.replace( i, i + 2, "\t" );
					}
					else {
						sb.replace( i, i + 2, String.valueOf( nextCh ) );
					}
					length--;
				}
			}

			string = sb.toString();
		}

		return string;
	}

	static boolean isNumeric( String string ) {
		int length = string.length();

		if( length == 0 ) {
			return false;
		}

		boolean dot = false;
		int i = 0;
		char character = string.charAt( 0 );

		if( (character == '-') || (character == '+') ) {
			i = 1;
		}
		else if( character == '.' ) {
			i = 1;
			dot = true;
		}

		while( i < length ) {
			character = string.charAt( i++ );

			if( character == '.' ) {
				if( dot ) {
					return false;
				}
				dot = true;
			}
			else if( !(Character.isDigit( character )) ) {
				return false;
			}
		}

		return true;
	}
}
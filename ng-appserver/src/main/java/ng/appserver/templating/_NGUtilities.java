package ng.appserver.templating;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Objects;

import ng.appserver.elements.NGHyperlink;
import ng.appserver.elements.NGImage;
import ng.appserver.elements.NGString;
import ng.appserver.elements.NGStylesheet;

public class _NGUtilities {

	/**
	 * @return A class matching classNameToSearch for. Searches by fully qualified class name and simpleName.
	 */
	public static Class classWithName( String classNameToSearchFor ) {
		Objects.requireNonNull( classNameToSearchFor );

		final List<Class> classes = List.of( NGString.class, NGImage.class, NGHyperlink.class, NGStylesheet.class );

		for( Class c : classes ) {
			if( c.getName().equals( classNameToSearchFor ) || c.getSimpleName().equals( classNameToSearchFor ) ) {
				return c;
			}
		}

		throw new RuntimeException( "Class not found: " + classNameToSearchFor );
	}

	public static Class lookForClassInAllBundles( String s1 ) {
		throw new RuntimeException( "Not implemnted" );
	}

	public static <E> E instantiateObject( Class<E> objectClass, Class[] parameterTypes, Object[] parameters ) {
		try {
			Constructor<E> constructor = objectClass.getDeclaredConstructor( parameterTypes );
			return constructor.newInstance( parameters );
		}
		catch( Throwable e ) {
			throw new RuntimeException( e );
		}
	}

	public static boolean isNumber( String string ) {
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
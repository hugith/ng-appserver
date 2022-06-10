package ng.appserver.elements;

import java.util.Map;

import ng.appserver.NGAssociation;
import ng.appserver.NGContext;
import ng.appserver.NGElement;
import ng.appserver.NGResponse;

/**
 * FIXME: Work in progress
 */

public class NGConditional extends NGDynamicGroup {

	private NGAssociation _conditionAssociation;
	private NGAssociation _negateAssociation;

	public NGConditional( final String name, final Map<String, NGAssociation> associations, final NGElement content ) {
		super( name, associations, content );
		_conditionAssociation = associations.get( "condition" );
		_negateAssociation = associations.get( "negate" );

		if( _conditionAssociation == null ) {
			// FIXME: We should probably have an exception class for missing bindings, IllegalArgumentException isn't really nice // Hugi 2022-06-05
			throw new IllegalArgumentException( "The binding [condition] is required" );
		}
	}

	@Override
	public void appendToResponse( NGResponse response, NGContext context ) {
		final Object condition = _conditionAssociation.valueInComponent( context.component() );
		Boolean conditionAsBoolean = evaluate( condition );

		if( _negateAssociation != null ) {
			final Boolean negate = (Boolean)_negateAssociation.valueInComponent( context.component() );

			if( negate == true ) {
				conditionAsBoolean = !conditionAsBoolean;
			}
		}

		if( conditionAsBoolean ) {
			appendChildrenToResponse( response, context );
		}
	}

	/**
	 * FIXME: This is here as a temporary placeholder. We need to globally define the concepts of truthy/falsy within the framework // Hugi 2022-06-10
	 */
	private static boolean evaluate( Object object ) {
		if( object == null ) {
			return false;
		}

		return (boolean)object;
	}
}
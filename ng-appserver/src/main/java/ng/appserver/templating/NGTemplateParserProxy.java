package ng.appserver.templating;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import ng.appserver.NGApplication;
import ng.appserver.NGApplication.NGElementNotFoundException;
import ng.appserver.NGAssociation;
import ng.appserver.NGAssociationFactory;
import ng.appserver.NGBindingConfigurationException;
import ng.appserver.NGContext;
import ng.appserver.NGElement;
import ng.appserver.NGResponse;
import ng.appserver.elements.NGDynamicGroup;
import ng.appserver.elements.NGHTMLBareString;
import ng.appserver.elements.NGHTMLCommentString;
import ng.appserver.templating.NGDeclaration.NGBindingValue;
import x.junk.NGElementNotFoundElement;

/**
 * Bridges the "new and old world" for template parsing
 */

public class NGTemplateParserProxy {

	private final String _htmlString;
	private final String _wodString;

	/**
	 * @param htmlString The HTML to parse
	 * @param wodString The associated wod/declarations
	 */
	public NGTemplateParserProxy( final String htmlString, final String wodString ) {
		Objects.requireNonNull( htmlString );
		Objects.requireNonNull( wodString );

		_htmlString = htmlString;
		_wodString = wodString;
	}

	/**
	 * @return A parsed element template
	 */
	public NGElement parse() throws NGDeclarationFormatException, NGHTMLFormatException {
		final PNode rootNode = new NGTemplateParser( _htmlString, _wodString ).parse();
		return toDynamicElement( rootNode );
	}

	private static NGElement toDynamicElement( final PNode node ) {
		return switch( node ) {
			case PBasicNode n -> toDynamicElement( n );
			case PGroupNode n -> toTemplate( n.children() );
			case PHTMLNode n -> new NGHTMLBareString( n.value() );
			case PCommentNode n -> new NGHTMLCommentString( n.value() );
		};
	}

	private static NGElement toDynamicElement( final PBasicNode node ) {

		final String type = node.type();
		final Map<String, NGAssociation> associations = toAssociations( node.bindings(), node.isInline() );
		final NGElement childTemplate = toTemplate( node.children() );

		try {
			System.out.println( type );
			return NGApplication.dynamicElementWithName( type, associations, childTemplate, Collections.emptyList() );
		}
		catch( NGElementNotFoundException e ) {
			// FIXME: Experimental functionality, probably doesn't belong with the parser part of the framework.
			// But since it's definitely something we want, I'm keeping this here for reference until it finds it's final home. // Hugi 2024-10-19
			return new NGElementNotFoundElement( type );
		}
		catch( RuntimeException e ) {
			// FIXME: Digging this deep to get to the actual exception is crazy. We need to fix this up // Hugi 2024-11-19
			if( e.getCause() instanceof InvocationTargetException ite ) {
				if( ite.getCause() instanceof NGBindingConfigurationException bce ) {
					return new NGElement() {

						@Override
						public void appendToResponse( NGResponse response, NGContext context ) {
							response.appendContentString( """
									<span style="display: inline-block; margin: 10px; padding: 10px; color:white; background-color: red; border-radius: 5px; box-shadow: 5px 5px 0px rgba(0,0,200,0.8); font-size: 12px;">Binding configuration error <br><span style="font-size: 16px"><strong>%s</strong><br>%s</span>
									""".formatted( "&lt;wo:" + type + "&gt;", bce.getMessage() ) );
						};
					};
				}
			}

			throw e;
		}
	}

	private static Map<String, NGAssociation> toAssociations( final Map<String, NGBindingValue> bindings, final boolean isInline ) {
		final Map<String, NGAssociation> associations = new HashMap<>();

		for( Entry<String, NGBindingValue> entry : bindings.entrySet() ) {
			final String bindingName = entry.getKey();
			final NGAssociation association = NGAssociationFactory.toAssociation( entry.getValue(), isInline );
			associations.put( bindingName, association );
		}

		return associations;
	}

	/**
	 * @return An element/template from the given list of nodes.
	 */
	private static NGElement toTemplate( final List<PNode> nodes ) {

		final List<NGElement> elements = new ArrayList<>();

		for( final PNode pNode : nodes ) {
			elements.add( toDynamicElement( pNode ) );
		}

		if( elements.size() == 1 ) {
			return elements.getFirst();
		}

		return NGDynamicGroup.of( elements );
	}
}
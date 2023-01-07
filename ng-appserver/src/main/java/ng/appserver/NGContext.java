package ng.appserver;

import java.util.Objects;

public class NGContext {

	/**
	 * The request that initiated the creation of this context
	 */
	private final NGRequest _request;

	/**
	 * The response that will be constructed and/or  will be returned by this context.
	 */
	private NGResponse _response;

	/**
	 * Stores the context's session
	 *
	 * FIXME: THIS SHOULD ABSOLUTELY NOT BE STATIC! Just for testing, most applications need (ahem) more than one session // Hugi 2022-06-09
	 */
	private static NGSession _session = NGSession.createSession();

	/**
	 * The component currently being processed by this context
	 */
	private NGComponent _currentComponent;

	/**
	 * The page level component
	 */
	private NGComponent _page;

	/**
	 * This context's uniqueID within it's session
	 */
	private String _contextID;

	/**
	 * ID of the element currently being rendered by the context.
	 *
	 * FIXME: Rename to currentElementID?  // Hugi 2022-06-06
	 * FIXME: Not sure we want to initialize the elementID here. Cheaper to do elsewhere? // Hugi 2022-06-08
	 */
	private NGElementID _elementID = new NGElementID();

	/**
	 * FIXME: Testing. Should not be public
	 */
	private String _originatingContextID;

	/**
	 * In the case of component actions, this is the elementID of the element that invoked the action (clicked a link, submitted a form etc)
	 * Used in combination with _requestContextID to find the proper action to initiate.
	 *
	 * FIXME: I kind of feel like it should be the responsibility of the component request handler to maintain this. Component actions are leaking into the framework here.
	 * FIXME: Rename to _requestElementID to mirror the naming of _requestContextID?
	 */
	private String _senderID;

	/**
	 * Indicates the the context is currently rendering something nested inside a form element.
	 */
	private boolean _isInForm;

	public NGContext( final NGRequest request ) {
		Objects.requireNonNull( request );

		_request = request;
		request.setContext( this );

		// FIXME: Horrible session and context caching implementation just for testing purposes

		// Our contextID is just the next free slot in the session's context array
		_contextID = String.valueOf( session().contexts.size() );

		// Store our context with the session
		session().contexts.add( this );

		if( request.uri().contains( "/wo/" ) ) {
			final String componentPart = request.parsedURI().getString( 1 );

			// The _requestContextID is the first part of the request handler path
			final String _requestContextID = componentPart.split( "\\." )[0];

			// That context is currently stored in the session's context array (which will just keep on incrementing infinitely)
			_originatingContextID = _requestContextID;

			// And finally, the sending element ID is all the integers after the first one.
			_senderID = componentPart.substring( _requestContextID.length() + 1 );
		}
	}

	public NGRequest request() {
		return _request;
	}

	public NGResponse response() {
		return _response;
	}

	/**
	 * @return This context's session, creating a session if none is present.
	 */
	public NGSession session() {
		if( _session == null && _request._extractSessionID() != null ) {
			_session = NGApplication.application().sessionStore().checkoutSessionWithID( _request._extractSessionID() );
		}

		return _session;
	}

	/**
	 * @return This context's session, or null if no session is present.
	 *
	 * FIXME: This currently really only checks if session() has been invoked. We probably need to do a little deeper checking than this // Hugi 2023-01-07
	 */
	public NGSession existingSession() {
		return _session;
	}

	/**
	 * @return True if this context has an existing session
	 */
	public boolean hasSession() {
		return existingSession() != null;
	}

	/**
	 * @return The component currently being rendered by this context
	 *
	 * FIXME: Initially called component(). to reflect the WO naming. Perhaps better called currentComponent() to reflect it's use better?
	 */
	public NGComponent component() {
		return _currentComponent;
	}

	public void setCurrentComponent( NGComponent component ) {
		_currentComponent = component;
	}

	/**
	 * @return The page level component
	 */
	public NGComponent page() {
		return _page;
	}

	public void setPage( NGComponent value ) {
		_page = value;
	}

	/**
	 * @return ID of the element currently being rendered by the context.
	 */
	public String contextID() {
		return _contextID;
	}

	/**
	 * @return The ID of the "original context", i.e. the context from which the request that created this context was initiated
	 *
	 * FIXME: This can probably be removed from here and just moved to NGComponentRequestHandler
	 */
	public String _originatingContextID() {
		return _originatingContextID;
	}

	/**
	 * @return ID of the element currently being rendered by the context.
	 */
	public NGElementID elementID() {
		return _elementID;
	}

	/**
	 * @return ID of the element being targeted by a component action
	 */
	public String senderID() {
		return _senderID;
	}

	public boolean isInForm() {
		return _isInForm;
	}

	public void setIsInForm( boolean value ) {
		_isInForm = value;
	}

	@Override
	public String toString() {
		return "NGContext [_request=" + _request + ", _response=" + _response + ", _currentComponent=" + _currentComponent + ", _page=" + _page + ", _contextID=" + _contextID + ", _elementID=" + _elementID + ", _originatingContextID=" + _originatingContextID + ", _senderID=" + _senderID + ", _isInForm=" + _isInForm + "]";
	}
}
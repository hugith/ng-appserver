package ng.appserver;

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
	 * FIXME: THIS SHOULD NOT BE STATIC!
	 */
	private static NGSession _session = new NGSession();

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
	 * The ID of the context that initiated the request In the case of component actions, this will be used to restore the page we're working in.
	 *
	 * FIXME: Should be in the request instead? Or somehow handled by not storing this within the context?
	 */
	private String _requestContextID;

	/**
	 * FIXME: Testing. Should not be public
	 */
	public NGContext _originalContext;

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
		_request = request;

		System.out.println( "Creating context" );
		// FIXME: Horrible session and context caching implementation just for testing purposes
		_contextID = String.valueOf( session().contexts.size() );
		session().contexts.add( this );

		if( request.uri().contains( "/wo/" ) ) {
			final String componentPart = request.parsedURI().getString( 1 );

			_requestContextID = componentPart.split( "\\." )[0];
			_originalContext = session().contexts.get( Integer.parseInt( _requestContextID ) );
			_senderID = componentPart.substring( 2 ); // FIXME: Only works with one letter context IDs
			System.out.println( "Parsed senderID: " + _senderID );
			System.out.println( "contextID: " + _contextID );
			System.out.println( "requestContextID: " + _requestContextID );
		}
	}

	public NGRequest request() {
		return _request;
	}

	public NGResponse response() {
		return _response;
	}

	public NGSession session() {
		if( _session == null && _request._extractSessionID() != null ) {
			_session = NGApplication.application().sessionStore().checkoutSessionWithID( _request._extractSessionID() );
		}

		return _session;
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
	 * The page level component
	 */
	public NGComponent page() {
		return _page;
	}

	public void setPage( NGComponent value ) {
		_page = value;
	}

	/**
	 * ID of the element currently being rendered by the context.
	 */
	public String contextID() {
		return _contextID;
	}

	/**
	 * ID of the element currently being rendered by the context.
	 */
	public NGElementID elementID() {
		return _elementID;
	}

	public void setElementID( NGElementID value ) {
		_elementID = value;
	}

	public String requestContextID() {
		return _requestContextID;
	}

	public void setRequestContextID( String value ) {
		_requestContextID = value;
	}

	public String senderID() {
		return _senderID;
	}

	public void setSenderID( String value ) {
		_senderID = value;
	}

	public boolean isInForm() {
		return _isInForm;
	}

	public void setIsInForm( boolean value ) {
		_isInForm = value;
	}
}
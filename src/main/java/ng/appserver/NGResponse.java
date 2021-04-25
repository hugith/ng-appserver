package ng.appserver;

import java.nio.charset.StandardCharsets;

/**
 * FIXME:
 * Need to decide what to do about responses of different types.
 * Is a string response the same type as a binary response or a streaming response, even?
 * Are responses even mutable?
 */

public class NGResponse extends NGMessage {

	/**
	 * FIXME: Decide if we want a default 
	 */
	private int _status;

	/**
	 * FIXME: the response's content should probably be encapsulated by a stream.
	 */
	private byte[] _bytes;

	public NGResponse( final String contentString, final int status ) {
		setContentString( contentString );
		setStatus( status );
	}

	public NGResponse( final byte[] bytes, final int status ) {
		setBytes( bytes );
		_status = status;
	}

	public String contentString() {
		return new String( bytes(), StandardCharsets.UTF_8 );
	}

	public void setContentString( final String contentString ) {
		setBytes( contentString.getBytes( StandardCharsets.UTF_8 ) );
	}

	public int status() {
		return _status;
	}

	/**
	 * FIXME: Decide if this should be settable 
	 */
	private void setStatus( final int status ) {
		_status = status;
	}

	private void setBytes( final byte[] bytes ) {
		_bytes = bytes;
	}

	/**
	 * FIXME: This should handle more than just bytes 
	 */
	public byte[] bytes() {
		return _bytes;
	}
}
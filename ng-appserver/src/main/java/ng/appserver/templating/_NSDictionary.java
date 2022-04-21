package ng.appserver.templating;

import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;

public class _NSDictionary<K, V> extends HashMap<K, V> {

	public _NSDictionary( V value, K key ) {
		super();
		put( key, value );
	}

	public _NSDictionary() {}

	public Enumeration objectEnumerator() {
		return Collections.enumeration( this.values() );
	}

	public Enumeration keyEnumerator() {
		return Collections.enumeration( this.keySet() );
	}

	public Object objectForKey( String key ) {
		return get( key );
	}

	public void setObjectForKey( V object, K key ) {
		put( key, object );
	}

	public _NSMutableDictionary<K, V> mutableClone() {
		return (_NSMutableDictionary<K, V>)this.clone();
	}
}
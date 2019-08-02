package org.bipolis.ds.sandbox;

import java.nio.ByteBuffer;

/**
 * Provides LdifFiles.
 *
 * @author stbischof
 *
 */
public interface LdifProvider {

	/**
	 * The
	 * 
	 * @return
	 */
	ByteBuffer getLdif();

}

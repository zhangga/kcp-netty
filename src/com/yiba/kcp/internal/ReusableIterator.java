package com.yiba.kcp.internal;

import java.util.Iterator;

/**
 * Reusable iterator
 * @author U-Demon
 * @date 2019年1月24日 下午5:48:14
 */
public interface ReusableIterator<E> extends Iterator<E> {
	
	/**
     * Reset the iterator to initial state.
     *
     * @return this object
     */
    ReusableIterator<E> rewind();

}

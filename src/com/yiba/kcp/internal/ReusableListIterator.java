package com.yiba.kcp.internal;

import java.util.ListIterator;

/**
 * Reusable list iterator
 * @author U-Demon
 * @date 2019年1月24日 下午6:07:50
 * @param <E>
 */
public interface ReusableListIterator<E> extends ListIterator<E>, ReusableIterator<E> {
	
	/**
     * This method is identical to {@code rewind(0)}.
     *
     * @return this iterator
     */
    ReusableListIterator<E> rewind();


    /**
     * Reset the iterator and specify index of the first element to be returned from the iterator.
     *
     * @param index index of the first element to be returned from the iterator
     * @return this iterator
     */
    ReusableListIterator<E> rewind(int index);

}

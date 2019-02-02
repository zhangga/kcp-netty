package com.yiba.kcp.internal;

import java.util.Collection;

public interface ReItrCollection<E> extends Collection<E> {
	
	@Override
    ReusableIterator<E> iterator();

}

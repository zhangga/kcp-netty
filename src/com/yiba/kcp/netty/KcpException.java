package com.yiba.kcp.netty;

public class KcpException extends RuntimeException {

	private static final long serialVersionUID = -1862729595117218348L;

	public KcpException() {
        super();
    }

    public KcpException(String message, Throwable cause) {
        super(message, cause);
    }

    public KcpException(String message) {
        super(message);
    }

    public KcpException(Throwable cause) {
        super(cause);
    }

}

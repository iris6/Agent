package com.alibaba.dubbo.performance.demo.agent.dubbo.netty;

import java.util.List;
import java.util.Map;

/**
 * 描述与服务器的连接
 *
 */
public interface RpcConnection {
	void init();
	void connect();
	void connect(String host, int port);
	public Object Send(String interfaceName, String method, String parameterTypesString, String parameter) throws Exception;
	void close();
	boolean isConnected();
	boolean isClosed();
	public boolean containsFuture(String key);
	public InvokeFuture<Object> removeFuture(String key);
	public void setResult(Object ret);
	public void setTimeOut(long timeout);
	public List<InvokeFuture<Object>> getFutures(String method);
}

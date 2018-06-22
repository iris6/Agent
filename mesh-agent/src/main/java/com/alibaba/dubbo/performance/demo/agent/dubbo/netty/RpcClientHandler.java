package com.alibaba.dubbo.performance.demo.agent.dubbo.netty;

import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcFuture;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcRequestHolder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;

public class RpcClientHandler extends SimpleChannelInboundHandler<RpcResponse> {
	private RpcConnection connect;
	private Throwable cause;

	public RpcClientHandler(RpcConnection conn)
	{
		this.connect=conn;
	}

	@Override
	public void channelActive(ChannelHandlerContext ctx) throws Exception {
		super.channelActive(ctx);
		System.out.println("connected on server:"+ctx.channel().localAddress().toString());
	}
	@Override
	public void channelRead0(ChannelHandlerContext ctx, RpcResponse response)
			throws Exception {
		String key = response.getRequestId();
		if (connect.containsFuture(key)) {
			InvokeFuture<Object> future = connect.removeFuture(key);
			if (future == null) {
				return;
			}
			if (this.cause != null) {
				future.setResult(this.cause);
				cause.printStackTrace();
			} else {
				String requestId = response.getRequestId();
				if (requestId != null) {
					future.setResult(response);
					this.connect.setResult(response);
					for (InvokeFuture<Object> f : connect.getFutures(future.getMethod())) {
						f.setResult(response);
					}
				}
			}
		}
	}
}

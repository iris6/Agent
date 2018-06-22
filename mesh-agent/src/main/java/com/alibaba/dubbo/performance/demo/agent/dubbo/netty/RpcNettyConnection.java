package com.alibaba.dubbo.performance.demo.agent.dubbo.netty;

import com.alibaba.dubbo.performance.demo.agent.dubbo.DubboRpcDecoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.DubboRpcEncoder;
import com.alibaba.dubbo.performance.demo.agent.dubbo.RpcClient;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.JsonUtils;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.Request;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcInvocation;
import com.alibaba.dubbo.performance.demo.agent.dubbo.model.RpcResponse;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 连接的具体实现类
 *
 */
public class RpcNettyConnection implements RpcConnection {

	private InetSocketAddress inetAddr;
	
	private volatile Channel channel;
	
	private  RpcClientHandler handle;
	
	private static Map<String, InvokeFuture<Object>> futrues=new ConcurrentHashMap<String, InvokeFuture<Object>>();
	//连接数组
	private Map<String, Channel> channels=new ConcurrentHashMap<String, Channel>();
	
	private ServerBootstrap bootstrap;
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;
	
	private volatile ResultFuture<Object> resultFuture;//异步调用的时候的结果集
	
	private long timeout=3000;//默认超时
	
	private boolean connected=false;
	public RpcNettyConnection()
	{
		
	}
	public RpcNettyConnection(String host,int port)
	{
		inetAddr=new InetSocketAddress(host,port);
		handle=new RpcClientHandler(this);
		init();
	}
	private Channel getChannel(String key)
	{
		return channels.get(key);
	}
	@Override
	public void init() {
		try 
        {
			bossGroup = new NioEventLoopGroup(1,
					new DefaultThreadFactory("NettyServerBoss", true));
			workerGroup = new NioEventLoopGroup(4,
					new DefaultThreadFactory("NettyServerWorker", true));
            bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup,workerGroup).channel(NioServerSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel channel) throws Exception {
                    	channel.pipeline().addLast(new DubboRpcEncoder());
                    	channel.pipeline().addLast(new DubboRpcDecoder());
                        channel.pipeline().addLast(handle);
                    }
                })
                .option(ChannelOption.SO_KEEPALIVE, true);
        }
        catch (Exception ex) 
        {
        	ex.printStackTrace();
        }finally {
			bossGroup.shutdownGracefully();
			workerGroup.shutdownGracefully();
		}
	}

	@Override
	public void connect() {
		try
		{
			ChannelFuture future = bootstrap.bind(this.inetAddr).sync();
			channels.put(this.inetAddr.toString(), future.channel()); 
			connected=true;
		} 
		catch(InterruptedException e) 
		{
			e.printStackTrace();
		}
	}

	@Override
	public void connect(String host, int port) {
		ChannelFuture future = bootstrap.bind(new InetSocketAddress(host, port));
		future.addListener(new ChannelFutureListener(){
			@Override
			public void operationComplete(ChannelFuture cfuture) throws Exception {
				 Channel channel = cfuture.channel();
				 //添加进入连接数组
			     channels.put(channel.remoteAddress().toString(), channel); 
			}
		});
	}

	@Override
	public Object Send(String interfaceName, String method, String parameterTypesString, String parameter) throws Exception{
		if(channel==null)
			channel=getChannel(inetAddr.toString());
		if(channel!=null)
		{
			RpcInvocation invocation = new RpcInvocation();
			invocation.setMethodName(method);
			invocation.setAttachment("path", interfaceName);
			invocation.setParameterTypes(parameterTypesString);    // Dubbo内部用"Ljava/lang/String"来表示参数类型是String

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			PrintWriter writer = new PrintWriter(new OutputStreamWriter(out));
			JsonUtils.writeObject(parameter, writer);
			invocation.setArguments(out.toByteArray());

			Request request = new Request();
			request.setVersion("2.0.0");
			request.setTwoWay(true);
			request.setData(invocation);

			final InvokeFuture<Object> future=new InvokeFuture<Object>();
			futrues.put(request.getId()+"", future);
			ChannelFuture cfuture=channel.writeAndFlush(request);
			cfuture.addListener(new ChannelFutureListener() {
				@Override
				public void operationComplete(ChannelFuture rfuture) throws Exception {
					if(!rfuture.isSuccess()){
						future.setCause(rfuture.cause());
					}
				}
			});
			resultFuture=new ResultFuture<Object>(timeout);
			resultFuture.setRequestId(request.getId()+"");
			try
			{
				Object result=future.getResult(timeout, TimeUnit.MILLISECONDS);
				return result;
			}
			catch(RuntimeException e)
			{
				throw e;
			}
			finally
			{
				//这个结果已经收到
				futrues.remove(request.getId()+"");
			}
		}
		else
		{
			return null;
		}
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		if(channel==null)
			try {
				channel.closeFuture().sync();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	@Override
	public boolean isConnected() {
		// TODO Auto-generated method stub
		return connected;
	}

	@Override
	public boolean isClosed() {
		// TODO Auto-generated method stub
		return (null == channel) || !channel.isOpen()
				|| !channel.isWritable() || !channel.isActive();
	}

	@Override
	public boolean containsFuture(String key) {
		// TODO Auto-generated method stub
		return futrues.containsKey(key);
	}

	@Override
	public InvokeFuture<Object> removeFuture(String key) {
		// TODO Auto-generated method stub
		if(containsFuture(key))
			return futrues.remove(key);
		else
			return null;
	}
	@Override
	public void setResult(Object ret) {
		// TODO Auto-generated method stub
		RpcResponse response=(RpcResponse)ret;
		if(response.getRequestId().equals(resultFuture.getRequestId()))
			resultFuture.setResult(ret);
	}
	@Override
	public void setTimeOut(long timeout) {
		// TODO Auto-generated method stub
		this.timeout=timeout;
	}

	@Override
	public List<InvokeFuture<Object>> getFutures(String method) {
		List<InvokeFuture<Object>> list=new ArrayList<InvokeFuture<Object>>();
		// TODO Auto-generated method stub
		Iterator<Map.Entry<String, InvokeFuture<Object>>> it = futrues.entrySet().iterator();
		String methodName=null;
		InvokeFuture<Object> temp=null;
        while (it.hasNext()) 
        {
            Map.Entry<String, InvokeFuture<Object>> entry = it.next();
            
            
            methodName=entry.getValue().getMethod();
            temp=entry.getValue();
            
            if(methodName!=null&&methodName.equals(method)&&temp!=null)
            {
            	list.add(temp);
            	methodName=null;
            	temp=null;
            }
            
        }
        return list;
	}

}

package network.pipeline;

import io.netty.channel.*;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.ReadTimeoutException;
import network.Connection;
import network.data.Attributes;
import network.data.Host;
import network.listeners.MessageListener;
import network.messaging.NetworkMessage;
import network.messaging.control.HeartbeatMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public abstract class ConnectionHandler<T> extends ChannelDuplexHandler implements Connection<T> {

    private static final Logger logger = LogManager.getLogger(ConnectionHandler.class);

    Host peer;
    Attributes attributes;
    Channel channel;
    EventLoop loop;
    private final MessageListener<T> consumer;
    private final boolean incoming;

    public ConnectionHandler(MessageListener<T> consumer, EventLoop loop, boolean incoming){
        this.consumer = consumer;
        this.incoming = incoming;
        this.loop = loop;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        NetworkMessage netMsg = (NetworkMessage) msg;
        if (netMsg.code == NetworkMessage.CTRL_MSG) return;
        consumer.deliverMessage((T) netMsg.payload, this);
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) {
        ctx.write(msg, promise.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                exceptionCaught(ctx, future.cause());
            }
        }));
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.WRITER_IDLE) {
                ctx.channel().writeAndFlush(new NetworkMessage(NetworkMessage.CTRL_MSG, new HeartbeatMessage()));
            } else if (e.state() == IdleState.READER_IDLE) {
                ctx.pipeline().fireExceptionCaught(ReadTimeoutException.INSTANCE);
            }
        } else if (!(evt instanceof ChannelInputShutdownReadComplete)) {
            internalUserEventTriggered(ctx, evt);
        }
    }

    abstract void internalUserEventTriggered(ChannelHandlerContext ctx, Object evt);

    public final Host getPeer() {
        return peer;
    }

    public final Attributes getAttributes() {
        return attributes;
    }

    public boolean isInbound(){
        return incoming;
    }

    public boolean isOutbound(){
        return !incoming;
    }
}
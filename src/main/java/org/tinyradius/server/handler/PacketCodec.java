package org.tinyradius.server.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinyradius.packet.PacketEncoder;
import org.tinyradius.packet.RadiusPacket;
import org.tinyradius.server.SecretProvider;

import java.net.InetSocketAddress;
import java.util.List;

@ChannelHandler.Sharable
public class PacketCodec extends MessageToMessageCodec<DatagramPacket, ResponseContext> {

    private static final Logger logger = LoggerFactory.getLogger(PacketCodec.class);

    private final PacketEncoder packetEncoder;
    private final SecretProvider secretProvider;

    public PacketCodec(PacketEncoder packetEncoder, SecretProvider secretProvider) {
        this.packetEncoder = packetEncoder;
        this.secretProvider = secretProvider;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, DatagramPacket msg, List<Object> out) throws Exception {
        InetSocketAddress localAddress = msg.recipient();
        InetSocketAddress remoteAddress = msg.sender();

        String secret = secretProvider.getSharedSecret(remoteAddress);
        if (secret == null)
            logger.warn("Ignoring request from unknown client {}, shared secret lookup failed (local address {})", remoteAddress, localAddress);

        // parse packet
        RadiusPacket request = packetEncoder.fromDatagram(msg, secret);
        logger.debug("Received packet from {} on local address {} - {}", remoteAddress, localAddress, request);

        out.add(new RequestContext(request, localAddress, remoteAddress, secret));
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ResponseContext msg, List<Object> out) throws Exception {
        if (msg == null)
            return;

        out.add(packetEncoder.toDatagram(
                msg.getResponse().encodeResponse(msg.getSecret(), msg.getRequest().getAuthenticator()), msg.getRemoteAddress(), msg.getLocalAddress()));
        logger.debug("Sending response to {}", msg.getRemoteAddress());
    }
}
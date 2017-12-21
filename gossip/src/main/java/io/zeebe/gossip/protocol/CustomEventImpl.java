package io.zeebe.gossip.protocol;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.gossip.membership.GossipTerm;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import org.agrona.*;
import org.agrona.concurrent.UnsafeBuffer;

public class CustomEventImpl implements CustomEvent
{
    private final GossipTerm senderGossipTerm = new GossipTerm();
    private final SocketAddress senderAddress = new SocketAddress();

    private MutableDirectBuffer typeBuffer = new ExpandableArrayBuffer();
    private DirectBuffer typeView = new UnsafeBuffer(typeBuffer);
    private int typeLength = 0;

    private MutableDirectBuffer payloadBuffer = new ExpandableArrayBuffer();
    private DirectBuffer payloadView = new UnsafeBuffer(payloadBuffer);
    private int payloadLength = 0;

    public void typeLength(int length)
    {
        this.typeLength = length;
    }

    public MutableDirectBuffer getTypeBuffer()
    {
        return typeBuffer;
    }

    public void payloadLength(int length)
    {
        this.payloadLength = length;
    }

    public MutableDirectBuffer getPayloadBuffer()
    {
        return payloadBuffer;
    }

    public CustomEventImpl senderAddress(SocketAddress address)
    {
        this.senderAddress.host(address.getHostBuffer(), 0, address.hostLength());
        this.senderAddress.port(address.port());
        return this;
    }

    public CustomEventImpl senderGossipTerm(GossipTerm term)
    {
        this.senderGossipTerm.epoch(term.getEpoch()).heartbeat(term.getHeartbeat());
        return this;
    }

    public CustomEventImpl type(DirectBuffer typeBuffer)
    {
        this.typeLength = typeBuffer.capacity();
        this.typeBuffer.putBytes(0, typeBuffer, 0, typeLength);
        return this;
    }

    public CustomEventImpl payload(DirectBuffer payloadBuffer)
    {
        return payload(payloadBuffer, 0, payloadBuffer.capacity());
    }

    public CustomEventImpl payload(DirectBuffer payloadBuffer, int offset, int length)
    {
        this.payloadLength = length;
        this.payloadBuffer.putBytes(0, payloadBuffer, offset, length);
        return this;
    }

    @Override
    public GossipTerm getSenderGossipTerm()
    {
        return senderGossipTerm;
    }

    @Override
    public SocketAddress getSenderAddress()
    {
        return senderAddress;
    }

    @Override
    public DirectBuffer getType()
    {
        typeView.wrap(typeBuffer, 0, typeLength);
        return typeView;
    }

    @Override
    public DirectBuffer getPayload()
    {
        payloadView.wrap(payloadBuffer, 0, payloadLength);
        return payloadView;
    }

    public int getTypeLength()
    {
        return typeLength;
    }

    public int getPayloadLength()
    {
        return payloadLength;
    }

    @Override
    public String toString()
    {
        final StringBuilder builder = new StringBuilder();
        builder.append("CustomEvent [senderAddress=");
        builder.append(senderAddress);
        builder.append(", senderGossipTerm=");
        builder.append(senderGossipTerm);
        builder.append(", type=");
        builder.append(bufferAsString(typeView));
        builder.append(", payload=");
        builder.append(bufferAsString(payloadView));
        builder.append("]");
        return builder.toString();
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((senderAddress == null) ? 0 : senderAddress.hashCode());
        result = prime * result + ((senderGossipTerm == null) ? 0 : senderGossipTerm.hashCode());
        result = prime * result + ((payloadView == null) ? 0 : payloadView.hashCode());
        result = prime * result + ((typeView == null) ? 0 : typeView.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final CustomEventImpl other = (CustomEventImpl) obj;
        if (senderAddress == null)
        {
            if (other.senderAddress != null)
            {
                return false;
            }
        }
        else if (!senderAddress.equals(other.senderAddress))
        {
            return false;
        }
        if (senderGossipTerm == null)
        {
            if (other.senderGossipTerm != null)
            {
                return false;
            }
        }
        else if (!senderGossipTerm.equals(other.senderGossipTerm))
        {
            return false;
        }
        if (typeView == null)
        {
            if (other.typeView != null)
            {
                return false;
            }
        }
        else if (!BufferUtil.equals(typeView, other.typeView))
        {
            return false;
        }
        if (payloadView == null)
        {
            if (other.payloadView != null)
            {
                return false;
            }
        }
        else if (!BufferUtil.equals(payloadView, other.payloadView))
        {
            return false;
        }
        return true;
    }

}

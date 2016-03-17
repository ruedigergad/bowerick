/*
 *   Copyright 2014, Frankfurt University of Applied Sciences
 *
 *   This software is released under the terms of the Eclipse Public License 
 *   (EPL) 1.0. You can find a copy of the EPL at: 
 *   http://opensource.org/licenses/eclipse-1.0.php
 */

package bowerick;

import java.io.IOException;

import javax.jms.BytesMessage;
import javax.jms.Session;
import javax.jms.Connection;
import javax.jms.MessageProducer;
import javax.jms.JMSException;

import org.apache.activemq.command.Message;
import org.apache.activemq.util.ByteSequence;

import com.ning.compress.lzf.LZFEncoder;
import org.xerial.snappy.Snappy;

/**
 * Producer for sending the given number of {@link ByteMessagePayloadPart} instances in a single BytesMessage.
 *
 * @author Ruediger Gad
 *
 */
public class PooledBytesMessageProducer {

    private final MessageProducer producer;
    private final Session session;
    private final Connection connection;
    private final int poolSize;

    private BytesMessage msg;
    private int currentCount;

    private boolean compress = false;
    public enum CompressionMethod {Lzf, Snappy}
    private CompressionMethod compressionMethod = CompressionMethod.Lzf;

    public PooledBytesMessageProducer(MessageProducer producer, Session session,
                                      Connection connection, int poolSize) throws JMSException {
        this.producer = producer;
        this.session = session;
        this.connection = connection;
        this.poolSize = poolSize;

        init();
    }

    public void send(BytesMessagePayloadPart payloadPart) throws JMSException, IOException{
        payloadPart.appendToBytesMessage(msg);
        currentCount++;

        if (currentCount >= poolSize) {
            if (compress && msg instanceof Message) {
                Message castedMsg = (Message) msg;
                castedMsg.storeContent();
                ByteSequence origContent = castedMsg.getContent();

                ByteSequence compressedContent;
                switch (compressionMethod) {
                case Lzf:
                    compressedContent = new ByteSequence(LZFEncoder.encode(origContent.data));
                    break;
                case Snappy:
                    compressedContent = new ByteSequence(Snappy.compress(origContent.data));
                    break;
                default:
                    compressedContent = new ByteSequence(LZFEncoder.encode(origContent.data));
                    break;
                }

                castedMsg.setContent(compressedContent);
            }

            producer.send(msg);

            init();
        }
    }

    public void close() throws JMSException {
        connection.close();
    }

    public void setCompress(boolean compress) throws JMSException {
        this.compress = compress;
    }

    public boolean getCompress() {
        return compress;
    }

    public void setCompressionMethod(CompressionMethod compressionMethod) {
        this.compressionMethod = compressionMethod;
    }

    public CompressionMethod getCompressionMethod() {
        return compressionMethod;
    }

    protected void init() throws JMSException {
        msg = session.createBytesMessage();
        currentCount = 0;
    }
}

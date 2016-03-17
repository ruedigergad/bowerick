/*
 *   Copyright 2014, Frankfurt University of Applied Sciences
 *
 *   This software is released under the terms of the Eclipse Public License 
 *   (EPL) 1.0. You can find a copy of the EPL at: 
 *   http://opensource.org/licenses/eclipse-1.0.php
 */

package clj_jms_activemq_toolkit;

import javax.jms.BytesMessage;
import javax.jms.JMSException;

/**
 * Wraps a byte array for being send as pooled BytesMessage via {@link PooledBytesMessageProducer}.
 *
 * @author Ruediger Gad
 */
public class ByteArrayWrapper implements BytesMessagePayloadPart {

    final byte[] data;
    final int offset;
    final int length;

    public ByteArrayWrapper(byte[] data) {
        this.data = data;
        this.offset = 0;
        this.length = data.length;
    }

    public ByteArrayWrapper(byte[] data, int offset, int length) {
        this.data = data;
        this.offset = offset;
        this.length = length;
    }

    @Override
    public void appendToBytesMessage(BytesMessage msg) throws JMSException {
        msg.writeBytes(data, offset, length);
    }

}

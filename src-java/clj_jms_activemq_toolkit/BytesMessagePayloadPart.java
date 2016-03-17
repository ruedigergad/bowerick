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
 * Specifies the part of a payload of a BytesMessage as send via {@link PolledBytesMessageProducer}.
 * The implementing class has to take care of the serialization to a byte array representation.
 *
 * @author Ruediger Gad
 *
 */
public interface BytesMessagePayloadPart {

    public void appendToBytesMessage(BytesMessage msg) throws JMSException;

}

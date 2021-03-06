/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.messaging.rabbitmq.util;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import io.ballerina.runtime.api.Environment;
import io.ballerina.runtime.api.TypeTags;
import io.ballerina.runtime.api.utils.JsonUtils;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.utils.TypeUtils;
import io.ballerina.runtime.api.utils.XmlUtils;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.transactions.TransactionResourceManager;
import org.ballerinalang.messaging.rabbitmq.RabbitMQConstants;
import org.ballerinalang.messaging.rabbitmq.RabbitMQUtils;
import org.ballerinalang.messaging.rabbitmq.observability.RabbitMQMetricsUtil;
import org.ballerinalang.messaging.rabbitmq.observability.RabbitMQObservabilityConstants;
import org.ballerinalang.messaging.rabbitmq.observability.RabbitMQTracingUtil;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Util class for RabbitMQ Message handling.
 *
 * @since 1.1.0
 */
public class MessageUtils {
    public static Object basicAck(Environment environment, boolean multiple,
                                  boolean ackMode, boolean ackStatus, BObject callerObj) {
        Channel channel = (Channel) callerObj.getNativeData(RabbitMQConstants.CHANNEL_NATIVE_OBJECT);
        int deliveryTag =
                Integer.parseInt(callerObj.getNativeData(RabbitMQConstants.DELIVERY_TAG.getValue()).toString());
        if (ackStatus) {
            return RabbitMQUtils.returnErrorValue(RabbitMQConstants.MULTIPLE_ACK_ERROR);
        } else if (ackMode) {
            return RabbitMQUtils.returnErrorValue(RabbitMQConstants.ACK_MODE_ERROR);
        } else {
            try {
                channel.basicAck(deliveryTag, multiple);
                RabbitMQMetricsUtil.reportAcknowledgement(channel, RabbitMQObservabilityConstants.ACK);
                RabbitMQTracingUtil.traceResourceInvocation(channel, environment);
                if (TransactionResourceManager.getInstance().isInTransaction()) {
                    RabbitMQUtils.handleTransaction(callerObj);
                }
            } catch (IOException exception) {
                RabbitMQMetricsUtil.reportError(channel, RabbitMQObservabilityConstants.ERROR_TYPE_ACK);
                return RabbitMQUtils.returnErrorValue(RabbitMQConstants.ACK_ERROR + exception.getMessage());
            } catch (AlreadyClosedException exception) {
                RabbitMQMetricsUtil.reportError(channel, RabbitMQObservabilityConstants.ERROR_TYPE_ACK);
                return RabbitMQUtils.returnErrorValue(RabbitMQConstants.CHANNEL_CLOSED_ERROR);
            }
        }
        return null;
    }

    public static Object basicNack(Environment environment, boolean ackMode,
                                   boolean ackStatus, boolean multiple, boolean requeue, BObject callerObj) {
        Channel channel = (Channel) callerObj.getNativeData(RabbitMQConstants.CHANNEL_NATIVE_OBJECT);
        int deliveryTag =
                Integer.parseInt(callerObj.getNativeData(RabbitMQConstants.DELIVERY_TAG.getValue()).toString());
        if (ackStatus) {
            RabbitMQMetricsUtil.reportError(channel, RabbitMQObservabilityConstants.ERROR_TYPE_NACK);
            return RabbitMQUtils.returnErrorValue(RabbitMQConstants.MULTIPLE_ACK_ERROR);
        } else if (ackMode) {
            RabbitMQMetricsUtil.reportError(channel, RabbitMQObservabilityConstants.ERROR_TYPE_NACK);
            return RabbitMQUtils.returnErrorValue(RabbitMQConstants.ACK_MODE_ERROR);
        } else {
            try {
                channel.basicNack(deliveryTag, multiple, requeue);
                RabbitMQMetricsUtil.reportAcknowledgement(channel, RabbitMQObservabilityConstants.NACK);
                RabbitMQTracingUtil.traceResourceInvocation(channel, environment);
                if (TransactionResourceManager.getInstance().isInTransaction()) {
                    RabbitMQUtils.handleTransaction(callerObj);
                }
            } catch (IOException exception) {
                RabbitMQMetricsUtil.reportError(channel, RabbitMQObservabilityConstants.ERROR_TYPE_NACK);
                return RabbitMQUtils.returnErrorValue(RabbitMQConstants.NACK_ERROR
                        + exception.getMessage());
            } catch (AlreadyClosedException exception) {
                RabbitMQMetricsUtil.reportError(channel, RabbitMQObservabilityConstants.ERROR_TYPE_NACK);
                return RabbitMQUtils.returnErrorValue(RabbitMQConstants.CHANNEL_CLOSED_ERROR);
            }
        }
        return null;
    }

    public static Object getTextContent(BArray messageContent) {
        byte[] messageCont = messageContent.getBytes();
        try {
            return StringUtils.fromString(new String(messageCont, StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException exception) {
            RabbitMQMetricsUtil.reportError(RabbitMQObservabilityConstants.ERROR_TYPE_GET_MSG_CONTENT);
            return RabbitMQUtils.returnErrorValue(RabbitMQConstants.TEXT_CONTENT_ERROR
                    + exception.getMessage());
        }
    }

    public static Object getFloatContent(BArray messageContent) {
        try {
            return Double.parseDouble(new String(messageContent.getBytes(), StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException exception) {
            RabbitMQMetricsUtil.reportError(RabbitMQObservabilityConstants.ERROR_TYPE_GET_MSG_CONTENT);
            return RabbitMQUtils.returnErrorValue(RabbitMQConstants.FLOAT_CONTENT_ERROR
                    + exception.getMessage());
        }
    }

    public static Object getIntContent(BArray messageContent) {
        try {
            return Integer.parseInt(new String(messageContent.getBytes(), StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException exception) {
            RabbitMQMetricsUtil.reportError(RabbitMQObservabilityConstants.ERROR_TYPE_GET_MSG_CONTENT);
            return RabbitMQUtils.returnErrorValue(RabbitMQConstants.INT_CONTENT_ERROR
                    + exception.getMessage());
        }
    }

    public static Object getJSONContent(BArray messageContent) {
        try {
            Object json = JsonUtils.parse(new String(messageContent.getBytes(), StandardCharsets.UTF_8.name()));
            if (json instanceof String) {
                return StringUtils.fromString((String) json);
            }
            return json;
        } catch (UnsupportedEncodingException exception) {
            RabbitMQMetricsUtil.reportError(RabbitMQObservabilityConstants.ERROR_TYPE_GET_MSG_CONTENT);
            return RabbitMQUtils.returnErrorValue
                    (RabbitMQConstants.JSON_CONTENT_ERROR + exception.getMessage());
        }
    }

    public static Object getXMLContent(BArray messageContent) {
        try {
            return XmlUtils.parse(new String(messageContent.getBytes(), StandardCharsets.UTF_8.name()));
        } catch (UnsupportedEncodingException exception) {
            RabbitMQMetricsUtil.reportError(RabbitMQObservabilityConstants.ERROR_TYPE_GET_MSG_CONTENT);
            return RabbitMQUtils.returnErrorValue(RabbitMQConstants.XML_CONTENT_ERROR
                    + exception.getMessage());
        }
    }

    public static byte[] convertDataIntoByteArray(Object data) {
        int typeTag = TypeUtils.getType(data).getTag();
        if (typeTag == TypeTags.STRING_TAG) {
            return ((BString) data).getValue().getBytes(StandardCharsets.UTF_8);
        } else {
            return ((BArray) data).getBytes();
        }
    }

    private MessageUtils() {
    }
}

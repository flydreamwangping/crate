/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.elasticsearch.transport;

import java.io.IOException;
import java.util.Collections;

import org.elasticsearch.Version;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.test.ESTestCase;
import org.junit.Test;

public class InboundMessageTests extends ESTestCase {

    private final NamedWriteableRegistry registry = new NamedWriteableRegistry(Collections.emptyList());

    @Test
    public void testReadRequest() throws IOException {
        String value = randomAlphaOfLength(10);
        Message message = new Message(value);
        String action = randomAlphaOfLength(10);
        long requestId = randomLong();
        boolean isHandshake = randomBoolean();
        boolean compress = randomBoolean();
        Version version = randomFrom(Version.CURRENT, Version.CURRENT.minimumCompatibilityVersion());
        OutboundMessage.Request request = new OutboundMessage.Request(message, version, action, requestId,
            isHandshake, compress);
        BytesReference reference;
        try (BytesStreamOutput streamOutput = new BytesStreamOutput()) {
            reference = request.serialize(streamOutput);
        }

        InboundMessage.Reader reader = new InboundMessage.Reader(version, registry);
        BytesReference sliced = reference.slice(6, reference.length() - 6);
        InboundMessage.Request inboundMessage = (InboundMessage.Request) reader.deserialize(sliced);

        assertEquals(isHandshake, inboundMessage.isHandshake());
        assertEquals(compress, inboundMessage.isCompress());
        assertEquals(version, inboundMessage.getVersion());
        assertEquals(action, inboundMessage.getActionName());
        assertTrue(inboundMessage.isRequest());
        assertFalse(inboundMessage.isResponse());
        assertFalse(inboundMessage.isError());
        assertEquals(value, new Message(inboundMessage.getStreamInput()).value);
    }

    @Test
    public void testReadResponse() throws IOException {
        String value = randomAlphaOfLength(10);
        Message message = new Message(value);
        long requestId = randomLong();
        boolean isHandshake = randomBoolean();
        boolean compress = randomBoolean();
        Version version = randomFrom(Version.CURRENT, Version.CURRENT.minimumCompatibilityVersion());
        OutboundMessage.Response request = new OutboundMessage.Response(
             message, version, requestId, isHandshake, compress);
        BytesReference reference;
        try (BytesStreamOutput streamOutput = new BytesStreamOutput()) {
            reference = request.serialize(streamOutput);
        }

        InboundMessage.Reader reader = new InboundMessage.Reader(version, registry);
        BytesReference sliced = reference.slice(6, reference.length() - 6);
        InboundMessage.Response inboundMessage = (InboundMessage.Response) reader.deserialize(sliced);
        assertEquals(isHandshake, inboundMessage.isHandshake());
        assertEquals(compress, inboundMessage.isCompress());
        assertEquals(version, inboundMessage.getVersion());
        assertTrue(inboundMessage.isResponse());
        assertFalse(inboundMessage.isRequest());
        assertFalse(inboundMessage.isError());
        assertEquals(value, new Message(inboundMessage.getStreamInput()).value);
    }

    @Test
    public void testReadErrorResponse() throws IOException {
        RemoteTransportException exception = new RemoteTransportException("error", new IOException());
        long requestId = randomLong();
        boolean isHandshake = randomBoolean();
        boolean compress = randomBoolean();
        Version version = randomFrom(Version.CURRENT, Version.CURRENT.minimumCompatibilityVersion());
        OutboundMessage.Response request = new OutboundMessage.Response(
            exception, version, requestId, isHandshake, compress);
        BytesReference reference;
        try (BytesStreamOutput streamOutput = new BytesStreamOutput()) {
            reference = request.serialize(streamOutput);
        }

        InboundMessage.Reader reader = new InboundMessage.Reader(version, registry);
        BytesReference sliced = reference.slice(6, reference.length() - 6);
        InboundMessage.Response inboundMessage = (InboundMessage.Response) reader.deserialize(sliced);
        assertEquals(isHandshake, inboundMessage.isHandshake());
        assertEquals(compress, inboundMessage.isCompress());
        assertEquals(version, inboundMessage.getVersion());
        assertTrue(inboundMessage.isResponse());
        assertFalse(inboundMessage.isRequest());
        assertTrue(inboundMessage.isError());
        assertEquals("[error]", inboundMessage.getStreamInput().readException().getMessage());
    }

    private static final class Message extends TransportMessage {

        public String value;

        private Message() {
        }

        private Message(StreamInput in) throws IOException {
            value = in.readString();
        }

        private Message(String value) {
            this.value = value;
        }

        @Override
        public void writeTo(StreamOutput out) throws IOException {
            out.writeString(value);
        }
    }
}

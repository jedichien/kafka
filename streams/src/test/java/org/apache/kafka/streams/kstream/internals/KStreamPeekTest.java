/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams.kstream.internals;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.ForeachAction;
import org.apache.kafka.streams.kstream.PrintForeachAction;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.KStreamBuilder;
import org.apache.kafka.test.KStreamTestDriver;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import java.io.PrintWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class KStreamPeekTest {

    private final String topicName = "topic";
    private final Serde<Integer> intSerd = Serdes.Integer();
    private final Serde<String> stringSerd = Serdes.String();
    private PrintWriter printWriter;
    private ByteArrayOutputStream byteOutStream;
    private KStreamTestDriver driver = null;


    @Before
    public void setUp() {
        byteOutStream = new ByteArrayOutputStream();
        printWriter = new PrintWriter(new OutputStreamWriter(byteOutStream, StandardCharsets.UTF_8));
    }

    @After
    public void cleanup() {
        if (driver != null) {
            driver.close();
        }
    }

    @Test
    public void shouldObserveStreamElements() {
        final KStreamBuilder builder = new KStreamBuilder();
        final KStream<Integer, String> stream = builder.stream(intSerd, stringSerd, topicName);
        final List<KeyValue<Integer, String>> peekObserved = new ArrayList<>(), streamObserved = new ArrayList<>();
        stream.peek(collect(peekObserved)).foreach(collect(streamObserved));

        driver = new KStreamTestDriver(builder);
        final List<KeyValue<Integer, String>> expected = new ArrayList<>();
        for (int key = 0; key < 32; key++) {
            final String value = "V" + key;
            driver.process(topicName, key, value);
            expected.add(new KeyValue<>(key, value));
        }

        assertEquals(expected, peekObserved);
        assertEquals(expected, streamObserved);
    }

    @Test
    public void shouldNotAllowNullAction() {
        final KStreamBuilder builder = new KStreamBuilder();
        final KStream<Integer, String> stream = builder.stream(intSerd, stringSerd, topicName);
        try {
            stream.peek(null);
            fail("expected null action to throw NPE");
        } catch (NullPointerException expected) { }
    }

    @Test
    public void testPrintKeyValueWithName() {
        final KStreamPeek<Integer, String> kStreamPeek = new KStreamPeek<>(new PrintForeachAction(printWriter, intSerd, stringSerd, "test-stream"), false);

        final List<KeyValue<Integer, String>> inputRecords = Arrays.asList(
                new KeyValue<>(0, "zero"),
                new KeyValue<>(1, "one"),
                new KeyValue<>(2, "two"),
                new KeyValue<>(3, "three"));
        
        final String[] expectedResult = {"[test-stream]: 0, zero", "[test-stream]: 1, one", "[test-stream]: 2, two", "[test-stream]: 3, three"};
        
        final KStreamBuilder builder = new KStreamBuilder();
        final KStream<Integer, String> stream = builder.stream(intSerd, stringSerd, topicName);
        stream.process(kStreamPeek);
        
        driver = new KStreamTestDriver(builder);
        for (KeyValue<Integer, String> record: inputRecords) {
            driver.process(topicName, record.key, record.value);
        }
        printWriter.flush();
        final String[] flushOutDatas = new String(byteOutStream.toByteArray(), Charset.forName("UTF-8")).split("\n");
        for (int i = 0; i < flushOutDatas.length; i++) {
            assertEquals(flushOutDatas[i], expectedResult[i]);
        }
    }

    private static <K, V> ForeachAction<K, V> collect(final List<KeyValue<K, V>> into) {
        return new ForeachAction<K, V>() {
            @Override
            public void apply(final K key, final V value) {
                into.add(new KeyValue<>(key, value));
            }
        };
    }
}

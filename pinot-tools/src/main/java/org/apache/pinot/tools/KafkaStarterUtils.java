/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.apache.pinot.common.utils.ZkStarter;
import org.apache.pinot.core.realtime.stream.StreamDataProvider;
import org.apache.pinot.core.realtime.stream.StreamDataServerStartable;


public class KafkaStarterUtils {
  public static final int DEFAULT_BROKER_ID = 0;
  public static final String DEFAULT_ZK_STR = ZkStarter.DEFAULT_ZK_STR + "/kafka";
  public static int DEFAULT_KAFKA_PORT = 19092;
  public static final String DEFAULT_KAFKA_BROKER = "localhost:" + DEFAULT_KAFKA_PORT;

  public static final String PORT = "port";
  public static final String BROKER_ID = "broker.id";
  private static final String ZOOKEEPER_CONNECT = "zookeeper.connect";
  private static final String LOG_DIRS = "log.dirs";

  public static final String KAFKA_SERVER_STARTABLE_CLASS_NAME =
      "org.apache.pinot.core.realtime.impl.kafka.server.KafkaDataServerStartable";
  public static final String KAFKA_PRODUCER_CLASS_NAME =
      "org.apache.pinot.core.realtime.impl.kafka.server.KafkaDataProducer";
  public static final String KAFKA_JSON_MESSAGE_DECODER_CLASS_NAME =
      "org.apache.pinot.core.realtime.impl.kafka.KafkaJSONMessageDecoder";

  public static Properties getDefaultKafkaConfiguration() {
    final Properties configuration = new Properties();

    // Enable topic deletion by default for integration tests
    configureTopicDeletion(configuration, true);

    // Set host name
    configureHostName(configuration, "localhost");

    configuration.put(PORT, DEFAULT_KAFKA_PORT);
    configuration.put(BROKER_ID, DEFAULT_BROKER_ID);
    configuration.put(ZOOKEEPER_CONNECT, DEFAULT_ZK_STR);
    configuration.put(LOG_DIRS, "/tmp/kafka-" + Double.toHexString(Math.random()));

    return configuration;
  }

  public static void configureTopicDeletion(Properties configuration, boolean topicDeletionEnabled) {
    configuration.put("delete.topic.enable", Boolean.toString(topicDeletionEnabled));
  }

  public static void configureHostName(Properties configuration, String hostName) {
    configuration.put("host.name", hostName);
  }

  public static Properties getTopicCreationProps(int numKafkaPartitions) {
    Properties topicProps = new Properties();
    topicProps.put("partition", numKafkaPartitions);
    return topicProps;
  }

  public static List<StreamDataServerStartable> startServers(final int brokerCount, final int port, final String zkStr,
      final Properties configuration) {
    List<StreamDataServerStartable> startables = new ArrayList<>(brokerCount);

    for (int i = 0; i < brokerCount; i++) {
      startables.add(startServer(port + i, i, zkStr, configuration));
    }
    return startables;
  }

  public static StreamDataServerStartable startServer(final int port, final int brokerId, final String zkStr,
      final Properties configuration) {
    StreamDataServerStartable kafkaStarter;
    try {
      configuration.put(KafkaStarterUtils.PORT, port);
      configuration.put(KafkaStarterUtils.BROKER_ID, brokerId);
      configuration.put(KafkaStarterUtils.ZOOKEEPER_CONNECT, zkStr);
      configuration.put(KafkaStarterUtils.LOG_DIRS, "/tmp/kafka-" + Double.toHexString(Math.random()));
      kafkaStarter = StreamDataProvider.getServerDataStartable(KAFKA_SERVER_STARTABLE_CLASS_NAME, configuration);
    } catch (Exception e) {
      throw new RuntimeException("Failed to start " + KAFKA_SERVER_STARTABLE_CLASS_NAME, e);
    }
    kafkaStarter.start();
    return kafkaStarter;
  }
}

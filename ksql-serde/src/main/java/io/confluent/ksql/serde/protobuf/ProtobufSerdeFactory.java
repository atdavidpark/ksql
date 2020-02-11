/*
 * Copyright 2020 Confluent Inc.
 *
 * Licensed under the Confluent Community License (the "License"; you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at
 *
 * http://www.confluent.io/confluent-community-license
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OF ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package io.confluent.ksql.serde.protobuf;

import io.confluent.connect.protobuf.ProtobufConverter;
import io.confluent.connect.protobuf.ProtobufConverterConfig;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.ksql.schema.ksql.PersistenceSchema;
import io.confluent.ksql.serde.KsqlSerdeFactory;
import io.confluent.ksql.serde.connect.ConnectDataTranslator;
import io.confluent.ksql.serde.connect.KsqlConnectDeserializer;
import io.confluent.ksql.serde.connect.KsqlConnectSerializer;
import io.confluent.ksql.serde.tls.ThreadLocalDeserializer;
import io.confluent.ksql.serde.tls.ThreadLocalSerializer;
import io.confluent.ksql.util.KsqlConfig;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

public class ProtobufSerdeFactory implements KsqlSerdeFactory {

  @Override
  public void validate(final PersistenceSchema schema) {
    // Supports all types
  }

  @Override
  public Serde<Object> createSerde(
      final PersistenceSchema schema,
      final KsqlConfig ksqlConfig,
      final Supplier<SchemaRegistryClient> schemaRegistryClientFactory
  ) {
    final Supplier<Serializer<Object>> serializer = () -> createSerializer(
        schema,
        ksqlConfig,
        schemaRegistryClientFactory
    );
    final Supplier<Deserializer<Object>> deserializer = () -> createDeserializer(
        schema,
        ksqlConfig,
        schemaRegistryClientFactory
    );

    // Sanity check:
    serializer.get();
    deserializer.get();

    return Serdes.serdeFrom(
        new ThreadLocalSerializer<>(serializer),
        new ThreadLocalDeserializer<>(deserializer)
    );
  }

  private KsqlConnectSerializer createSerializer(
      final PersistenceSchema schema,
      final KsqlConfig ksqlConfig,
      final Supplier<SchemaRegistryClient> schemaRegistryClientFactory
  ) {
    final ProtobufConverter converter = getConverter(schemaRegistryClientFactory.get(), ksqlConfig);

    return new KsqlConnectSerializer(
        schema.serializedSchema(),
        new ConnectDataTranslator(schema.serializedSchema()),
        converter
    );
  }

  private KsqlConnectDeserializer createDeserializer(
      final PersistenceSchema schema,
      final KsqlConfig ksqlConfig,
      final Supplier<SchemaRegistryClient> schemaRegistryClientFactory
  ) {
    final ProtobufConverter converter = getConverter(schemaRegistryClientFactory.get(), ksqlConfig);

    return new KsqlConnectDeserializer(
        converter,
        new ConnectDataTranslator(schema.serializedSchema())
    );
  }

  private ProtobufConverter getConverter(
      final SchemaRegistryClient schemaRegistryClient,
      final KsqlConfig ksqlConfig
  ) {
    final Map<String, Object> protobufConfig = ksqlConfig
        .originalsWithPrefix(KsqlConfig.KSQL_SCHEMA_REGISTRY_PREFIX);

    protobufConfig.put(
        ProtobufConverterConfig.SCHEMA_REGISTRY_URL_CONFIG,
        ksqlConfig.getString(KsqlConfig.SCHEMA_REGISTRY_URL_PROPERTY)
    );

    final ProtobufConverter converter = new ProtobufConverter(schemaRegistryClient);
    converter.configure(protobufConfig, false);

    return converter;
  }

}
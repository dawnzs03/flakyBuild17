// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: scalardb.proto

package com.scalar.db.rpc;

public interface MutationOrBuilder extends
    // @@protoc_insertion_point(interface_extends:rpc.Mutation)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string namespace = 1;</code>
   * @return The namespace.
   */
  java.lang.String getNamespace();
  /**
   * <code>string namespace = 1;</code>
   * @return The bytes for namespace.
   */
  com.google.protobuf.ByteString
      getNamespaceBytes();

  /**
   * <code>string table = 2;</code>
   * @return The table.
   */
  java.lang.String getTable();
  /**
   * <code>string table = 2;</code>
   * @return The bytes for table.
   */
  com.google.protobuf.ByteString
      getTableBytes();

  /**
   * <code>.rpc.Key partition_key = 3;</code>
   * @return Whether the partitionKey field is set.
   */
  boolean hasPartitionKey();
  /**
   * <code>.rpc.Key partition_key = 3;</code>
   * @return The partitionKey.
   */
  com.scalar.db.rpc.Key getPartitionKey();
  /**
   * <code>.rpc.Key partition_key = 3;</code>
   */
  com.scalar.db.rpc.KeyOrBuilder getPartitionKeyOrBuilder();

  /**
   * <code>.rpc.Key clustering_key = 4;</code>
   * @return Whether the clusteringKey field is set.
   */
  boolean hasClusteringKey();
  /**
   * <code>.rpc.Key clustering_key = 4;</code>
   * @return The clusteringKey.
   */
  com.scalar.db.rpc.Key getClusteringKey();
  /**
   * <code>.rpc.Key clustering_key = 4;</code>
   */
  com.scalar.db.rpc.KeyOrBuilder getClusteringKeyOrBuilder();

  /**
   * <code>.rpc.Consistency consistency = 5;</code>
   * @return The enum numeric value on the wire for consistency.
   */
  int getConsistencyValue();
  /**
   * <code>.rpc.Consistency consistency = 5;</code>
   * @return The consistency.
   */
  com.scalar.db.rpc.Consistency getConsistency();

  /**
   * <code>.rpc.MutateCondition condition = 6;</code>
   * @return Whether the condition field is set.
   */
  boolean hasCondition();
  /**
   * <code>.rpc.MutateCondition condition = 6;</code>
   * @return The condition.
   */
  com.scalar.db.rpc.MutateCondition getCondition();
  /**
   * <code>.rpc.MutateCondition condition = 6;</code>
   */
  com.scalar.db.rpc.MutateConditionOrBuilder getConditionOrBuilder();

  /**
   * <code>.rpc.Mutation.Type type = 7;</code>
   * @return The enum numeric value on the wire for type.
   */
  int getTypeValue();
  /**
   * <code>.rpc.Mutation.Type type = 7;</code>
   * @return The type.
   */
  com.scalar.db.rpc.Mutation.Type getType();

  /**
   * <pre>
   * only for Put operations
   * </pre>
   *
   * <code>repeated .rpc.Value value = 8 [deprecated = true];</code>
   */
  @java.lang.Deprecated java.util.List<com.scalar.db.rpc.Value> 
      getValueList();
  /**
   * <pre>
   * only for Put operations
   * </pre>
   *
   * <code>repeated .rpc.Value value = 8 [deprecated = true];</code>
   */
  @java.lang.Deprecated com.scalar.db.rpc.Value getValue(int index);
  /**
   * <pre>
   * only for Put operations
   * </pre>
   *
   * <code>repeated .rpc.Value value = 8 [deprecated = true];</code>
   */
  @java.lang.Deprecated int getValueCount();
  /**
   * <pre>
   * only for Put operations
   * </pre>
   *
   * <code>repeated .rpc.Value value = 8 [deprecated = true];</code>
   */
  @java.lang.Deprecated java.util.List<? extends com.scalar.db.rpc.ValueOrBuilder> 
      getValueOrBuilderList();
  /**
   * <pre>
   * only for Put operations
   * </pre>
   *
   * <code>repeated .rpc.Value value = 8 [deprecated = true];</code>
   */
  @java.lang.Deprecated com.scalar.db.rpc.ValueOrBuilder getValueOrBuilder(
      int index);

  /**
   * <pre>
   * only for Put operations
   * </pre>
   *
   * <code>repeated .rpc.Column columns = 9;</code>
   */
  java.util.List<com.scalar.db.rpc.Column> 
      getColumnsList();
  /**
   * <pre>
   * only for Put operations
   * </pre>
   *
   * <code>repeated .rpc.Column columns = 9;</code>
   */
  com.scalar.db.rpc.Column getColumns(int index);
  /**
   * <pre>
   * only for Put operations
   * </pre>
   *
   * <code>repeated .rpc.Column columns = 9;</code>
   */
  int getColumnsCount();
  /**
   * <pre>
   * only for Put operations
   * </pre>
   *
   * <code>repeated .rpc.Column columns = 9;</code>
   */
  java.util.List<? extends com.scalar.db.rpc.ColumnOrBuilder> 
      getColumnsOrBuilderList();
  /**
   * <pre>
   * only for Put operations
   * </pre>
   *
   * <code>repeated .rpc.Column columns = 9;</code>
   */
  com.scalar.db.rpc.ColumnOrBuilder getColumnsOrBuilder(
      int index);
}
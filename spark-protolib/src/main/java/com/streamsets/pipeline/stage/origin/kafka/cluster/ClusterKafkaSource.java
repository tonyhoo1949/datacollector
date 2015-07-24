/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.stage.origin.kafka.cluster;

import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.ErrorListener;
import com.streamsets.pipeline.api.OffsetCommitter;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.api.StageException;
import com.streamsets.pipeline.stage.origin.kafka.BaseKafkaSource;
import com.streamsets.pipeline.stage.origin.kafka.SourceArguments;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;

/**
 * Ingests kafka produce data from spark streaming
 */
public class ClusterKafkaSource extends BaseKafkaSource implements OffsetCommitter, ClusterSource, ErrorListener {
  private static final Logger LOG = LoggerFactory.getLogger(ClusterKafkaSource.class);

  private final ClusterQueue clusterQueue;
  private final ClusterQueueConsumer clusterQueueConsumer;

  public ClusterKafkaSource(SourceArguments args) {
    super(args);
    this.clusterQueue = new ClusterQueue();
    this.clusterQueueConsumer = new ClusterQueueConsumer(clusterQueue);
  }

  private String getRecordId(String topic) {
    return "spark-streaming" + "::" + topic;
  }

  @Override
  public String produce(String lastSourceOffset, int maxBatchSize, BatchMaker batchMaker) throws StageException {
    // Ignore the batch size
    OffsetAndResult<MessageAndPartition> offsetAndResult = clusterQueueConsumer.produce(maxWaitTime);
    for (MessageAndPartition messageAndPartition : offsetAndResult.getResult()) {
      String messageId = getRecordId(topic);
      List<Record> records = processKafkaMessage(messageId, messageAndPartition.getPayload());
      for (Record record : records) {
        batchMaker.addRecord(record);
      }
    }
    return offsetAndResult.getOffset();
  }

  @Override
  public long getRecordsProduced() {
    return clusterQueueConsumer.getRecordsProduced();
  }

  @Override
  public void init() {
    LOG.info("Successfully initialized Spark Kafka Consumer");
  }

  @Override
  public void destroy() {
    //
  }

  @Override
  public void commit(String offset) throws StageException {
    clusterQueueConsumer.commit(offset);
  }

  @Override
  public <T> void put(List<T> batch) throws InterruptedException {
    clusterQueue.putData(batch);
  }

  @Override
  public void errorNotification(Throwable throwable) {
    clusterQueueConsumer.errorNotification(throwable);
  }

  @Override
  public boolean inErrorState() {
    return clusterQueue.inErrorState();
  }
}
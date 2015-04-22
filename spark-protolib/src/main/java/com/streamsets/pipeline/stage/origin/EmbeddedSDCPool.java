/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.stage.origin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;

import com.streamsets.pipeline.BootstrapSpark;
import com.streamsets.pipeline.api.Source;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmbeddedSDCPool {
  private static final Logger LOG = LoggerFactory.getLogger(EmbeddedSDCPool.class);
  private ConcurrentLinkedDeque<EmbeddedSDC> concurrentQueue = new ConcurrentLinkedDeque<EmbeddedSDC>();
  private final List<EmbeddedSDC> totalInstances = new CopyOnWriteArrayList<>();
  private Properties properties;
  private String pipelineJson;


  public EmbeddedSDCPool(Properties properties, String pipelineJson) throws Exception {
    this.properties = properties;
    this.pipelineJson = pipelineJson;
    createEmbeddedSDC();
  }

  private EmbeddedSDC createEmbeddedSDC() throws Exception {
    final EmbeddedSDC embeddedSDC = new EmbeddedSDC();
    Object source = BootstrapSpark.createPipeline(properties, pipelineJson, new Runnable() { // post-batch runnable
      @Override
      public void run() {
        LOG.debug("Returning SDC instance {} back to queue", embeddedSDC.getInstanceId());
        returnEmbeddedSDC(embeddedSDC);
      }
    });
    if (!(source instanceof SparkStreamingSource)) {
      throw new IllegalArgumentException("Source is not of type SparkStreamingSource: " + source.getClass().getName());
    }
    embeddedSDC.setSource((SparkStreamingSource)source);
    totalInstances.add(embeddedSDC);
    concurrentQueue.add(embeddedSDC);
    LOG.debug("After adding, size of queue is " + concurrentQueue.size());
    return embeddedSDC;
  }

  public EmbeddedSDC getEmbeddedSDC() throws Exception {
    LOG.debug("Before polling, size of queue is " + concurrentQueue.size());
    EmbeddedSDC embeddedSDC = concurrentQueue.poll();
    if (embeddedSDC == null) {
      LOG.debug("No SDC found in queue, creating new one");
      embeddedSDC = createEmbeddedSDC();
    }
    return embeddedSDC;
  }

  public int size() {
    return concurrentQueue.size();
  }

  public void returnEmbeddedSDC(EmbeddedSDC embeddedSDC) {
    concurrentQueue.offer(embeddedSDC);
    LOG.debug("After returning an SDC, size of queue is " + concurrentQueue.size());
  }

  public void destoryEmbeddedSDC() {
    //
  }

  public List<EmbeddedSDC> getTotalInstances() {
    return totalInstances;
  }
}

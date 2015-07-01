/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */

package com.streamsets.pipeline.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsets.dataCollector.execution.PipelineStatus;
import com.streamsets.dataCollector.execution.Runner;
import com.streamsets.pipeline.callback.CallbackInfo;
import com.streamsets.pipeline.json.ObjectMapperFactory;
import com.streamsets.pipeline.main.RuntimeInfo;
import com.streamsets.pipeline.prodmanager.PipelineManager;
import com.streamsets.pipeline.prodmanager.State;
import com.streamsets.pipeline.restapi.bean.CounterJson;
import com.streamsets.pipeline.restapi.bean.MeterJson;
import com.streamsets.pipeline.restapi.bean.MetricRegistryJson;
import com.streamsets.pipeline.runner.production.ThreadHealthReporter;
import com.streamsets.pipeline.store.PipelineStoreException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsEventRunnable implements Runnable {
  public static final String RUNNABLE_NAME = "MetricsEventRunnable";
  private final static Logger LOG = LoggerFactory.getLogger(MetricsEventRunnable.class);
  private final List<MetricsEventListener> metricsEventListenerList = new ArrayList<>();
  private final Map<String, MetricRegistryJson> slaveMetrics;

  private final PipelineManager pipelineManager;
  private final RuntimeInfo runtimeInfo;
  private ThreadHealthReporter threadHealthReporter;
  private final int scheduledDelay;
  private final Runner runner;

  // TODO - Remove pipelineManager after multi pipeline support
  public MetricsEventRunnable(PipelineManager pipelineManager, RuntimeInfo runtimeInfo, int scheduledDelay, Runner runner) {
    this.pipelineManager = pipelineManager;
    this.runtimeInfo = runtimeInfo;
    this.scheduledDelay = scheduledDelay/1000;
    slaveMetrics = new HashMap<>();
    this.runner = runner;
  }

  public void addMetricsEventListener(MetricsEventListener metricsEventListener) {
    metricsEventListenerList.add(metricsEventListener);
  }

  public void removeMetricsEventListener(MetricsEventListener metricsEventListener) {
    metricsEventListenerList.remove(metricsEventListener);
  }

  public void clearSlaveMetrics() {
    slaveMetrics.clear();
  }

  public void setThreadHealthReporter(ThreadHealthReporter threadHealthReporter) {
    this.threadHealthReporter = threadHealthReporter;
  }

  @Override
  public void run() {
    //Added log trace to debug SDC-725
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    Date now = new Date();
    LOG.trace("MetricsEventRunnable Run - " + sdf.format(now));
    try {
      if(threadHealthReporter != null) {
        threadHealthReporter.reportHealth(RUNNABLE_NAME, scheduledDelay, System.currentTimeMillis());
      }
      boolean isRunning = false;
      if (pipelineManager == null) {
        isRunning = runner.getStatus().isActive();
      } else {
        isRunning =
          pipelineManager.getPipelineState() != null && pipelineManager.getPipelineState().getState() == State.RUNNING;
      }

      if (metricsEventListenerList.size() > 0 && isRunning) {
        ObjectMapper objectMapper = ObjectMapperFactory.get();

        String metricsJSONStr;

        if(runtimeInfo.getExecutionMode().equals(RuntimeInfo.ExecutionMode.CLUSTER)) {
          MetricRegistryJson metricRegistryJson = getAggregatedMetrics();
          metricsJSONStr = objectMapper.writer().writeValueAsString(metricRegistryJson);
        } else {
          metricsJSONStr =
            objectMapper.writer().writeValueAsString(
              pipelineManager != null ? pipelineManager.getMetrics() : runner.getMetrics());
        }

        for(MetricsEventListener alertEventListener : metricsEventListenerList) {
          try {
            alertEventListener.notification(metricsJSONStr);
          } catch(Exception ex) {
            LOG.warn("Error while notifying metrics, {}", ex.getMessage(), ex);
          }
        }
      }
    } catch (IOException ex) {
      LOG.warn("Error while serializing metrics, {}", ex.getMessage(), ex);
    } catch (PipelineStoreException ex) {
      LOG.warn("Error while fetching status of pipeline,  {}", ex.getMessage(), ex);
    }
  }

  public MetricRegistryJson getAggregatedMetrics() {
    MetricRegistryJson aggregatedMetrics = new MetricRegistryJson();
    Map<String, CounterJson> aggregatedCounters = null;
    Map<String, MeterJson> aggregatedMeters = null;
    List<String> slaves = new ArrayList<>();

    for(CallbackInfo callbackInfo : pipelineManager.getSlaveCallbackList()) {
      slaves.add(callbackInfo.getSdcURL());
      MetricRegistryJson metricRegistryJson = callbackInfo.getMetricRegistryJson();
      if(metricRegistryJson != null) {
        slaveMetrics.put(callbackInfo.getSdcSlaveToken(), callbackInfo.getMetricRegistryJson());
      }
    }

    for(String slaveSdcToken: slaveMetrics.keySet()) {
      MetricRegistryJson metrics = slaveMetrics.get(slaveSdcToken);
      if(aggregatedCounters == null) {
        //First Slave Metrics
        aggregatedCounters = metrics.getCounters();
        aggregatedMeters = metrics.getMeters();
      } else {
        //Otherwise add to the aggregated Metrics
        Map<String, CounterJson> slaveCounters = metrics.getCounters();
        for(String meterName: aggregatedCounters.keySet()) {
          CounterJson aggregatedCounter = aggregatedCounters.get(meterName);
          CounterJson slaveCounter = slaveCounters.get(meterName);
          aggregatedCounter.setCount(aggregatedCounter.getCount() + slaveCounter.getCount());
        }

        Map<String, MeterJson> slaveMeters = metrics.getMeters();
        for(String meterName: aggregatedMeters.keySet()) {
          MeterJson aggregatedMeter = aggregatedMeters.get(meterName);
          MeterJson slaveMeter = slaveMeters.get(meterName);
          aggregatedMeter.setCount(aggregatedMeter.getCount() + slaveMeter.getCount());

          aggregatedMeter.setM1_rate(aggregatedMeter.getM1_rate() + slaveMeter.getM1_rate());
          aggregatedMeter.setM5_rate(aggregatedMeter.getM5_rate() + slaveMeter.getM5_rate());
          aggregatedMeter.setM15_rate(aggregatedMeter.getM15_rate() + slaveMeter.getM15_rate());
          aggregatedMeter.setM30_rate(aggregatedMeter.getM30_rate() + slaveMeter.getM30_rate());

          aggregatedMeter.setH1_rate(aggregatedMeter.getH1_rate() + slaveMeter.getH1_rate());
          aggregatedMeter.setH6_rate(aggregatedMeter.getH6_rate() + slaveMeter.getH6_rate());
          aggregatedMeter.setH12_rate(aggregatedMeter.getH12_rate() + slaveMeter.getH12_rate());
          aggregatedMeter.setH24_rate(aggregatedMeter.getH24_rate() + slaveMeter.getH24_rate());

          aggregatedMeter.setMean_rate(aggregatedMeter.getMean_rate() + slaveMeter.getMean_rate());

        }
      }
    }

    aggregatedMetrics.setCounters(aggregatedCounters);
    aggregatedMetrics.setMeters(aggregatedMeters);
    aggregatedMetrics.setSlaves(slaves);

    return aggregatedMetrics;
  }
}

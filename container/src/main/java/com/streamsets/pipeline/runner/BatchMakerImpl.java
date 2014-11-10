/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.streamsets.pipeline.runner;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.streamsets.pipeline.api.BatchMaker;
import com.streamsets.pipeline.api.Record;
import com.streamsets.pipeline.record.RecordImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BatchMakerImpl implements BatchMaker {
  private final StagePipe stagePipe;
  private final String instanceName;
  private final Set<String> outputLanes;
  private final String singleOutputLane;
  private final Map<String, List<Record>> stageOutput;
  private final Map<String, List<Record>> stageOutputSnapshot;
  private int recordAllowance;
  private int size;

  public BatchMakerImpl(StagePipe stagePipe, boolean keepSnapshot) {
    this(stagePipe, keepSnapshot, Integer.MAX_VALUE);
  }

  public BatchMakerImpl(StagePipe stagePipe, boolean keepSnapshot, int recordAllowance) {
    this.stagePipe = stagePipe;
    this.instanceName= stagePipe.getStage().getInfo().getInstanceName();
    outputLanes = ImmutableSet.copyOf(stagePipe.getStage().getConfiguration().getOutputLanes());
    singleOutputLane = (outputLanes.size() == 1) ? outputLanes.iterator().next() : null;
    stageOutput = new HashMap<String, List<Record>>();
    stageOutputSnapshot = (keepSnapshot) ? new HashMap<String, List<Record>>() : null;
    for (String outputLane : outputLanes) {
      stageOutput.put(outputLane, new ArrayList<Record>());
      if (stageOutputSnapshot != null) {
        stageOutputSnapshot.put(outputLane, new ArrayList<Record>());
      }
    }
    this.recordAllowance = recordAllowance;
  }

  public StagePipe getStagePipe() {
    return stagePipe;
  }

  @Override
  public Set<String> getLanes() {
    return outputLanes;
  }

  @Override
  public void addRecord(Record record, String... lanes) {
    if (recordAllowance-- == 0) {
      throw new IllegalStateException("The maximum number of records has been reached");
    }
    Preconditions.checkNotNull(record, "record cannot be null");
    record = ((RecordImpl)record).createCopy();
    ((RecordImpl)record).setStage(instanceName);
    ((RecordImpl)record).setTrackingId();

    if (lanes.length == 0) {
      Preconditions.checkArgument(outputLanes.size() == 1, String.format(
          "No lane has been specified and the stage '%s' has multiple output lanes '%s'", instanceName, outputLanes));
      stageOutput.get(singleOutputLane).add(record);
    } else {
      if (lanes.length > 1) {
        Set<String> laneSet = ImmutableSet.copyOf(lanes);
        Preconditions.checkArgument(laneSet.size() == lanes.length, String.format(
            "Specified lanes cannot have duplicates '%s'", laneSet));
      }
      for (String lane : lanes) {
        Preconditions.checkArgument(outputLanes.contains(lane), String.format(
            "Invalid output lane '%s' for stage '%s', available lanes '%s'", lane, instanceName, outputLanes));
        stageOutput.get(lane).add(record);
      }
    }
    if (stageOutputSnapshot != null) {
      record = ((RecordImpl)record).createSnapshot();
      if (lanes.length == 0) {
        stageOutputSnapshot.get(singleOutputLane).add(record);
      } else {
        for (String lane : lanes) {
          stageOutputSnapshot.get(lane).add(record);
        }
      }
    }
    size++;
  }

  public Map<String, List<Record>> getStageOutput() {
    return stageOutput;
  }

  public Map<String, List<Record>> getStageOutputSnapshot() {
    return stageOutputSnapshot;
  }

  public int getSize() {
    return size;
  }

  public int getSize(String lane) {
    return stageOutput.get(lane).size();
  }

}

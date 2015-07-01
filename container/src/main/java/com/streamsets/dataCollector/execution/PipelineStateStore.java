/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.dataCollector.execution;

import java.util.List;
import java.util.Map;

import com.streamsets.pipeline.api.ExecutionMode;
import com.streamsets.pipeline.store.PipelineStoreException;

// one per SDC
public interface PipelineStateStore {

  //PipelineStore should receive a PipelineStateStore instance and use it for 2 things
  // 1. when the pipeline is edited it should call the edited method to changed it state to EDITED
  // 2. the PipelineStore should reject any save/delete if the pipeline is running

  // the edited method should record only a change from <new> to EDITED or <other-status> to EDITED,
  // ignoring all EDITED to EDITED.
  public PipelineState edited(String user, String name, String rev, ExecutionMode executionMode) throws PipelineStoreException;

 //called by PipelineStore when the pipeline is being deleted from the store.
  public void delete(String name, String rev) throws PipelineStoreException;

  public PipelineState saveState(String user, String name, String rev, PipelineStatus status, String message,
    Map<String, Object> attributes, ExecutionMode executionMode) throws PipelineStoreException;

  public PipelineState getState(String name, String rev) throws PipelineStoreException;

  public List<PipelineState> getHistory(String name, String rev, boolean fromBeginning) throws PipelineStoreException;

  public void deleteHistory(String name, String rev);

  public void init();

  public void destroy();

}

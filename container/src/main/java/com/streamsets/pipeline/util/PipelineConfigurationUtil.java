/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.util;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamsets.pipeline.api.ConfigDef;
import com.streamsets.pipeline.api.el.ELEvalException;
import com.streamsets.pipeline.config.ConfigConfiguration;
import com.streamsets.pipeline.config.ConfigDefinition;
import com.streamsets.pipeline.config.PipelineConfiguration;
import com.streamsets.pipeline.config.StageConfiguration;
import com.streamsets.pipeline.config.StageDefinition;
import com.streamsets.pipeline.el.ELEvaluator;
import com.streamsets.pipeline.el.ELVariables;
import com.streamsets.pipeline.el.RuntimeEL;
import com.streamsets.pipeline.json.ObjectMapperFactory;
import com.streamsets.pipeline.restapi.bean.BeanHelper;
import com.streamsets.pipeline.restapi.bean.PipelineConfigurationJson;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PipelineConfigurationUtil {

  private static final String KEY = "key";
  private static final String VALUE = "value";
  public static Map<String, String> getFlattenedStringMap(String mapName, PipelineConfiguration pipelineConf) {
    Map<String, String> result = new HashMap<>();
    for (Map.Entry<String, Object> entry : getFlattenedMap(mapName, pipelineConf).entrySet()) {
      result.put(entry.getKey(), String.valueOf(entry.getValue()));
    }
    return result;
  }

  public static Map<String, Object> getFlattenedMap(String mapName, PipelineConfiguration pipelineConf) {
    Map<String, Object> constants = new HashMap<>();
    if(pipelineConf != null && pipelineConf.getConfiguration() != null) {
      for (ConfigConfiguration configConfiguration : pipelineConf.getConfiguration()) {
        if (mapName.equals(configConfiguration.getName()) && configConfiguration.getValue() != null) {
          for (Map<String, String> map : (List<Map<String, String>>) configConfiguration.getValue()) {
            constants.put(map.get(KEY), map.get(VALUE));
          }
          return constants;
        }
      }
    }
    return constants;
  }

  public static String getSourceLibName(String pipelineJson) throws JsonParseException, JsonMappingException,
    IOException {
    ObjectMapper json = ObjectMapperFactory.getOneLine();
    PipelineConfigurationJson pipelineConfigBean = json.readValue(pipelineJson, PipelineConfigurationJson.class);
    PipelineConfiguration pipelineConf = BeanHelper.unwrapPipelineConfiguration(pipelineConfigBean);
    for (int i = 0; i < pipelineConf.getStages().size(); i++) {
      StageConfiguration stageConf = pipelineConf.getStages().get(i);
      if (stageConf.getInputLanes().isEmpty()) {
        return stageConf.getLibrary();
      }
    }
    return null;
  }

}

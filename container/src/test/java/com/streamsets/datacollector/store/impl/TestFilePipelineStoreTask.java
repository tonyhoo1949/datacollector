/**
 * (c) 2014 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.datacollector.store.impl;


import static org.junit.Assert.assertEquals;

import com.streamsets.datacollector.config.DataRuleDefinition;
import com.streamsets.datacollector.config.MetricElement;
import com.streamsets.datacollector.config.MetricType;
import com.streamsets.datacollector.config.MetricsRuleDefinition;
import com.streamsets.datacollector.config.PipelineConfiguration;
import com.streamsets.datacollector.config.RuleDefinitions;
import com.streamsets.datacollector.config.StageConfiguration;
import com.streamsets.datacollector.config.ThresholdType;
import com.streamsets.datacollector.execution.PipelineStateStore;
import com.streamsets.datacollector.main.RuntimeInfo;
import com.streamsets.datacollector.runner.MockStages;
import com.streamsets.datacollector.stagelibrary.StageLibraryTask;
import com.streamsets.datacollector.store.PipelineInfo;
import com.streamsets.datacollector.store.PipelineStoreException;
import com.streamsets.datacollector.store.PipelineStoreTask;
import com.streamsets.datacollector.store.impl.CachePipelineStoreTask;
import com.streamsets.datacollector.store.impl.FilePipelineStoreTask;
import com.streamsets.datacollector.util.ContainerError;
import com.streamsets.datacollector.util.LockCache;
import com.streamsets.datacollector.util.LockCacheModule;

import dagger.ObjectGraph;
import dagger.Provides;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import javax.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TestFilePipelineStoreTask {

  private static final String DEFAULT_PIPELINE_NAME = "xyz";
  private static final String DEFAULT_PIPELINE_DESCRIPTION = "Default Pipeline";
  private static final String SYSTEM_USER = "system";
  protected PipelineStoreTask store;

  @dagger.Module(injects = {FilePipelineStoreTask.class, LockCache.class},
    includes = LockCacheModule.class)
  public static class Module {
    public Module() {
    }
    @Provides
    @Singleton
    public RuntimeInfo provideRuntimeInfo() {
      RuntimeInfo mock = Mockito.mock(RuntimeInfo.class);
      Mockito.when(mock.getDataDir()).thenReturn("target/" + UUID.randomUUID());
      return mock;
    }

    @Provides
    @Singleton
    public StageLibraryTask provideStageLibrary() {
      return MockStages.createStageLibrary();
    }

    @Provides
    @Singleton
    public PipelineStateStore providePipelineStateStore() {
      return null;
    }
  }

  @Before
  public void setUp() {
    ObjectGraph dagger = ObjectGraph.create(new Module());
    FilePipelineStoreTask filePipelineStoreTask = dagger.get(FilePipelineStoreTask.class);
    store = new CachePipelineStoreTask(filePipelineStoreTask, new LockCache<String>());
  }

  @After
  public void tearDown() {
    store = null;
  }

  @Test
  public void testStoreNoDefaultPipeline() throws Exception {
    try {
      //creating store dir
      store.init();
      Assert.assertTrue(store.getPipelines().isEmpty());
    } finally {
      store.stop();
    }
    try {
      //store dir already exists
      store.init();
      Assert.assertTrue(store.getPipelines().isEmpty());
    } finally {
      store.stop();
    }
  }

  @Test
  public void testCreateDelete() throws Exception {
    try {
      store.init();
      Assert.assertEquals(0, store.getPipelines().size());
      store.create("foo", "a", "A");
      Assert.assertEquals(1, store.getPipelines().size());
      store.save("foo2", "a", "A", "", store.load("a", "0"));
      assertEquals("foo2", store.getPipelines().get(0).getLastModifier());
      Assert.assertEquals("a", store.getInfo("a").getName());
      store.delete("a");
      Assert.assertEquals(0, store.getPipelines().size());
    } finally {
      store.stop();
    }
  }

  @Test(expected = PipelineStoreException.class)
  public void testCreateExistingPipeline() throws Exception {
    try {
      store.init();
      store.create("foo", "a", "A");
      store.create("foo", "a", "A");
    } finally {
      store.stop();
    }
  }

  @Test(expected = PipelineStoreException.class)
  public void testDeleteNotExisting() throws Exception {
    try {
      store.init();
      store.delete("a");
    } finally {
      store.stop();
    }
  }

  @Test(expected = PipelineStoreException.class)
  public void testSaveNotExisting() throws Exception {
    try {
      store.init();
      createDefaultPipeline(store);
      PipelineConfiguration pc = store.load(DEFAULT_PIPELINE_NAME, FilePipelineStoreTask.REV);
      store.save("foo", "a", null, null, pc);
    } finally {
      store.stop();
    }
  }

  @Test(expected = PipelineStoreException.class)
  public void testSaveWrongUuid() throws Exception {
    try {
      store.init();
      createDefaultPipeline(store);
      PipelineConfiguration pc = store.load(DEFAULT_PIPELINE_NAME, FilePipelineStoreTask.REV);
      pc.setUuid(UUID.randomUUID());
      store.save("foo", DEFAULT_PIPELINE_NAME, null, null, pc);
    } finally {
      store.stop();
    }
  }

  @Test(expected = PipelineStoreException.class)
  public void testLoadNotExisting() throws Exception {
    try {
      store.init();
      store.load("a", null);
    } finally {
      store.stop();
    }
  }

  @Test(expected = PipelineStoreException.class)
  public void testHistoryNotExisting() throws Exception {
    try {
      store.init();
      store.getHistory("a");
    } finally {
      store.stop();
    }
  }

  private PipelineConfiguration createPipeline(UUID uuid) {
    PipelineConfiguration pc = MockStages.createPipelineConfigurationSourceTarget();
    pc.setUuid(uuid);
    return pc;
  }

  @Test
  public void testSave() throws Exception {
    try {
      store.init();
      createDefaultPipeline(store);
      PipelineInfo info1 = store.getInfo(DEFAULT_PIPELINE_NAME);
      PipelineConfiguration pc0 = store.load(DEFAULT_PIPELINE_NAME, FilePipelineStoreTask.REV);
      pc0 = createPipeline(pc0.getUuid());
      Thread.sleep(5);
      store.save("foo", DEFAULT_PIPELINE_NAME, null, null, pc0);
      PipelineInfo info2 = store.getInfo(DEFAULT_PIPELINE_NAME);
      Assert.assertEquals(info1.getCreated(), info2.getCreated());
      Assert.assertEquals(info1.getCreator(), info2.getCreator());
      Assert.assertEquals(info1.getName(), info2.getName());
      Assert.assertEquals(info1.getLastRev(), info2.getLastRev());
      Assert.assertEquals("foo", info2.getLastModifier());
      Assert.assertTrue(info2.getLastModified().getTime() > info1.getLastModified().getTime());
    } finally {
      store.stop();
    }
  }

  @Test
  public void testSaveAndLoad() throws Exception {
    try {
      store.init();
      createDefaultPipeline(store);
      PipelineConfiguration pc = store.load(DEFAULT_PIPELINE_NAME, FilePipelineStoreTask.REV);
      Assert.assertTrue(pc.getStages().isEmpty());
      UUID uuid = pc.getUuid();
      pc = createPipeline(pc.getUuid());
      pc = store.save("foo", DEFAULT_PIPELINE_NAME, null, null, pc);
      UUID newUuid = pc.getUuid();
      Assert.assertNotEquals(uuid, newUuid);
      PipelineConfiguration pc2 = store.load(DEFAULT_PIPELINE_NAME, FilePipelineStoreTask.REV);
      Assert.assertFalse(pc2.getStages().isEmpty());
      Assert.assertEquals(pc.getUuid(), pc2.getUuid());
      PipelineInfo info = store.getInfo(DEFAULT_PIPELINE_NAME);
      Assert.assertEquals(pc.getUuid(), info.getUuid());
    } finally {
      store.stop();
    }
  }

  @Test
  public void testStoreAndRetrieveRules() throws PipelineStoreException {
    store.init();
    createDefaultPipeline(store);
    RuleDefinitions ruleDefinitions = store.retrieveRules(DEFAULT_PIPELINE_NAME,
      FilePipelineStoreTask.REV);
    Assert.assertNotNull(ruleDefinitions);
    Assert.assertTrue(ruleDefinitions.getDataRuleDefinitions().isEmpty());
    Assert.assertTrue(ruleDefinitions.getMetricsRuleDefinitions().isEmpty());

    List<MetricsRuleDefinition> metricsRuleDefinitions = ruleDefinitions.getMetricsRuleDefinitions();
    metricsRuleDefinitions.add(new MetricsRuleDefinition("m1", "m1", "a", MetricType.COUNTER,
      MetricElement.COUNTER_COUNT, "p", false, true));
    metricsRuleDefinitions.add(new MetricsRuleDefinition("m2", "m2", "a", MetricType.TIMER,
      MetricElement.TIMER_M15_RATE, "p", false, true));
    metricsRuleDefinitions.add(new MetricsRuleDefinition("m3", "m3", "a", MetricType.HISTOGRAM,
      MetricElement.HISTOGRAM_MEAN, "p", false, true));

    List<DataRuleDefinition> dataRuleDefinitions = ruleDefinitions.getDataRuleDefinitions();
    dataRuleDefinitions.add(new DataRuleDefinition("a", "a", "a", 20, 300, "x", true, "c", ThresholdType.COUNT, "200",
      1000, true, false, true));
    dataRuleDefinitions.add(new DataRuleDefinition("b", "b", "b", 20, 300, "x", true, "c", ThresholdType.COUNT, "200",
      1000, true, false, true));
    dataRuleDefinitions.add(new DataRuleDefinition("c", "c", "c", 20, 300, "x", true, "c", ThresholdType.COUNT, "200",
      1000, true, false, true));

    store.storeRules(DEFAULT_PIPELINE_NAME, FilePipelineStoreTask.REV, ruleDefinitions);

    RuleDefinitions actualRuleDefinitions = store.retrieveRules(DEFAULT_PIPELINE_NAME,
      FilePipelineStoreTask.REV);

    Assert.assertTrue(ruleDefinitions == actualRuleDefinitions);
  }

  @Test
  public void testStoreMultipleCopies() throws PipelineStoreException {
    /*This test case mimicks a use case where 2 users connect to the same data collector instance
    * using different browsers and modify the same rule definition. The user who saves last runs into an exception.
    * The user is forced to reload, reapply changes and save*/
    store.init();
    createDefaultPipeline(store);
    RuleDefinitions ruleDefinitions1 = store.retrieveRules(DEFAULT_PIPELINE_NAME,
      FilePipelineStoreTask.REV);

    RuleDefinitions tempRuleDef = store.retrieveRules(DEFAULT_PIPELINE_NAME,
      FilePipelineStoreTask.REV);
    //Mimick two different clients [browsers] retrieving from the store
    RuleDefinitions ruleDefinitions2 = new RuleDefinitions(tempRuleDef.getMetricsRuleDefinitions(),
      tempRuleDef.getDataRuleDefinitions(), tempRuleDef.getEmailIds(), tempRuleDef.getUuid());

    List<MetricsRuleDefinition> metricsRuleDefinitions = ruleDefinitions1.getMetricsRuleDefinitions();
    metricsRuleDefinitions.add(new MetricsRuleDefinition("m1", "m1", "a", MetricType.COUNTER,
      MetricElement.COUNTER_COUNT, "p", false, true));
    metricsRuleDefinitions.add(new MetricsRuleDefinition("m2", "m2", "a", MetricType.TIMER,
      MetricElement.TIMER_M15_RATE, "p", false, true));
    metricsRuleDefinitions.add(new MetricsRuleDefinition("m3", "m3", "a", MetricType.HISTOGRAM,
      MetricElement.HISTOGRAM_MEAN, "p", false, true));

    List<DataRuleDefinition> dataRuleDefinitions = ruleDefinitions2.getDataRuleDefinitions();
    dataRuleDefinitions.add(new DataRuleDefinition("a", "a", "a", 20, 300, "x", true, "c", ThresholdType.COUNT, "200",
      1000, true, false, true));
    dataRuleDefinitions.add(new DataRuleDefinition("b", "b", "b", 20, 300, "x", true, "c", ThresholdType.COUNT, "200",
      1000, true, false, true));
    dataRuleDefinitions.add(new DataRuleDefinition("c", "c", "c", 20, 300, "x", true, "c", ThresholdType.COUNT, "200",
      1000, true, false, true));

    //store ruleDefinition1
    store.storeRules(DEFAULT_PIPELINE_NAME, FilePipelineStoreTask.REV, ruleDefinitions1);

    //attempt storing rule definition 2, should fail
    try {
      store.storeRules(DEFAULT_PIPELINE_NAME, FilePipelineStoreTask.REV, ruleDefinitions2);
      Assert.fail("Expected PipelineStoreException as the rule definition being saved is not the latest copy.");
    } catch (PipelineStoreException e) {
      Assert.assertEquals(e.getErrorCode(), ContainerError.CONTAINER_0205);
    }

    //reload, modify and and then store
    ruleDefinitions2 = store.retrieveRules(DEFAULT_PIPELINE_NAME,
      FilePipelineStoreTask.REV);
    dataRuleDefinitions = ruleDefinitions2.getDataRuleDefinitions();
    dataRuleDefinitions.add(new DataRuleDefinition("a", "a", "a", 20, 300, "x", true, "c", ThresholdType.COUNT, "200",
      1000, true, false, true));
    dataRuleDefinitions.add(new DataRuleDefinition("b", "b", "b", 20, 300, "x", true, "c", ThresholdType.COUNT, "200",
      1000, true, false, true));
    dataRuleDefinitions.add(new DataRuleDefinition("c", "c", "c", 20, 300, "x", true, "c", ThresholdType.COUNT, "200",
      1000, true, false, true));

    store.storeRules(DEFAULT_PIPELINE_NAME, FilePipelineStoreTask.REV, ruleDefinitions2);

    RuleDefinitions actualRuleDefinitions = store.retrieveRules(DEFAULT_PIPELINE_NAME,
      FilePipelineStoreTask.REV);

    Assert.assertTrue(ruleDefinitions2 == actualRuleDefinitions);
  }

  private void createDefaultPipeline(PipelineStoreTask store) throws PipelineStoreException {
    store.create(SYSTEM_USER, DEFAULT_PIPELINE_NAME, DEFAULT_PIPELINE_DESCRIPTION);
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testUiInfoSave() throws Exception {
    try {
      store.init();
      createDefaultPipeline(store);
      PipelineConfiguration pc = store.load(DEFAULT_PIPELINE_NAME, FilePipelineStoreTask.REV);
      Assert.assertTrue(pc.getUiInfo().isEmpty());

      // add a stage to pipeline
      pc.getStages().add(new StageConfiguration("i", "l", "n", 1, Collections.EMPTY_LIST, Collections.EMPTY_MAP,
                                                Collections.EMPTY_LIST, Collections.EMPTY_LIST));

      // set some uiInfo at pipeline and stage level and dave
      pc.getUiInfo().put("a", "A");
      pc.getUiInfo().put("b", "B");
      pc.getStages().get(0).getUiInfo().put("ia", "IA");
      pc.getStages().get(0).getUiInfo().put("ib", "IB");
      pc = store.save("foo", DEFAULT_PIPELINE_NAME, null, null, pc);

      // verify uiInfo stays after save
      Assert.assertEquals(2, pc.getUiInfo().size());
      Assert.assertEquals("A", pc.getUiInfo().get("a"));
      Assert.assertEquals("B", pc.getUiInfo().get("b"));
      Assert.assertEquals(2, pc.getStages().get(0).getUiInfo().size());
      Assert.assertEquals("IA", pc.getStages().get(0).getUiInfo().get("ia"));
      Assert.assertEquals("IB", pc.getStages().get(0).getUiInfo().get("ib"));

      // load and verify uiInfo stays
      pc = store.load(DEFAULT_PIPELINE_NAME, null);
      Assert.assertEquals(2, pc.getUiInfo().size());
      Assert.assertEquals("A", pc.getUiInfo().get("a"));
      Assert.assertEquals("B", pc.getUiInfo().get("b"));
      Assert.assertEquals(2, pc.getStages().get(0).getUiInfo().size());
      Assert.assertEquals("IA", pc.getStages().get(0).getUiInfo().get("ia"));
      Assert.assertEquals("IB", pc.getStages().get(0).getUiInfo().get("ib"));

      // extract uiInfo, modify and save uiInfo only
      Map<String, Object> uiInfo = FilePipelineStoreTask.extractUiInfo(pc);
      ((Map)uiInfo.get(":pipeline:")).clear();
      ((Map)uiInfo.get(":pipeline:")).put("a", "AA");
      ((Map)uiInfo.get("i")).clear();
      ((Map)uiInfo.get("i")).put("ia", "IIAA");
      store.saveUiInfo(DEFAULT_PIPELINE_NAME, null, uiInfo);

      pc = store.load(DEFAULT_PIPELINE_NAME, null);
      Assert.assertNotNull(pc.getUiInfo());
      Assert.assertEquals(1, pc.getUiInfo().size());
      Assert.assertEquals("AA", pc.getUiInfo().get("a"));
      Assert.assertEquals(1, pc.getStages().get(0).getUiInfo().size());
      Assert.assertEquals("IIAA",pc.getStages().get(0).getUiInfo().get("ia"));

    } finally {
      store.stop();
    }
  }

}
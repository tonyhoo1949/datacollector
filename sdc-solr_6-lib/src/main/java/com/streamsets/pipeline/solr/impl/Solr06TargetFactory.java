/**
 * Copyright 2016 StreamSets Inc.
 *
 * Licensed under the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.streamsets.pipeline.solr.impl;

import com.streamsets.pipeline.solr.api.SdcSolrTarget;
import com.streamsets.pipeline.solr.api.SdcSolrTargetFactory;
import com.streamsets.pipeline.solr.api.TargetFactorySettings;

public class Solr06TargetFactory extends SdcSolrTargetFactory {
  private TargetFactorySettings settings;

  public Solr06TargetFactory() {
  }

  @Override
  protected void init(TargetFactorySettings settings) {
    this.settings = settings;
  }

  @Override
  public SdcSolrTarget create() {
    return new SolrTarget06(
        settings.getInstanceType(),
        settings.getSolrURI(),
        settings.getZookeeperConnect(),
        settings.getDefaultCollection()
    );
  }

}

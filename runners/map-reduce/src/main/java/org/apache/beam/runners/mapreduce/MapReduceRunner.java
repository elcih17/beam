/*
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
package org.apache.beam.runners.mapreduce;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.base.Throwables;
import org.apache.beam.runners.mapreduce.translation.Graphs;
import org.apache.beam.runners.mapreduce.translation.GraphConverter;
import org.apache.beam.runners.mapreduce.translation.GraphPlanner;
import org.apache.beam.runners.mapreduce.translation.JobPrototype;
import org.apache.beam.runners.mapreduce.translation.TranslationContext;
import org.apache.beam.sdk.Pipeline;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.PipelineRunner;
import org.apache.beam.sdk.options.PipelineOptions;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.Job;

/**
 * {@link PipelineRunner} for MapReduce.
 */
public class MapReduceRunner extends PipelineRunner<PipelineResult> {

  /**
   * Construct a runner from the provided options.
   *
   * @param options Properties which configure the runner.
   * @return The newly created runner.
   */
  public static MapReduceRunner fromOptions(PipelineOptions options) {
    return new MapReduceRunner(options.as(MapReducePipelineOptions.class));
  }

  private final MapReducePipelineOptions options;

  MapReduceRunner(MapReducePipelineOptions options) {
    this.options = checkNotNull(options, "options");
  }

  @Override
  public PipelineResult run(Pipeline pipeline) {
    TranslationContext context = new TranslationContext(options);
    GraphConverter graphConverter = new GraphConverter(context);
    pipeline.traverseTopologically(graphConverter);

    GraphPlanner planner = new GraphPlanner();
    Graphs.FusedGraph fusedGraph = planner.plan(context.getInitGraph());
    int stageId = 0;
    for (Graphs.FusedStep fusedStep : fusedGraph.getFusedSteps()) {
      JobPrototype jobPrototype = JobPrototype.create(stageId++, fusedStep, options);
      try {
        Job job = jobPrototype.build(options.getJarClass(), new Configuration());
        job.waitForCompletion(true);
      } catch (Exception e) {
        Throwables.throwIfUnchecked(e);
      }
    }
    return null;
  }
}

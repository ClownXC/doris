// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package org.apache.doris.nereids.util;

import org.apache.doris.common.Pair;
import org.apache.doris.nereids.trees.expressions.EqualTo;
import org.apache.doris.nereids.trees.expressions.NamedExpression;
import org.apache.doris.nereids.trees.plans.JoinType;
import org.apache.doris.nereids.trees.plans.logical.LogicalJoin;
import org.apache.doris.nereids.trees.plans.logical.LogicalOlapScan;
import org.apache.doris.nereids.trees.plans.logical.LogicalPlan;
import org.apache.doris.nereids.trees.plans.logical.LogicalProject;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class LogicalPlanBuilder {
    private final LogicalPlan plan;

    public LogicalPlanBuilder(LogicalPlan plan) {
        this.plan = plan;
    }

    public LogicalPlan build() {
        return plan;
    }

    public LogicalPlanBuilder from(LogicalPlan plan) {
        return new LogicalPlanBuilder(plan);
    }

    public LogicalPlanBuilder scan(long tableId, String tableName, int hashColumn) {
        LogicalOlapScan scan = PlanConstructor.newLogicalOlapScan(tableId, tableName, hashColumn);
        return from(scan);
    }

    public LogicalPlanBuilder projectWithExprs(List<NamedExpression> projectExprs) {
        LogicalProject<LogicalPlan> project = new LogicalProject<>(projectExprs, this.plan);
        return from(project);
    }

    public LogicalPlanBuilder project(List<Integer> slotsIndex) {
        List<NamedExpression> projectExprs = Lists.newArrayList();
        for (Integer index : slotsIndex) {
            projectExprs.add(this.plan.getOutput().get(index));
        }
        LogicalProject<LogicalPlan> project = new LogicalProject<>(projectExprs, this.plan);
        return from(project);
    }

    public LogicalPlanBuilder hashJoinUsing(LogicalPlan right, JoinType joinType, Pair<Integer, Integer> hashOnSlots) {
        ImmutableList<EqualTo> hashConjunts = ImmutableList.of(
                new EqualTo(this.plan.getOutput().get(hashOnSlots.first), right.getOutput().get(hashOnSlots.second)));

        LogicalJoin<LogicalPlan, LogicalPlan> join = new LogicalJoin<>(joinType, new ArrayList<>(hashConjunts),
                Optional.empty(), this.plan, right);
        return from(join);
    }
}

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

package org.apache.doris.nereids.rules.exploration.join;

import org.apache.doris.common.Pair;
import org.apache.doris.nereids.memo.Group;
import org.apache.doris.nereids.memo.GroupExpression;
import org.apache.doris.nereids.trees.plans.JoinType;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.logical.LogicalJoin;
import org.apache.doris.nereids.trees.plans.logical.LogicalOlapScan;
import org.apache.doris.nereids.trees.plans.logical.LogicalPlan;
import org.apache.doris.nereids.trees.plans.logical.LogicalProject;
import org.apache.doris.nereids.util.LogicalPlanBuilder;
import org.apache.doris.nereids.util.MemoTestUtils;
import org.apache.doris.nereids.util.PlanChecker;
import org.apache.doris.nereids.util.PlanConstructor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JoinCommuteTest {
    @Test
    public void testInnerJoinCommute() {
        LogicalOlapScan scan1 = PlanConstructor.newLogicalOlapScan(0, "t1", 0);
        LogicalOlapScan scan2 = PlanConstructor.newLogicalOlapScan(1, "t2", 0);

        LogicalPlan join = new LogicalPlanBuilder(scan1)
                .hashJoinUsing(scan2, JoinType.INNER_JOIN, Pair.of(0, 0)) // t1.id = t2.id
                .build();

        PlanChecker.from(MemoTestUtils.createConnectContext(), join)
                .transform(JoinCommute.LEFT_DEEP.build())
                .checkMemo(memo -> {
                    Group root = memo.getRoot();
                    Assertions.assertEquals(2, root.getLogicalExpressions().size());

                    Assertions.assertTrue(root.logicalExpressionsAt(0).getPlan() instanceof LogicalJoin);
                    Assertions.assertTrue(root.logicalExpressionsAt(1).getPlan() instanceof LogicalProject);

                    GroupExpression newJoinGroupExpr = root.logicalExpressionsAt(1).child(0).getLogicalExpression();
                    Plan left = newJoinGroupExpr.child(0).getLogicalExpression().getPlan();
                    Plan right = newJoinGroupExpr.child(1).getLogicalExpression().getPlan();
                    Assertions.assertTrue(left instanceof LogicalOlapScan);
                    Assertions.assertTrue(right instanceof LogicalOlapScan);

                    Assertions.assertEquals("t2", ((LogicalOlapScan) left).getTable().getName());
                    Assertions.assertEquals("t1", ((LogicalOlapScan) right).getTable().getName());
                });
    }
}

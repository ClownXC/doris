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

import org.apache.doris.nereids.trees.expressions.NamedExpression;
import org.apache.doris.nereids.trees.expressions.Slot;
import org.apache.doris.nereids.trees.expressions.SlotReference;
import org.apache.doris.nereids.trees.plans.GroupPlan;
import org.apache.doris.nereids.trees.plans.Plan;
import org.apache.doris.nereids.trees.plans.logical.LogicalJoin;
import org.apache.doris.nereids.util.ExpressionUtils;
import org.apache.doris.nereids.util.PlanUtils;
import org.apache.doris.nereids.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Common function for JoinLAsscom
 */
class JoinLAsscomHelper extends ThreeJoinHelper {
    /*
     *      topJoin                newTopJoin
     *      /     \                 /     \
     * bottomJoin  C   -->  newBottomJoin  B
     *  /    \                  /    \
     * A      B                A      C
     */

    /**
     * Init plan and output.
     */
    public JoinLAsscomHelper(LogicalJoin<? extends Plan, GroupPlan> topJoin,
            LogicalJoin<GroupPlan, GroupPlan> bottomJoin) {
        super(topJoin, bottomJoin, bottomJoin.left(), bottomJoin.right(), topJoin.right());
    }

    /**
     * Create newTopJoin.
     */
    public Plan newTopJoin() {
        // Split inside-project into two part.
        Map<Boolean, List<NamedExpression>> projectExprsMap = allProjects.stream()
                .collect(Collectors.partitioningBy(projectExpr -> {
                    Set<Slot> usedSlots = projectExpr.collect(Slot.class::isInstance);
                    return bOutput.containsAll(usedSlots);
                }));

        List<NamedExpression> newLeftProjectExpr = projectExprsMap.get(Boolean.FALSE);
        List<NamedExpression> newRightProjectExprs = projectExprsMap.get(Boolean.TRUE);

        // If add project to B, we should add all slotReference used by hashOnCondition.
        // TODO: Does nonHashOnCondition also need to be considered.
        Set<SlotReference> onUsedSlotRef = bottomJoin.getHashJoinConjuncts().stream()
                .flatMap(expr -> {
                    Set<SlotReference> usedSlotRefs = expr.collect(SlotReference.class::isInstance);
                    return usedSlotRefs.stream();
                }).filter(Utils.getOutputSlotReference(bottomJoin)::contains).collect(Collectors.toSet());
        boolean existRightProject = !newRightProjectExprs.isEmpty();
        boolean existLeftProject = !newLeftProjectExpr.isEmpty();
        onUsedSlotRef.forEach(slotRef -> {
            if (existRightProject && bOutput.contains(slotRef) && !newRightProjectExprs.contains(slotRef)) {
                newRightProjectExprs.add(slotRef);
            } else if (existLeftProject && aOutput.contains(slotRef) && !newLeftProjectExpr.contains(slotRef)) {
                newLeftProjectExpr.add(slotRef);
            }
        });

        if (existLeftProject) {
            newLeftProjectExpr.addAll(cOutput);
        }
        LogicalJoin<GroupPlan, GroupPlan> newBottomJoin = new LogicalJoin<>(topJoin.getJoinType(),
                newBottomHashJoinConjuncts, ExpressionUtils.optionalAnd(newBottomNonHashJoinConjuncts), a, c,
                bottomJoin.getJoinReorderContext());
        newBottomJoin.getJoinReorderContext().setHasLAsscom(false);
        newBottomJoin.getJoinReorderContext().setHasCommute(false);

        Plan left = PlanUtils.projectOrSelf(newLeftProjectExpr, newBottomJoin);
        Plan right = PlanUtils.projectOrSelf(newRightProjectExprs, b);

        LogicalJoin<Plan, Plan> newTopJoin = new LogicalJoin<>(bottomJoin.getJoinType(),
                newTopHashJoinConjuncts,
                ExpressionUtils.optionalAnd(newTopNonHashJoinConjuncts), left, right,
                topJoin.getJoinReorderContext());
        newTopJoin.getJoinReorderContext().setHasLAsscom(true);

        return PlanUtils.projectOrSelf(new ArrayList<>(topJoin.getOutput()), newTopJoin);
    }

    public static boolean checkInner(LogicalJoin<? extends Plan, GroupPlan> topJoin,
            LogicalJoin<GroupPlan, GroupPlan> bottomJoin) {
        return !bottomJoin.getJoinReorderContext().hasCommuteZigZag()
                && !topJoin.getJoinReorderContext().hasLAsscom();
    }

    public static boolean checkOuter(LogicalJoin<? extends Plan, GroupPlan> topJoin,
            LogicalJoin<GroupPlan, GroupPlan> bottomJoin) {
        // hasCommute will cause to lack of OuterJoinAssocRule:Left
        return !topJoin.getJoinReorderContext().hasLeftAssociate()
                && !topJoin.getJoinReorderContext().hasRightAssociate()
                && !topJoin.getJoinReorderContext().hasExchange()
                && !bottomJoin.getJoinReorderContext().hasCommute();
    }
}

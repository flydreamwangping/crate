/*
 * This file is part of a module with proprietary Enterprise Features.
 *
 * Licensed to Crate.io Inc. ("Crate.io") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 *
 * To use this file, Crate.io must have given you permission to enable and
 * use such Enterprise Features and you must have a valid Enterprise or
 * Subscription Agreement with Crate.io.  If you enable or use the Enterprise
 * Features, you represent and warrant that you have a valid Enterprise or
 * Subscription Agreement with Crate.io.  Your use of the Enterprise Features
 * if governed by the terms and conditions of your Enterprise or Subscription
 * Agreement with Crate.io.
 */

package io.crate.module;

import io.crate.execution.engine.aggregation.AggregationFunction;
import io.crate.expression.AbstractFunctionModule;
import io.crate.operation.aggregation.HyperLogLogDistinctAggregation;
import io.crate.window.NthValueFunctions;
import io.crate.window.OffsetValueFunctions;
import io.crate.window.RankFunctions;

public class EnterpriseFunctionsModule extends AbstractFunctionModule<AggregationFunction> {

    @Override
    public void configureFunctions() {
        HyperLogLogDistinctAggregation.register(this);
        NthValueFunctions.register(this);
        OffsetValueFunctions.register(this);
        RankFunctions.register(this);
    }
}

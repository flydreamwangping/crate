/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.analyze;

import com.google.common.primitives.Booleans;
import io.crate.analyze.symbol.Symbol;
import io.crate.analyze.symbol.Symbols;
import io.crate.collections.Lists2;
import io.crate.exceptions.AmbiguousOrderByException;
import io.crate.planner.ExplainLeaf;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.io.stream.Writeable;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.function.Function;

public class OrderBy implements Writeable {

    private List<Symbol> orderBySymbols;
    private boolean[] reverseFlags;
    private Boolean[] nullsFirst;

    public OrderBy(List<Symbol> orderBySymbols, boolean[] reverseFlags, Boolean[] nullsFirst) {
        assert !orderBySymbols.isEmpty() : "orderBySymbols must not be empty";
        assert orderBySymbols.size() == reverseFlags.length && reverseFlags.length == nullsFirst.length :
            "size of symbols / reverseFlags / nullsFirst must match";

        this.orderBySymbols = orderBySymbols;
        this.reverseFlags = reverseFlags;
        this.nullsFirst = nullsFirst;
    }

    public List<Symbol> orderBySymbols() {
        return orderBySymbols;
    }

    public boolean[] reverseFlags() {
        return reverseFlags;
    }

    public Boolean[] nullsFirst() {
        return nullsFirst;
    }

    public OrderBy subset(Collection<Integer> positions) {
        List<Symbol> orderBySymbols = new ArrayList<>(positions.size());
        Boolean[] nullsFirst = new Boolean[positions.size()];
        boolean[] reverseFlags = new boolean[positions.size()];
        int pos = 0;
        for (Integer i : positions) {
            orderBySymbols.add(Symbols.DEEP_COPY.apply(this.orderBySymbols.get(i)));
            nullsFirst[pos] = this.nullsFirst[i];
            reverseFlags[pos] = this.reverseFlags[i];
            pos++;
        }
        return new OrderBy(orderBySymbols, reverseFlags, nullsFirst);
    }

    public OrderBy(StreamInput in) throws IOException {
        int numOrderBy = in.readVInt();
        reverseFlags = new boolean[numOrderBy];

        for (int i = 0; i < numOrderBy; i++) {
            reverseFlags[i] = in.readBoolean();
        }

        orderBySymbols = new ArrayList<>(numOrderBy);
        for (int i = 0; i < numOrderBy; i++) {
            orderBySymbols.add(Symbols.fromStream(in));
        }

        nullsFirst = new Boolean[numOrderBy];
        for (int i = 0; i < numOrderBy; i++) {
            nullsFirst[i] = in.readOptionalBoolean();
        }
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(reverseFlags.length);
        for (boolean reverseFlag : reverseFlags) {
            out.writeBoolean(reverseFlag);
        }
        for (Symbol symbol : orderBySymbols) {
            Symbols.toStream(symbol, out);
        }
        for (Boolean nullFirst : nullsFirst) {
            out.writeOptionalBoolean(nullFirst);
        }
    }

    public OrderBy copyAndReplace(Function<? super Symbol, ? extends Symbol> replaceFunction) {
        return new OrderBy(Lists2.copyAndReplace(orderBySymbols, replaceFunction), reverseFlags, nullsFirst);
    }

    public void replace(Function<? super Symbol, ? extends Symbol> replaceFunction) {
        ListIterator<Symbol> listIt = orderBySymbols.listIterator();
        while (listIt.hasNext()) {
            listIt.set(replaceFunction.apply(listIt.next()));
        }
    }

    public OrderBy merge(@Nullable OrderBy otherOrderBy) {
        if (otherOrderBy != null) {
            List<Symbol> newOrderBySymbols = otherOrderBy.orderBySymbols();
            List<Boolean> newReverseFlags = new ArrayList<>(Booleans.asList(otherOrderBy.reverseFlags()));
            List<Boolean> newNullsFirst = new ArrayList<>(Arrays.asList(otherOrderBy.nullsFirst()));

            for (int i = 0; i < orderBySymbols.size(); i++) {
                Symbol orderBySymbol = orderBySymbols.get(i);
                int idx = newOrderBySymbols.indexOf(orderBySymbol);
                if (idx == -1) {
                    newOrderBySymbols.add(orderBySymbol);
                    newReverseFlags.add(reverseFlags[i]);
                    newNullsFirst.add(nullsFirst[i]);
                } else {
                    if (newReverseFlags.get(idx) != reverseFlags[i]) {
                        throw new AmbiguousOrderByException(orderBySymbol);
                    }
                    if (newNullsFirst.get(idx) != nullsFirst[i]) {
                        throw new AmbiguousOrderByException(orderBySymbol);
                    }
                }
            }

            this.orderBySymbols = newOrderBySymbols;
            this.reverseFlags = Booleans.toArray(newReverseFlags);
            this.nullsFirst = newNullsFirst.toArray(new Boolean[0]);
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OrderBy orderBy = (OrderBy) o;
        return orderBySymbols.equals(orderBy.orderBySymbols) &&
               Arrays.equals(reverseFlags, orderBy.reverseFlags) &&
               Arrays.equals(nullsFirst, orderBy.nullsFirst);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderBySymbols, reverseFlags, nullsFirst);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("OrderBy{");
        explainRepresentation(sb, orderBySymbols, reverseFlags, nullsFirst);
        return sb.toString();
    }

    public static StringBuilder explainRepresentation(StringBuilder sb,
                                                      List<? extends ExplainLeaf> leaves,
                                                      boolean[] reverseFlags,
                                                      Boolean[] nullsFirst) {
        for (int i = 0; i < leaves.size(); i++) {
            ExplainLeaf leaf = leaves.get(i);
            sb.append(leaf.representation());
            sb.append(" ");
            if (reverseFlags[i]) {
                sb.append("DESC");
            } else {
                sb.append("ASC");
            }
            Boolean nullFirst = nullsFirst[i];
            if (nullFirst != null) {
                sb.append(" ");
                sb.append(nullFirst ? "NULLS FIRST" : "NULLS LAST");
            }
            if (i + 1 < leaves.size()) {
                sb.append(" ");
            }
        }
        return sb;
    }
}

/**
 *    Copyright 2016-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.mybatis.dynamic.sql.update;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.mybatis.dynamic.sql.Condition;
import org.mybatis.dynamic.sql.SqlColumn;
import org.mybatis.dynamic.sql.SqlCriterion;
import org.mybatis.dynamic.sql.SqlTable;
import org.mybatis.dynamic.sql.util.FragmentAndParameters;
import org.mybatis.dynamic.sql.util.FragmentCollector;
import org.mybatis.dynamic.sql.where.AbstractWhereBuilder;
import org.mybatis.dynamic.sql.where.WhereSupport;

public class UpdateSupportBuilder {

    private int id = 1;
    private List<SetColumnAndValue<?>> columnsAndValues = new ArrayList<>();
    private SqlTable table;
    
    private UpdateSupportBuilder(SqlTable table) {
        this.table = table;
    }
    
    public <T> UpdateSupportBuilderFinisher<T> set(SqlColumn<T> column) {
        return new UpdateSupportBuilderFinisher<>(column);
    }
    
    public <T> UpdateSupportWhereBuilder where(SqlColumn<T> column, Condition<T> condition, SqlCriterion<?>...subCriteria) {
        return new UpdateSupportWhereBuilder(table, columnsAndValues, column, condition, subCriteria);
    }
    
    public class UpdateSupportBuilderFinisher<T> {
        
        private SqlColumn<T> column;
        
        public UpdateSupportBuilderFinisher(SqlColumn<T> column) {
            this.column = column;
        }
        
        public UpdateSupportBuilder equalToNull() {
            columnsAndValues.add(SetColumnAndValue.of(column));
            return UpdateSupportBuilder.this;
        }

        public UpdateSupportBuilder equalTo(T value) {
            columnsAndValues.add(SetColumnAndValue.of(column, value, id++));
            return UpdateSupportBuilder.this;
        }

        public UpdateSupportBuilder equalToWhenPresent(T value) {
            if (value != null) {
                columnsAndValues.add(SetColumnAndValue.of(column, value, id++));
            }
            return UpdateSupportBuilder.this;
        }
    }

    public static UpdateSupportBuilder update(SqlTable table) {
        return new UpdateSupportBuilder(table);
    }
    
    public static class UpdateSupportWhereBuilder extends AbstractWhereBuilder<UpdateSupportWhereBuilder> {
        private List<SetColumnAndValue<?>> setColumnsAndValues;
        private SqlTable table;
        
        public <T> UpdateSupportWhereBuilder(SqlTable table, List<SetColumnAndValue<?>> setColumnsAndValues, SqlColumn<T> column, Condition<T> condition, SqlCriterion<?>...subCriteria) {
            super(column, condition, subCriteria);
            this.table = table;
            this.setColumnsAndValues = setColumnsAndValues;
        }
        
        public UpdateSupport build() {
            Map<String, Object> parameters = new HashMap<>();
            FragmentCollector setValuesCollector = renderSetValues();
            WhereSupport whereSupport = renderCriteria(SqlColumn::name);
            parameters.putAll(setValuesCollector.parameters());
            parameters.putAll(whereSupport.getParameters());
            return UpdateSupport.of(setValuesCollector.fragments().collect(Collectors.joining(", ", "set ", "")), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    whereSupport.getWhereClause(), parameters, table);
        }
        
        private FragmentCollector renderSetValues() {
            return setColumnsAndValues.stream()
                    .map(SetColumnAndValue::fragmentAndParameters)
                    .collect(Collector.of(FragmentCollector::new,
                            FragmentCollector::add,
                            FragmentCollector::merge));
        }

        @Override
        protected UpdateSupportWhereBuilder getThis() {
            return this;
        }
    }
    
    static class SetColumnAndValue<T> {
        private FragmentAndParameters fragmentAndParameters;
        
        private SetColumnAndValue(SqlColumn<T> column, T value, int uniqueId) {
            String mapKey = "up" + uniqueId; //$NON-NLS-1$
            String jdbcPlaceholder = column.getFormattedJdbcPlaceholder("parameters", mapKey); //$NON-NLS-1$
            String setPhrase = column.name() + " = " + jdbcPlaceholder; //$NON-NLS-1$
            
            fragmentAndParameters = new FragmentAndParameters.Builder(setPhrase) //$NON-NLS-1$)
                    .withParameter(mapKey, value)
                    .build();
        }
        
        private SetColumnAndValue(SqlColumn<T> column) {
            fragmentAndParameters = new FragmentAndParameters.Builder(column.name() + " = null") //$NON-NLS-1$)
                    .build();
        }
        
        FragmentAndParameters fragmentAndParameters() {
            return fragmentAndParameters;
        }
        
        static <T> SetColumnAndValue<T> of(SqlColumn<T> column, T value, int uniqueId) {
            return new SetColumnAndValue<>(column, value, uniqueId);
        }

        static <T> SetColumnAndValue<T> of(SqlColumn<T> column) {
            return new SetColumnAndValue<>(column);
        }
    }
}

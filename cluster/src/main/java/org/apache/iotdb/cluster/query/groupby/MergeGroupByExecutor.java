package org.apache.iotdb.cluster.query.groupby;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.iotdb.cluster.server.member.MetaGroupMember;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.query.aggregation.AggregateResult;
import org.apache.iotdb.db.query.context.QueryContext;
import org.apache.iotdb.db.query.dataset.groupby.GroupByExecutor;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.read.common.Path;
import org.apache.iotdb.tsfile.read.filter.basic.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergeGroupByExecutor implements GroupByExecutor {
  private static final Logger logger = LoggerFactory.getLogger(MergeGroupByExecutor.class);

  private List<AggregateResult> results = new ArrayList<>();
  private List<Integer> aggregationTypes = new ArrayList<>();
  private Path path;
  private Set<String> deviceMeasurements;
  private TSDataType dataType;
  private QueryContext context;
  private Filter timeFilter;
  private MetaGroupMember metaGroupMember;

  private List<GroupByExecutor> groupByExecutors;

  MergeGroupByExecutor(Path path, Set<String> deviceMeasurements, TSDataType dataType,
      QueryContext context, Filter timeFilter,
      MetaGroupMember metaGroupMember) {
    this.path = path;
    this.deviceMeasurements = deviceMeasurements;
    this.dataType = dataType;
    this.context = context;
    this.timeFilter = timeFilter;
    this.metaGroupMember = metaGroupMember;
  }

  @Override
  public void addAggregateResult(AggregateResult aggrResult) {
    results.add(aggrResult);
    aggregationTypes.add(aggrResult.getAggregationType().ordinal());
  }

  private void resetAggregateResults() {
    for (AggregateResult result : results) {
      result.reset();
    }
  }

  @Override
  public List<AggregateResult> calcResult(long curStartTime, long curEndTime)
      throws QueryProcessException, IOException {
    if (groupByExecutors == null) {
      initExecutors();
    }
    resetAggregateResults();
    for (GroupByExecutor groupByExecutor : groupByExecutors) {
      List<AggregateResult> subResults = groupByExecutor
          .calcResult(curStartTime, curEndTime);
      for (int i = 0; i < subResults.size(); i++) {
        results.get(i).merge(subResults.get(i));
      }
    }
    logger.debug("Aggregation result of {}@[{}, {}] is {}", path, curStartTime, curEndTime, results);
    return results;
  }

  private void initExecutors() throws QueryProcessException {
    try {
      groupByExecutors = metaGroupMember.getGroupByExecutors(path, deviceMeasurements, dataType,
          context, timeFilter, aggregationTypes);
    } catch (StorageEngineException e) {
      throw new QueryProcessException(e);
    }
  }
}
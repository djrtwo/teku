/*
 * Copyright 2019 ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tech.pegasys.teku.beaconrestapi.handlers.node;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tech.pegasys.teku.beaconrestapi.CacheControlUtils.CACHE_NONE;

import io.javalin.core.util.Header;
import io.javalin.http.Context;
import org.junit.jupiter.api.Test;
import tech.pegasys.teku.api.ChainDataProvider;
import tech.pegasys.teku.api.schema.Fork;
import tech.pegasys.teku.datastructures.util.DataStructureUtil;
import tech.pegasys.teku.provider.JsonProvider;

public class GetForkTest {
  private final DataStructureUtil dataStructureUtil = new DataStructureUtil();
  private Context context = mock(Context.class);
  private final Fork fork = new Fork(dataStructureUtil.randomFork());
  private final JsonProvider jsonProvider = new JsonProvider();
  private final ChainDataProvider provider = mock(ChainDataProvider.class);

  @Test
  public void shouldReturnForkWhenSet() throws Exception {
    GetFork handler = new GetFork(provider, jsonProvider);
    when(provider.getFork()).thenReturn(fork);
    handler.handle(context);
    verify(context).result(jsonProvider.objectToJSON(fork));
    verify(context).header(Header.CACHE_CONTROL, CACHE_NONE);
  }
}

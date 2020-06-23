/*
 * Copyright 2020 ConsenSys AG.
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

package tech.pegasys.teku.cli.subcommand.debug;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.primitives.UnsignedLong;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.tuweni.bytes.Bytes32;
import tech.pegasys.teku.bls.BLSPublicKey;
import tech.pegasys.teku.bls.BLSSignature;
import tech.pegasys.teku.pow.api.Eth1EventsChannel;
import tech.pegasys.teku.pow.event.Deposit;
import tech.pegasys.teku.pow.event.DepositsFromBlockEvent;
import tech.pegasys.teku.pow.event.MinGenesisTimeBlockEvent;

class YamlEth1EventsChannel implements Eth1EventsChannel, AutoCloseable {

  private UnsignedLong nextExpectedDeposit = UnsignedLong.ZERO;
  private UnsignedLong lastBlockNumber;
  private UnsignedLong lastBlockTimestamp;
  private final SequenceWriter writer;

  public YamlEth1EventsChannel(final PrintStream out) throws IOException {
    final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    writer = mapper.writerWithDefaultPrettyPrinter().writeValuesAsArray(out);
  }

  @Override
  public void onDepositsFromBlock(final DepositsFromBlockEvent event) {
    final List<String> errors = new ArrayList<>();
    if (lastBlockNumber != null && event.getBlockNumber().compareTo(lastBlockNumber) <= 0) {
      errors.add(
          "Blocks received out of order. Got number "
              + event.getBlockNumber()
              + " after "
              + lastBlockNumber);
    }
    if (lastBlockTimestamp != null
        && event.getBlockTimestamp().compareTo(lastBlockTimestamp) <= 0) {
      errors.add(
          "Blocks received out of order. Got timestamp "
              + event.getBlockTimestamp()
              + " after "
              + lastBlockTimestamp);
    }
    final List<DepositInfo> deposits =
        event.getDeposits().stream().map(this::convertDeposit).collect(Collectors.toList());

    lastBlockNumber = event.getBlockNumber();
    lastBlockTimestamp = event.getBlockTimestamp();
    try {
      writer.write(
          new BlockInfo(
              event.getBlockNumber(),
              event.getBlockTimestamp(),
              event.getBlockHash(),
              deposits,
              errors));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private DepositInfo convertDeposit(final Deposit deposit) {
    final List<String> errors = new ArrayList<>();
    if (!nextExpectedDeposit.equals(deposit.getMerkle_tree_index())) {
      errors.add(
          "Incorrect deposit index. Expected "
              + nextExpectedDeposit
              + " but got "
              + deposit.getMerkle_tree_index());
    }
    nextExpectedDeposit = deposit.getMerkle_tree_index().plus(UnsignedLong.ONE);
    return new DepositInfo(
        deposit.getMerkle_tree_index(),
        deposit.getPubkey(),
        deposit.getAmount(),
        deposit.getWithdrawal_credentials(),
        deposit.getSignature(),
        errors);
  }

  @Override
  public void onMinGenesisTimeBlock(final MinGenesisTimeBlockEvent event) {
    try {
      writer.write(new MinGenesisTimeInfo(event));
    } catch (final IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }

  private static class BlockInfo {
    public final UnsignedLong block;
    public final UnsignedLong timestamp;
    public final String hash;
    public final List<DepositInfo> deposits;

    @JsonInclude(Include.NON_EMPTY)
    public final List<String> errors;

    public BlockInfo(
        final UnsignedLong blockNumber,
        final UnsignedLong blockTimestamp,
        final Bytes32 blockHash,
        final List<DepositInfo> deposits,
        final List<String> errors) {
      this.block = blockNumber;
      this.timestamp = blockTimestamp;
      this.hash = blockHash.toHexString();
      this.errors = errors;
      this.deposits = deposits;
    }
  }

  private static class MinGenesisTimeInfo {
    public final UnsignedLong number;
    public final UnsignedLong timestamp;
    public final String hash;
    public final boolean minGenesisBlock = true;

    public MinGenesisTimeInfo(final MinGenesisTimeBlockEvent event) {
      this.number = event.getBlockNumber();
      this.timestamp = event.getTimestamp();
      this.hash = event.getBlockHash().toHexString();
    }
  }

  private static class DepositInfo {
    public final UnsignedLong index;
    public final String publicKey;
    public final UnsignedLong amount;
    public final String withdrawalCredentials;
    public final String signature;

    @JsonInclude(Include.NON_EMPTY)
    public final List<String> errors;

    private DepositInfo(
        final UnsignedLong index,
        final BLSPublicKey publicKey,
        final UnsignedLong amount,
        final Bytes32 withdrawalCredentials,
        final BLSSignature signature,
        final List<String> errors) {
      this.index = index;
      this.publicKey = publicKey.toBytesCompressed().toHexString();
      this.amount = amount;
      this.withdrawalCredentials = withdrawalCredentials.toHexString();
      this.signature = signature.toBytes().toHexString();
      this.errors = errors;
    }
  }
}

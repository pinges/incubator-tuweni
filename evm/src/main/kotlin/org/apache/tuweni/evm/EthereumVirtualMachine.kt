/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tuweni.evm

import org.apache.tuweni.bytes.Bytes
import org.apache.tuweni.bytes.Bytes32
import org.apache.tuweni.eth.Address
import org.apache.tuweni.eth.repository.BlockchainRepository
import org.apache.tuweni.units.bigints.UInt256
import org.apache.tuweni.units.ethereum.Gas
import org.apache.tuweni.units.ethereum.Wei

/**
 * Types of EVM calls
 */
enum class CallKind(val number: Int) {
  CALL(0),
  DELEGATECALL(1),
  CALLCODE(2),
  CREATE(3),
  CREATE2(4)
}

/**
 * EVM execution status codes
 */
enum class EVMExecutionStatusCode(val number: Int) {
  SUCCESS(0),
  FAILURE(1),
  REVERT(2),
  OUT_OF_GAS(3),
  INVALID_INSTRUCTION(4),
  UNDEFINED_INSTRUCTION(5),
  STACK_OVERFLOW(6),
  STACK_UNDERFLOW(7),
  BAD_JUMP_DESTINATION(8),
  INVALID_MEMORY_ACCESS(9),
  CALL_DEPTH_EXCEEDED(10),
  STATIC_MODE_VIOLATION(11),
  PRECOMPILE_FAILURE(12),
  CONTRACT_VALIDATION_FAILURE(13),
  ARGUMENT_OUT_OF_RANGE(14),
  WASM_UNREACHABLE_INSTRUCTION(15),
  WASM_TRAP(16),
  INTERNAL_ERROR(-1),
  REJECTED(-2),
  OUT_OF_MEMORY(-3);
}

/**
 * Finds a code matching a number, or throw an exception if no matching code exists.
 * @param code the number to match
 * @return the execution code
 */
fun fromCode(code: Int): EVMExecutionStatusCode = EVMExecutionStatusCode.values().first {
  code == it.number
}

/**
 * Known hard fork revisions to execute against.
 */
enum class HardFork(val number: Int) {
  FRONTIER(0),
  HOMESTEAD(1),
  TANGERINE_WHISTLE(2),
  SPURIOUS_DRAGON(3),
  BYZANTIUM(4),
  CONSTANTINOPLE(5),
  PETERSBURG(6),
  ISTANBUL(7),
  BERLIN(8),
}

val latestHardFork = HardFork.BERLIN

/**
 * Result of EVM execution
 * @param statusCode the execution result status
 * @param gasLeft how much gas is left
 * @param hostContext the context of changes
 */
data class EVMResult(
  val statusCode: EVMExecutionStatusCode,
  val gasLeft: Long,
  val hostContext: HostContext
)

/**
 * Message sent to the EVM for execution
 */
data class EVMMessage(
  val kind: Int,
  val flags: Int,
  val depth: Int = 0,
  val gas: Gas,
  val destination: Address,
  val sender: Address,
  val inputData: Bytes,
  val value: Bytes,
  val createSalt: Bytes32 = Bytes32.ZERO
)

/**
 * An Ethereum Virtual Machine.
 *
 * @param repository the blockchain repository
 * @param evmVmFactory factory to create the EVM
 * @param options the options to set on the EVM, specific to the library
 */
class EthereumVirtualMachine(
  private val repository: BlockchainRepository,
  private val evmVmFactory: () -> EvmVm,
  private val options: Map<String, String> = mapOf()
) {

  private var vm: EvmVm? = null

  private fun vm() = vm!!

  /**
   * Start the EVM
   */
  suspend fun start() {
    vm = evmVmFactory()
    options.forEach { (k, v) ->
      vm().setOption(k, v)
    }
  }

  /**
   * Provides the version of the EVM
   *
   * @return the version of the underlying EVM library
   */
  fun version(): String = vm().version()

  /**
   * Stop the EVM
   */
  suspend fun stop() {
    vm().close()
  }

  /**
   * Execute an operation in the EVM.
   * @param sender the sender of the transaction
   * @param destination the destination of the transaction
   * @param code the code to execute
   * @param inputData the execution input
   * @param gas the gas available for the operation
   * @param gasPrice current gas price
   * @param currentCoinbase the coinbase address to reward
   * @param currentNumber current block number
   * @param currentTimestamp current block timestamp
   * @param currentGasLimit current gas limit
   * @param currentDifficulty block current total difficulty
   * @param callKind the type of call
   * @param revision the hard fork revision in which to execute
   * @return the result of the execution
   */
  suspend fun execute(
    sender: Address,
    destination: Address,
    value: Bytes,
    code: Bytes,
    inputData: Bytes,
    gas: Gas,
    gasPrice: Wei,
    currentCoinbase: Address,
    currentNumber: Long,
    currentTimestamp: Long,
    currentGasLimit: Long,
    currentDifficulty: UInt256,
    callKind: CallKind = CallKind.CALL,
    revision: HardFork = latestHardFork,
    depth: Int = 0
  ): EVMResult {
    val hostContext = TransactionalEVMHostContext(
      repository,
      this,
      depth,
      sender,
      destination,
      value,
      code,
      gas,
      gasPrice,
      currentCoinbase,
      currentNumber,
      currentTimestamp,
      currentGasLimit,
      currentDifficulty
    )
    val result =
      executeInternal(
        sender,
        destination,
        value,
        code,
        inputData,
        gas,
        callKind,
        revision,
        depth,
        hostContext
      )

    return result
  }

  internal suspend fun executeInternal(
    sender: Address,
    destination: Address,
    value: Bytes,
    code: Bytes,
    inputData: Bytes,
    gas: Gas,
    callKind: CallKind = CallKind.CALL,
    revision: HardFork = latestHardFork,
    depth: Int = 0,
    hostContext: HostContext
  ): EVMResult {
    val msg =
      EVMMessage(
        callKind.number, 0, depth, gas, destination, sender, inputData,
        value
      )

    return vm().execute(
      hostContext,
      revision,
      msg,
      code
    )
  }

  /**
   * Provides the capabilities exposed by the underlying EVM library
   *
   * @return the EVM capabilities
   */
  fun capabilities(): Int = vm().capabilities()
}

/**
 * This interface represents the callback functions must be implemented in order to interface with
 * the EVM.
 */
interface HostContext {
  /**
   * Check account existence function.
   *
   *
   * This function is used by the VM to check if there exists an account at given address.
   *
   * @param address The address of the account the query is about.
   * @return true if exists, false otherwise.
   */
  suspend fun accountExists(address: Address): Boolean

  /**
   * Get storage function.
   *
   *
   * This function is used by a VM to query the given account storage entry.
   *
   * @param address The address of the account.
   * @param key The index of the account's storage entry.
   * @return The storage value at the given storage key or null bytes if the account does not exist.
   */
  suspend fun getStorage(address: Address, keyBytes: Bytes): Bytes32

  /**
   * Set storage function.
   *
   *
   * This function is used by a VM to update the given account storage entry. The VM MUST make
   * sure that the account exists. This requirement is only a formality because VM implementations
   * only modify storage of the account of the current execution context (i.e. referenced by
   * message::destination).
   *
   * @param address The address of the account.
   * @param key The index of the storage entry.
   * @param value The value to be stored.
   * @return The effect on the storage item.
   */
  suspend fun setStorage(address: Address, key: Bytes, value: Bytes32): Int

  /**
   * Get balance function.
   *
   *
   * This function is used by a VM to query the balance of the given account.
   *
   * @param address The address of the account.
   * @return The balance of the given account or 0 if the account does not exist.
   */
  suspend fun getBalance(address: Address): Bytes32

  /**
   * Get code size function.
   *
   *
   * This function is used by a VM to get the size of the code stored in the account at the given
   * address.
   *
   * @param address The address of the account.
   * @return The size of the code in the account or 0 if the account does not exist.
   */
  suspend fun getCodeSize(address: Address): Int

  /**
   * Get code hash function.
   *
   *
   * This function is used by a VM to get the keccak256 hash of the code stored in the account at
   * the given address. For existing accounts not having a code, this function returns keccak256
   * hash of empty data.
   *
   * @param address The address of the account.
   * @return The hash of the code in the account or null bytes if the account does not exist.
   */
  suspend fun getCodeHash(address: Address): Bytes32

  /**
   * Copy code function.
   *
   *
   * This function is used by an EVM to request a copy of the code of the given account to the
   * memory buffer provided by the EVM. The Client MUST copy the requested code, starting with the
   * given offset, to the provided memory buffer up to the size of the buffer or the size of the
   * code, whichever is smaller.
   *
   * @param address The address of the account.
   * @return A copy of the requested code.
   */
  suspend fun getCode(address: Address): Bytes

  /**
   * Selfdestruct function.
   *
   *
   * This function is used by an EVM to SELFDESTRUCT given contract. The execution of the
   * contract will not be stopped, that is up to the EVM.
   *
   * @param address The address of the contract to be selfdestructed.
   * @param beneficiary The address where the remaining ETH is going to be transferred.
   */
  suspend fun selfdestruct(address: Address, beneficiary: Address)

  /**
   * This function supports EVM calls.
   *
   * @param msg The call parameters.
   * @return The result of the call.
   */
  suspend fun call(evmMessage: EVMMessage): EVMResult

  /**
   * Get transaction context function.
   *
   *
   * This function is used by an EVM to retrieve the transaction and block context.
   *
   * @return The transaction context.
   */
  fun getTxContext(): Bytes?

  /**
   * Get block hash function.
   *
   *
   * This function is used by a VM to query the hash of the header of the given block. If the
   * information about the requested block is not available, then this is signalled by returning
   * null bytes.
   *
   * @param number The block number.
   * @return The block hash or null bytes if the information about the block is not available.
   */
  fun getBlockHash(number: Long): Bytes32

  /**
   * Log function.
   *
   *
   * This function is used by an EVM to inform about a LOG that happened during an EVM bytecode
   * execution.
   *
   * @param address The address of the contract that generated the log.
   * @param data The unindexed data attached to the log.
   * @param dataSize The length of the data.
   * @param topics The the array of topics attached to the log.
   * @param topicCount The number of the topics. Valid values are between 0 and 4 inclusively.
   */
  fun emitLog(address: Address, data: Bytes, topics: Array<Bytes>, topicCount: Int)
}

interface EvmVm {
  fun setOption(key: String, value: String)
  fun version(): String
  suspend fun close()
  suspend fun execute(hostContext: HostContext, fork: HardFork, msg: EVMMessage, code: Bytes): EVMResult
  fun capabilities(): Int
}

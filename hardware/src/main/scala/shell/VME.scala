/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package vta.shell
import chisel3._
import chisel3.util._
import vta.util.config._
import vta.util.genericbundle._
import vta.interface.axi._


/** VME parameters.
 *
 * These parameters are used on VME interfaces and modules.
 */
case class VMEParams
  (val nReadClients: Int = 8,
    val nWriteClients: Int = 1,
    val clientBits : Int = 3,
    val RequestQueueDepth : Int = 16,
    val vmeParams : Int = 18,
    val clientCmdQueueDepth : Int = 1,
    val clientTagBitWidth : Int = 21,
    val clientDataQueueDepth : Int = 16) {

  val RequestQueueMaskBits : Int = RequestQueueDepth.toInt

  require(nReadClients > 0,
  s"\n\n[VTA] [VMEParams] nReadClients must be larger than 0\n\n")
  require(
    nWriteClients == 1,
    s"\n\n[VTA] [VMEParams] nWriteClients must be 1, only one-write-client support atm\n\n")
}

/** VMEBase. Parametrize base class. */
abstract class VMEBase(implicit p: Parameters) extends GenericParameterizedBundle(p)

/** VMECmd.
 *
 * This interface is used for creating write and read requests to memory.
 */
class clientTag(implicit p:Parameters) extends Bundle{
  val clientBits = p(ShellKey).vmeParams.clientBits
  val RequestQueueDepth = p(ShellKey).vmeParams.RequestQueueDepth
  val RequestQueueMaskBits = p(ShellKey).vmeParams.RequestQueueMaskBits
  val client_id  = UInt(clientBits.W)
  val client_tag = UInt(p(ShellKey).vmeParams.clientTagBitWidth.W)
  val client_mask = UInt(RequestQueueMaskBits.W)
}

class VMECmd(implicit p: Parameters) extends VMEBase {
  val addrBits = p(ShellKey).memParams.addrBits
  val lenBits = p(ShellKey).memParams.lenBits
  val tagBits  = p(ShellKey).vmeParams.clientTagBitWidth
  val addr = UInt(addrBits.W)
  val len = UInt(lenBits.W)
  val tag = UInt(tagBits.W)
}
class VMECmdData(implicit p: Parameters) extends VMEBase {
  val data = UInt(p(ShellKey).memParams.dataBits.W)
  val last = Bool()
}

class VMEData(implicit p: Parameters) extends VMEBase {
  val dataBits = p(ShellKey).memParams.dataBits
  val data = UInt(dataBits.W)
  val tag = UInt(p(ShellKey).vmeParams.clientTagBitWidth.W)
  val last = Bool()
}

/** VMEReadMaster.
 *
 * This interface is used by modules inside the core to generate read requests
 * and receive responses from VME.
 */
class VMEReadMaster(implicit p: Parameters) extends Bundle {
  val dataBits = p(ShellKey).memParams.dataBits
  val cmd = Decoupled(new VMECmd)
  val data = Flipped(Decoupled(new VMEData))
}

/** VMEReadClient.
 *
 * This interface is used by the VME to receive read requests and generate
 * responses to modules inside the core.
 */
class VMEReadClient(implicit p: Parameters) extends Bundle {
  val dataBits = p(ShellKey).memParams.dataBits
  val cmd = Flipped(Decoupled(new VMECmd))
  val data = Decoupled(new VMEData)
}

/** VMEWriteData.
 *
 * This interface is used by the VME to handle write requests from modules inside
 * the core.
 */
class VMEWriteData(implicit p: Parameters) extends Bundle {
  val dataBits = p(ShellKey).memParams.dataBits
  val strbBits = dataBits/8

  val data = UInt(dataBits.W)
  val strb = UInt(strbBits.W)
}

/** VMEWriteMaster.
 *
 * This interface is used by modules inside the core to generate write requests
 * to the VME.
 */
class VMEWriteMaster(implicit p: Parameters) extends Bundle {
  val dataBits = p(ShellKey).memParams.dataBits
  val cmd = Decoupled(new VMECmd)
  val data = Decoupled(new VMEWriteData)
  val ack = Input(Bool())
}

/** VMEWriteClient.
 *
 * This interface is used by the VME to handle write requests from modules inside
 * the core.
 */
class VMEWriteClient(implicit p: Parameters) extends Bundle {
  val dataBits = p(ShellKey).memParams.dataBits
  val cmd = Flipped(Decoupled(new VMECmd))
  val data = Flipped(Decoupled(new VMEWriteData))
  val ack = Output(Bool())
}

/** VMEMaster.
 *
 * Pack nRd number of VMEReadMaster interfaces and nWr number of VMEWriteMaster
 * interfaces.
 */
class VMEMaster(implicit p: Parameters) extends Bundle {
  val nRd = p(ShellKey).vmeParams.nReadClients
  val nWr = p(ShellKey).vmeParams.nWriteClients
  val rd = Vec(nRd, new VMEReadMaster)
  val wr = Vec(nWr, new VMEWriteMaster)
}

/** VMEClient.
 *
 * Pack nRd number of VMEReadClient interfaces and nWr number of VMEWriteClient
 * interfaces.
 */
class VMEClient(implicit p: Parameters) extends Bundle {
  val nRd = p(ShellKey).vmeParams.nReadClients
  val nWr = p(ShellKey).vmeParams.nWriteClients
  val rd = Vec(nRd, new VMEReadClient)
  val wr = Vec(nWr, new VMEWriteClient)
}
/** VTA Memory Engine (VME).
 *
 * This unit multiplexes the memory controller interface for the Core. Currently,
 * it supports single-writer and multiple-reader mode and it is also based on AXI.
 */
class VMETop(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val mem = new AXIMaster(p(ShellKey).memParams)
    val vme = new VMEClient
  })

  val forceSimpleVME = true // force simple vme for simple tensor load/uop/fetch

  if (forceSimpleVME) {
    val vme = Module(new VMESimple)
    io <> vme.io
  } else {
    val vme = Module(new VME)
    io <> vme.io
  }
}

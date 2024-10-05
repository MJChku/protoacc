// /*
//  * Licensed to the Apache Software Foundation (ASF) under one
//  * or more contributor license agreements.  See the NOTICE file
//  * distributed with this work for additional information
//  * regarding copyright ownership.  The ASF licenses this file
//  * to you under the Apache License, Version 2.0 (the
//  * "License"); you may not use this file except in compliance
//  * with the License.  You may obtain a copy of the License at
//  *
//  *   http://www.apache.org/licenses/LICENSE-2.0
//  *
//  * Unless required by applicable law or agreed to in writing,
//  * software distributed under the License is distributed on an
//  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
//  * KIND, either express or implied.  See the License for the
//  * specific language governing permissions and limitations
//  * under the License.
//  */

// package vta.shell
// import chisel3._
// import chisel3.util._
// import vta.util.config._
// import vta.util.genericbundle._
// import vta.interface.axi._


// /** VTA Memory Engine (VME).
//  *
//  * This unit multiplexes the memory controller interface for the Core. Currently,
//  * it supports single-writer and multiple-reader mode and it is also based on AXI.
//  */
// class VME(implicit p: Parameters) extends Module {
//   val io = IO(new Bundle {
//     val mem = new AXIMaster(p(ShellKey).memParams)
//     val vme = new VMEClient
//   })

//   val nReadClients = p(ShellKey).vmeParams.nReadClients
//   val rd_arb = Module(new Arbiter(new VMECmd, nReadClients))
//   val rd_arb_chosen = RegEnable(rd_arb.io.chosen, rd_arb.io.out.fire)

//   for (i <- 0 until nReadClients) { rd_arb.io.in(i) <> io.vme.rd(i).cmd }

//   val sReadIdle :: sReadAddr :: sReadData :: Nil = Enum(3)
//   val rstate = RegInit(sReadIdle)

//   switch(rstate) {
//     is(sReadIdle) {
//       when(rd_arb.io.out.valid) {
//         rstate := sReadAddr
//       }
//     }
//     is(sReadAddr) {
//       when(io.mem.ar.ready) {
//         rstate := sReadData
//       }
//     }
//     is(sReadData) {
//       when(io.mem.r.fire && io.mem.r.bits.last) {
//         rstate := sReadIdle
//       }
//     }
//   }

//   val sWriteIdle :: sWriteAddr :: sWriteData :: sWriteResp :: Nil = Enum(4)
//   val wstate = RegInit(sWriteIdle)
//   val addrBits = p(ShellKey).memParams.addrBits
//   val lenBits = p(ShellKey).memParams.lenBits
//   val wr_cnt = RegInit(0.U(lenBits.W))

//   when(wstate === sWriteIdle) {
//     wr_cnt := 0.U
//   }.elsewhen(io.mem.w.fire) {
//     wr_cnt := wr_cnt + 1.U
//   }

//   switch(wstate) {
//     is(sWriteIdle) {
//       when(io.vme.wr(0).cmd.valid) {
//         wstate := sWriteAddr
//       }
//     }
//     is(sWriteAddr) {
//       when(io.mem.aw.ready) {
//         wstate := sWriteData
//       }
//     }
//     is(sWriteData) {
//       when(
//         io.vme
//           .wr(0)
//           .data
//           .valid && io.mem.w.ready && wr_cnt === io.vme.wr(0).cmd.bits.len) {
//         wstate := sWriteResp
//       }
//     }
//     is(sWriteResp) {
//       when(io.mem.b.valid) {
//         wstate := sWriteIdle
//       }
//     }
//   }

//   // registers storing read/write cmds

//   val rd_len = RegInit(0.U(lenBits.W))
//   val wr_len = RegInit(0.U(lenBits.W))
//   val rd_addr = RegInit(0.U(addrBits.W))
//   val wr_addr = RegInit(0.U(addrBits.W))

//   when(rd_arb.io.out.fire) {
//     rd_len := rd_arb.io.out.bits.len
//     rd_addr := rd_arb.io.out.bits.addr
//   }

//   when(io.vme.wr(0).cmd.fire) {
//     wr_len := io.vme.wr(0).cmd.bits.len
//     wr_addr := io.vme.wr(0).cmd.bits.addr
//   }

//   // rd arb
//   rd_arb.io.out.ready := rstate === sReadIdle

//   val localTag = Reg(Vec(nReadClients, UInt(p(ShellKey).vmeParams.clientTagBitWidth.W)))
//   // vme
//   for (i <- 0 until nReadClients) {
//     io.vme.rd(i).data.valid := rd_arb_chosen === i.asUInt & io.mem.r.valid
//     io.vme.rd(i).data.bits.data := io.mem.r.bits.data
//     io.vme.rd(i).data.bits.last := io.mem.r.bits.last
//     io.vme.rd(i).data.bits.tag := localTag(i)

//     when (io.vme.rd(i).cmd.fire) {
//       localTag(i) := io.vme.rd(i).cmd.bits.tag
//     }
//   }

//   io.vme.wr(0).cmd.ready := wstate === sWriteIdle
//   io.vme.wr(0).ack := io.mem.b.fire
//   io.vme.wr(0).data.ready := wstate === sWriteData & io.mem.w.ready

//   // mem
//   io.mem.aw.valid := wstate === sWriteAddr
//   io.mem.aw.bits.addr := wr_addr
//   io.mem.aw.bits.len := wr_len

//   io.mem.w.valid := wstate === sWriteData & io.vme.wr(0).data.valid
//   io.mem.w.bits.data := io.vme.wr(0).data.bits.data
//   io.mem.w.bits.last := wr_cnt === io.vme.wr(0).cmd.bits.len
//   io.mem.w.bits.strb := Fill(p(ShellKey).memParams.strbBits, true.B)

//   io.mem.b.ready := wstate === sWriteResp

//   io.mem.ar.valid := rstate === sReadAddr
//   io.mem.ar.bits.addr := rd_addr
//   io.mem.ar.bits.len := rd_len
//   io.mem.ar.bits.id  := 0.U

//   io.mem.r.ready := rstate === sReadData & io.vme.rd(rd_arb_chosen).data.ready

//   // AXI constants - statically defined
//   io.mem.setConst()
// }

package vta.shell
import chisel3._
import chisel3.util._
import vta.util.config._
import vta.util.genericbundle._
import vta.interface.axi._

class VMESimple(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val mem = new AXIMaster(p(ShellKey).memParams)
    val vme = new VMEClient
  })

  val nReadClients = p(ShellKey).vmeParams.nReadClients
  val rd_arb = Module(new Arbiter(new VMECmd, nReadClients))
  val rd_arb_chosen = RegEnable(rd_arb.io.chosen, rd_arb.io.out.fire)

  for (i <- 0 until nReadClients) { rd_arb.io.in(i) <> io.vme.rd(i).cmd }

  val sReadIdle :: sReadAddr :: sReadData :: Nil = Enum(3)
  val rstate = RegInit(sReadIdle)

  switch(rstate) {
    is(sReadIdle) {
      when(rd_arb.io.out.valid) {
        rstate := sReadAddr
      }
    }
    is(sReadAddr) {
      when(io.mem.ar.ready) {
        rstate := sReadData
      }
    }
    is(sReadData) {
      when(io.mem.r.fire && io.mem.r.bits.last) {
        rstate := sReadIdle
      }
    }
  }

  val sWriteIdle :: sWriteAddr :: sWriteData :: sWriteResp :: Nil = Enum(4)
  val wstate = RegInit(sWriteIdle)
  val addrBits = p(ShellKey).memParams.addrBits
  val lenBits = p(ShellKey).memParams.lenBits
  val wr_cnt = RegInit(0.U(lenBits.W))

  when(wstate === sWriteIdle) {
    wr_cnt := 0.U
  }.elsewhen(io.mem.w.fire) {
    wr_cnt := wr_cnt + 1.U
  }

  switch(wstate) {
    is(sWriteIdle) {
      when(io.vme.wr(0).cmd.valid) {
        wstate := sWriteAddr
      }
    }
    is(sWriteAddr) {
      when(io.mem.aw.ready) {
        wstate := sWriteData
      }
    }
    is(sWriteData) {
      when(
        io.vme
          .wr(0)
          .data
          .valid && io.mem.w.ready && wr_cnt === io.vme.wr(0).cmd.bits.len) {
        wstate := sWriteResp
      }
    }
    is(sWriteResp) {
      when(io.mem.b.valid) {
        wstate := sWriteIdle
      }
    }
  }

  // registers storing read/write cmds

  val rd_len = RegInit(0.U(lenBits.W))
  val wr_len = RegInit(0.U(lenBits.W))
  val rd_addr = RegInit(0.U(addrBits.W))
  val wr_addr = RegInit(0.U(addrBits.W))

  when(rd_arb.io.out.fire) {
    rd_len := rd_arb.io.out.bits.len
    rd_addr := rd_arb.io.out.bits.addr
  }

  when(io.vme.wr(0).cmd.fire) {
    wr_len := io.vme.wr(0).cmd.bits.len
    wr_addr := io.vme.wr(0).cmd.bits.addr
  }

  // rd arb
  rd_arb.io.out.ready := rstate === sReadIdle

  val localTag = Reg(Vec(nReadClients, UInt(p(ShellKey).vmeParams.clientTagBitWidth.W)))
  // vme
  for (i <- 0 until nReadClients) {
    io.vme.rd(i).data.valid := rd_arb_chosen === i.asUInt & io.mem.r.valid
    io.vme.rd(i).data.bits.data := io.mem.r.bits.data
    io.vme.rd(i).data.bits.last := io.mem.r.bits.last
    io.vme.rd(i).data.bits.tag := localTag(i)

    // didn't set cmd ready

    when (io.vme.rd(i).cmd.fire) {
      localTag(i) := io.vme.rd(i).cmd.bits.tag
    }
  }

  io.vme.wr(0).cmd.ready := wstate === sWriteIdle
  io.vme.wr(0).ack := io.mem.b.fire
  io.vme.wr(0).data.ready := wstate === sWriteData & io.mem.w.ready

  // mem
  io.mem.aw.valid := wstate === sWriteAddr
  io.mem.aw.bits.addr := wr_addr
  io.mem.aw.bits.len := wr_len

  io.mem.w.valid := wstate === sWriteData & io.vme.wr(0).data.valid
  io.mem.w.bits.data := io.vme.wr(0).data.bits.data
  io.mem.w.bits.last := wr_cnt === io.vme.wr(0).cmd.bits.len
  io.mem.w.bits.strb := Fill(p(ShellKey).memParams.strbBits, true.B)

  io.mem.b.ready := wstate === sWriteResp

  io.mem.ar.valid := rstate === sReadAddr
  io.mem.ar.bits.addr := rd_addr
  io.mem.ar.bits.len := rd_len
  io.mem.ar.bits.id  := 0.U

  io.mem.r.ready := rstate === sReadData & io.vme.rd(rd_arb_chosen).data.ready

  // AXI constants - statically defined
  io.mem.setConst()
}


/** VTA Memory Engine (VME).
 *
 * This unit multiplexes the memory controller interface for the Core. Currently,
 * it supports single-writer and multiple-reader mode and it is also based on AXI.
 */
class VME(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val mem = new AXIMaster(p(ShellKey).memParams)
    val vme = new VMEClient
  })
  val clientCmdQueueDepth = p(ShellKey).vmeParams.clientCmdQueueDepth
  val clientDataQueueDepth = p(ShellKey).vmeParams.clientDataQueueDepth
  val RequestQueueDepth = p(ShellKey).vmeParams.RequestQueueDepth
  val RequestQueueAddrWidth = log2Ceil(RequestQueueDepth.toInt)
  val dataBits = p(ShellKey).memParams.dataBits
  val nReadClients = p(ShellKey).vmeParams.nReadClients
  val addrBits = p(ShellKey).memParams.addrBits
  val lenBits = p(ShellKey).memParams.lenBits
  val idBits = p(ShellKey).memParams.idBits
  val vmeTag_array = SyncReadMem(RequestQueueDepth,(new(clientTag)))
  val vmeTag_array_wr_data = Wire(new(clientTag))
  val vmeTag_array_wr_addr = Wire(UInt(RequestQueueAddrWidth.W))
  val vmeTag_array_rd_addr = Wire(UInt(RequestQueueAddrWidth.W))
  val vmeTag_array_wr_en  = Wire(Bool())
  val localTag_out  = Wire(new(clientTag))
  val availableEntriesEn = Wire(Bool())
  val availableEntriesNext = Wire(UInt(RequestQueueDepth.W))
  val availableEntries     = Reg(availableEntriesNext.cloneType)
  val freeTagLocation  = Wire(UInt(RequestQueueDepth.W))
  val (resetEntry,newEntry,firstPostn) = firstOneOH(availableEntries.asUInt)
  val updateEntry = Wire(UInt(RequestQueueDepth.W))
  when(io.mem.r.bits.last & io.mem.r.valid){
  availableEntriesNext := updateEntry | availableEntries
  }.elsewhen(availableEntriesEn && availableEntries =/= 0.U && !(io.mem.r.bits.last & io.mem.r.valid)){
  availableEntriesNext:= newEntry
  }.otherwise{
  availableEntriesNext:= availableEntries
  }
  when(reset.asBool){
  availableEntries := VecInit(Seq.fill(RequestQueueDepth)(true.B)).asUInt
  updateEntry := 0.U
  }.otherwise{
  availableEntries := availableEntriesNext
  updateEntry := VecInit(IndexedSeq.tabulate(RequestQueueDepth){ i => i.U === (io.mem.r.bits.id).asUInt }).asUInt
  }
  // Cmd Queues for eaach VME client
  val VMEcmd_Qs = IndexedSeq.fill(nReadClients){ Module(new Queue(new VMECmd, clientCmdQueueDepth))}

  //---------------------------------------
  //--- Find available buffer entries -----
  //---------------------------------------
  def firstOneOH (in: UInt) = {
    val oneHotIdx = for(bitIdx <- 0 until in.getWidth) yield {
      if (bitIdx == 0){
        in(0)
      }
      else{
        in(bitIdx) && ~in(bitIdx-1,0).orR
      }
    }
    val oHot = VecInit(oneHotIdx).asUInt
    val newVec = in&(~oHot) // turn bit to 0
    val bitPostn = PriorityEncoder(oneHotIdx)
    (oHot, newVec,bitPostn)
  }
  val default_tag = Wire(new(clientTag))
  default_tag.client_tag  := 0.U
  default_tag.client_id  := 0.U
  default_tag.client_mask := 0.U

  val cmd_valids = for { q <- VMEcmd_Qs } yield q.io.deq.valid

  val vme_select = PriorityEncoder(cmd_valids :+ true.B)
  val any_cmd_valid = cmd_valids.foldLeft(false.B){ case (x,y) => x || y}
  availableEntriesEn := io.mem.ar.ready & any_cmd_valid

  for { i <- 0 until nReadClients} {
    VMEcmd_Qs(i).io.enq.valid := io.vme.rd(i).cmd.valid  & VMEcmd_Qs(i).io.enq.ready
    VMEcmd_Qs(i).io.enq.bits  := io.vme.rd(i).cmd.bits
    VMEcmd_Qs(i).io.deq.ready := io.mem.ar.ready &
    (vme_select === i.U) & (availableEntries.asUInt =/= 0.U) &
    !(io.mem.r.bits.last & io.mem.r.valid)
    io.vme.rd(i).cmd.ready := VMEcmd_Qs(i).io.enq.ready
  }

  vmeTag_array_wr_addr := firstPostn.asUInt


  val cmd_readys = for { q <- VMEcmd_Qs} yield q.io.deq.ready
  val any_cmd_ready = cmd_readys.foldLeft(false.B){ case (x,y) => x || y}

  vmeTag_array_wr_en := any_cmd_ready

  when(vmeTag_array_wr_en){
    val rdwrPort = vmeTag_array(vmeTag_array_wr_addr)
    rdwrPort  := vmeTag_array_wr_data
  }

  io.mem.ar.bits.addr := 0.U
  io.mem.ar.bits.len  := 0.U
  io.mem.ar.valid     := 0.U
  io.mem.ar.bits.id   := 0.U
  vmeTag_array_wr_data := default_tag

  // Last assign wins so do this in reverse order
  for { i <- 4 to 0 by -1} {
    when(VMEcmd_Qs(i).io.deq.ready){
      io.mem.ar.bits.addr := VMEcmd_Qs(i).io.deq.bits.addr
      io.mem.ar.bits.len  := VMEcmd_Qs(i).io.deq.bits.len
      io.mem.ar.valid     := VMEcmd_Qs(i).io.deq.valid
      io.mem.ar.bits.id   := vmeTag_array_wr_addr
      vmeTag_array_wr_data.client_id  := i.U
      vmeTag_array_wr_data.client_tag := VMEcmd_Qs(i).io.deq.bits.tag
      vmeTag_array_wr_data.client_mask := resetEntry
    }
  }

  // We need one clock cycle to look up the local tag from the
  // centralized tag buffer vmeTag_array
  // Adding a flop stage for mem.r.data, mem.r.last, mem.r.valid
  // till local tag lookup is performed.
  io.mem.r.ready  := true.B
  vmeTag_array_rd_addr :=  io.mem.r.bits.id
  localTag_out         :=  vmeTag_array(vmeTag_array_rd_addr)
  freeTagLocation      :=  localTag_out.client_mask

  for (i <- 0 until nReadClients) {
    io.vme.rd(i).data.valid := ((RegNext(io.mem.r.valid, init = false.B)) && ((localTag_out.client_id) === i.U)
    && io.vme.rd(i).data.ready)
    //VME doesnt stop on not ready
    assert(io.vme.rd(i).data.ready || ~io.vme.rd(i).data.valid)
    io.vme.rd(i).data.bits.data := RegNext(io.mem.r.bits.data, init = false.B)
    io.vme.rd(i).data.bits.last := RegNext(io.mem.r.bits.last, init = false.B)
    io.vme.rd(i).data.bits.tag  := localTag_out.client_tag
  }

  // VME <-> AXI write interface
  val wr_len = RegInit(0.U(lenBits.W))
  val wr_addr = RegInit(0.U(addrBits.W))
  val sWriteIdle :: sWriteAddr :: sWriteData :: sWriteResp :: Nil = Enum(4)
  val wstate = RegInit(sWriteIdle)
  val wr_cnt = RegInit(0.U(lenBits.W))
  io.vme.wr(0).cmd.ready := wstate === sWriteIdle
  io.vme.wr(0).ack := io.mem.b.fire
  io.vme.wr(0).data.ready := wstate === sWriteData & io.mem.w.ready
  io.mem.aw.valid := wstate === sWriteAddr
  io.mem.aw.bits.addr := wr_addr
  io.mem.aw.bits.len := wr_len
  io.mem.aw.bits.id  := p(ShellKey).memParams.idConst.U // no support for multiple writes
  io.mem.w.valid := wstate === sWriteData & io.vme.wr(0).data.valid
  io.mem.w.bits.data := io.vme.wr(0).data.bits.data
  io.mem.w.bits.strb := io.vme.wr(0).data.bits.strb
  io.mem.w.bits.last := wr_cnt === wr_len
  io.mem.w.bits.id   := p(ShellKey).memParams.idConst.U // no support for multiple writes
  io.mem.b.ready := wstate === sWriteResp
  when(io.vme.wr(0).cmd.fire) {
    wr_len := io.vme.wr(0).cmd.bits.len
    wr_addr := io.vme.wr(0).cmd.bits.addr
  }
  when(wstate === sWriteIdle) {
    wr_cnt := 0.U
  }
  .elsewhen(io.mem.w.fire){
    wr_cnt := wr_cnt + 1.U
  }
  switch(wstate){
    is(sWriteIdle){
      when(io.vme.wr(0).cmd.valid){
        wstate := sWriteAddr
      }
    }
    is(sWriteAddr){
      when(io.mem.aw.ready){
        wstate := sWriteData
      }
    }
    is(sWriteData){
      when(io.vme.wr(0).data.valid && io.mem.w.ready && wr_cnt === wr_len) {
        wstate := sWriteResp
      }
    }
    is(sWriteResp) {
      when(io.mem.b.valid) {
        wstate := sWriteIdle
      }
    }
  }
  // AXI constants - statically define
  io.mem.setConst()
}
